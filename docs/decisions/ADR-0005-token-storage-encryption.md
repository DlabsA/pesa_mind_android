# ADR-0005: Encrypt TokenManager's secrets at rest (DataStore + Tink + Keystore)

**Status:** Proposed — `ktlintCheck` and `assembleDebug` both green (see "Verification"),
not yet committed; awaiting approval per this session's explicit "don't commit until I
approve."
**Date:** 2026-07-26

## Context

`TokenManager` (`core/storage/TokenManager.kt`) persists the JWT, refresh token, PIN, and
unlock pattern in a `Preferences` DataStore (`pesamind_prefs`) as **plaintext strings** — a
direct, named violation of this repo's own secrets rule
(`.claude/CLAUDE.md`: "Any locally-persisted secret ... must be encrypted"). First flagged in
`docs/vault/05-debt-burndown.md` (item A1) as the highest-severity open Android item: a
rooted device, an `adb backup`, or a lost/shared device exposes all four values in cleartext
today.

The rule as written named `EncryptedSharedPreferences` as the fix. That's stale: ESP was
deprecated in `androidx.security.crypto` 1.1.0-alpha07 (April 2025) over main-thread
StrictMode violations and keyset-corruption crashes reported on some OEMs. `androidx.security
.crypto` was already a dependency in this repo (`libs.androidx.security.crypto`,
`1.1.0-alpha06`) but had **zero actual usages** anywhere in `app/src/main` — confirmed by
grep before removing it. Nothing currently built on it, so removing it is a clean swap, not a
migration off a live dependency.

## Decision

Keep DataStore as the store. Add an encryption layer on top of it: Tink's `Aead` primitive
(AES256-GCM), with the Data Encryption Key wrapped by an Android Keystore-resident key via
`AndroidKeysetManager` (`com.google.crypto.tink:tink-android`). `TokenCryptoManager`
(`core/storage/TokenCryptoManager.kt`, new) owns the keyset lifecycle and exposes
`encrypt`/`decryptOutcome`; `TokenManager` calls it internally at every existing save/get call
site. No caller anywhere in the app changes — `saveTokens`/`getToken`/`savePin`/`getPin`/
`savePattern`/`getPattern`/`getRefreshToken` all keep their existing signatures and null
semantics.

### Deviation: `Aead`, not `StreamingAead`

The task brief that produced this ADR specified Tink `StreamingAead`. Implemented with `Aead`
instead, flagged explicitly per this repo's own reuse-first "if you deviate, say so" norm:
`StreamingAead`'s AES-GCM-HKDF-STREAMING format is chunked/segmented, built for encrypting
large files or streams without holding the whole plaintext in memory. JWTs, refresh tokens,
PINs, and patterns are all well under 1KB — `StreamingAead` would add per-segment framing
overhead for zero benefit here, and Tink's own Android/Keystore integration guide uses `Aead`
for exactly this "protect a small app secret with a Keystore-wrapped key" case, not
`StreamingAead`. Same security properties either way (AES-256, authenticated encryption,
Keystore-wrapped key) — this is an implementation-detail correction, not a change to the
security posture the brief asked for. Happy to switch if there's a reason for `StreamingAead`
specifically (e.g. a plan to reuse the same primitive uniformly for large data elsewhere) that
wasn't visible from this task alone.

### Ciphertext format: `"ENC1:" + Base64(ciphertext)`, self-describing

Every encrypted value is stored with an explicit `ENC1:` prefix rather than relying on
decrypt-throws-on-garbage to distinguish "encrypted" from "still-plaintext, not yet
migrated." This makes every read self-describing and migration-order-independent: a value
without the prefix is read back as-is (plaintext), a prefixed value is decrypted. No flag,
no synchronous blocking of `TokenManager.init()`, no race window where an early cold-start
read could hit a half-migrated value — see "Migration" below for why this is safe without a
guard flag.

### Field binding via associated data

Each field's encrypt/decrypt call passes that DataStore key's own name (`"jwt_token"`,
`"refresh_token"`, `"user_pin"`, `"user_pattern"`) as Tink's associated-data parameter. This
binds a ciphertext to the field it was encrypted for — copying a stored ciphertext into a
different field's slot fails to decrypt (wrong AAD) instead of silently succeeding. Cheap,
and standard AEAD practice; not part of the original brief but a natural fit given Tink's API
already takes an AAD parameter.

## Migration: idempotent-by-construction, not flag-gated

Unlike `PrefsToRoomMigrator` (thousands of rows, needs a fast-path flag + double-guard),
this migration is 4 short strings, so it's simpler to make it a no-op-safe check that just
runs every launch: `TokenManager.init()` launches a background coroutine
(`migrateToEncryptedStorage()`, caught, same fire-and-forget discipline as
`PrefsToRoomMigrator`'s own call site in `PesaMindApp.onCreate()`) that, inside one
`dataStore.edit{}` transaction, re-encrypts any of the four values still missing the `ENC1:`
prefix. Idempotent: once a value is prefixed, subsequent runs skip it. This alone would not
be enough for JWT/refresh token — the general encrypt-on-write below already re-encrypts
those on their next natural save (token refresh) — but PIN and pattern are written once at
setup and may otherwise go months without a rewrite, so the explicit pass matters most for
those two.

Current users are not logged out or PIN-locked by this: every existing plaintext value is
read back correctly (no prefix → returned as-is) until its turn in the migration pass, and
every new write (`saveTokens`/`savePin`/`savePattern`) encrypts unconditionally from the
moment this ships, regardless of whether the background pass has run yet.

## Decrypt-failure path

`TokenCryptoManager.decryptOutcome` returns a 3-way `DecryptOutcome` (`Success`, `Absent`,
`Failed`) instead of a bare nullable string, specifically so `TokenManager` can tell "never
set" apart from "was set, can no longer be read" (new device where the DataStore/keyset files
weren't restored at all — the backup exclusion below prevents this exact case from even
reaching the decrypt path — or, on the *same* device, a corrupted/invalidated Keystore key).

On `Failed`, `readSecret`'s `onFailure` callback proactively clears the affected state instead
of leaving it stuck:
- `getToken`/`getRefreshToken` → `clearTokens()`/`clearRefreshToken()`. `isLoggedIn()` becomes
  `false`, and `NavGraph`'s existing `LockState.NONE -> if (isLoggedIn()) Dashboard else
  Login` routing (unchanged) sends the user to Login — no new navigation logic needed.
- `getPin`/`getPattern` → `clearLock()` (clears both PIN and pattern state, not just the one
  that failed — safe, since `savePin`/`savePattern` already keep the two mutually exclusive,
  so at most one is ever meaningfully set). `getLockState()` then reports `NONE` instead of a
  lock mode whose secret can't be verified, which — combined with the JWT also having failed
  to decrypt via the same invalidated keyset — routes to Login, not a dead end.

Known, accepted gap: if only the JWT/refresh-token keyset entry is somehow unreadable while
PIN/pattern remain fine (or vice versa) — implausible since all four share one keyset, but
not provably impossible — the user lands on Login having lost a PIN/pattern they didn't need
to lose. Not specifically handled; the shared-keyset design makes this a very small residual
risk, not eliminated by construction.

Never throws past `TokenCryptoManager`: every exception path inside it is caught and mapped
to `Failed`/plaintext-fallback, so a decrypt/encrypt failure can't crash-loop the app on
launch — the worst case is being routed to Login, an existing, already-handled UI state.

## Backup exclusion

`res/xml/backup_rules.xml` (API < 31 fallback) and `res/xml/data_extraction_rules.xml`
(API 31+, both `<cloud-backup>` and `<device-transfer>`) now exclude:
- `datastore/pesamind_prefs.preferences_pb` (domain `file`) — the DataStore file
  `TokenManager` writes to. Confirmed via grep this file is exclusive to `TokenManager`; no
  other manager's `preferencesDataStore(...)` call shares this name.
- `pesamind_token_keyset_prefs.xml` (domain `sharedpref`) — the wrapped Tink keyset itself.

Both are excluded because the Keystore key wrapping the keyset is device-bound and never
migrates with either backup path — a restored/transferred copy of either file would be
permanently undecryptable on the receiving device. Excluding them means a fresh install on a
new device simply doesn't see these files at all (clean "logged out" state), which is a
cleaner outcome than reaching the decrypt-failure path above at all.

## Dependency change

`gradle/libs.versions.toml`: removed `androidxSecurityCrypto`/`androidx-security-crypto`
(confirmed zero usages), added `tink`/`tink-android`. `app/build.gradle.kts`: swapped the one
`implementation` line accordingly.

**Open item, flagged plainly rather than guessed past:** `tink` is pinned to `1.15.0` in the
version catalog. Network tools (`WebSearch`, `WebFetch`, and even a plain `curl` via Bash)
were all unavailable for the entire implementation session (a "temporarily unavailable"
classifier error), so this version could not be confirmed against Maven Central as this
repo's build config was actually written. **Verify before merging:**
```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -A1 "com.google.crypto.tink"
```
or check https://mvnrepository.com/artifact/com.google.crypto.tink/tink-android directly.
**Update:** `1.15.0` resolved successfully in `assembleDebug` below and all Tink API calls
(`AeadConfig.register()`, `AndroidKeysetManager.Builder()`, `KeyTemplates.get("AES256_GCM")`,
`keysetHandle.getPrimitive(Aead::class.java)`, `Aead.encrypt`/`decrypt` with AAD) compiled
clean against it on the first try — confirms the version exists and this ADR's assumed API
surface matches it. Still worth a quick check that `1.15.0` is the *latest* stable release
(not just *a* working one) before merging, since that couldn't be confirmed against Maven
Central directly this session.

## Verification

- `./gradlew ktlintFormat` then `./gradlew ktlintCheck` — both green, no manual formatting
  fixes needed beyond what `ktlintFormat` applied automatically.
- `./gradlew assembleDebug` — **BUILD SUCCESSFUL in 2m 11s, 44 actionable tasks: 44
  executed.** No compile errors in `TokenCryptoManager.kt`/`TokenManager.kt`; the only
  warnings in the log are pre-existing deprecated-icon warnings in unrelated screen files,
  not from this change.
- **Not run this session — needs a device/emulator, unavailable here (same standing gap
  every ADR-0004 slice has noted):** an on-device check of the four behavioral requirements
  — existing plaintext values migrate without logging a user out, a fresh install's
  DataStore/keyset files are absent from `adb backup` output, decrypt-failure routes to
  Login without crashing, and the PIN/pattern unlock flows still accept the correct
  PIN/pattern post-migration. Static reasoning above (the self-describing `ENC1:` prefix,
  the unconditional encrypt-going-forward on every save, the null-semantics-preserving read
  path) covers this without a device, but per this repo's own instruction to say so
  explicitly when a behavior can't be measured directly: this is reasoned, not observed.
- No unit tests written for `TokenCryptoManager`/`TokenManager`'s new migration and
  decrypt-failure paths — not requested this session, but worth naming as a gap: the standing
  `runTest`-vs-`runBlocking` landmine (`.claude/CLAUDE.md`) applies to any future test here,
  since `TokenManager.init()` launches a background coroutine.

## Consequences

- `core/storage/TokenCryptoManager.kt` is the one place Tink/Keystore logic lives; any future
  secret needing the same treatment should extend it rather than hand-rolling a second crypto
  layer (now stated directly in `.claude/CLAUDE.md`'s Secrets section).
- `androidx.security.crypto` is no longer a dependency in this repo.
- `TokenManager`'s public API is unchanged — every existing caller (10 files, confirmed via
  grep before this change) needed zero edits.
- Not addressed here, out of scope: `ThemeManager`'s separate raw-`SharedPreferences`
  inconsistency (`docs/vault/05-debt-burndown.md`), and the pre-existing `Setmonthlybudgetscreen
  .kt`/`Yealy*` naming debt — unrelated to this change.
