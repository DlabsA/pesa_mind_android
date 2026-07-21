# ADR-0002: Remove hardcoded release-signing credential from `app/build.gradle.kts`

**Status:** Accepted
**Date:** 2026-07-20

## Context

`docs/vault/01-architecture-audit.md` finding 1 (CRITICAL): `app/build.gradle.kts`
committed a cleartext literal fallback for the release keystore's `storePassword` and
`keyPassword` —

```kotlin
storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "K@sh404730"
keyPassword = System.getenv("KEY_PASSWORD") ?: "K@sh404730"
```

— a direct violation of `.claude/CLAUDE.md`'s Secrets rule ("No secret, password, or key
ever has a literal fallback value in a committed file"), and the literal example that
rule was written against. The same keystore signs **both** release and debug builds
(`isMinifyEnabled = false`), so the leak covered every build variant, not just release.
Confirmed present across all 6 commits that have touched the file, i.e. present in git
history from the point it was introduced, not just the working tree.

## Decision

1. **Secret resolution moved to a gitignored `keystore.properties` at repo root**,
   resolved in this order (matching the existing `GOOGLE_ANDROID_CLIENT_ID` pattern in
   the same file): Gradle property (`-P`, or `~/.gradle/gradle.properties`, both picked
   up automatically by `findProperty`) → env var → `keystore.properties`. No literal
   fallback for `storePassword`/`keyPassword` — if both aren't resolvable, the entire
   `release` `signingConfig` block is skipped (`hasReleaseSigningConfig` gate), not
   defaulted to a baked-in value. `keyAlias`/`storeFile` keep non-secret defaults
   (`"pesa_mind"` / `~/.android/my-release-key.keystore`) since those are not the secret
   the rule targets — only the passwords had a cleartext fallback removed.
2. **`debug` build type no longer reuses the `release` signingConfig.** It previously set
   `signingConfig = signingConfigs.getByName("release")` explicitly; that line is
   removed, so debug builds fall through to the default AGP debug-keystore signing (auto
   -generated `~/.android/debug.keystore`, confirmed via `./gradlew :app:signingReport`).
   This was silently signing every debug build with the same production key material
   in addition to leaking its password.
3. **`.gitignore`** gained `keystore.properties`, `*.jks`, `*.keystore` (in addition to
   the existing `local.properties`/`.env` entries) so neither the properties file nor a
   keystore binary can be re-added by accident.
4. **Key rotation and git-history purge are explicitly out of scope for this change** —
   both are destructive/hard-to-reverse (password rotation affects the live signing key;
   history rewrite requires a force-push and breaks every collaborator clone) and are the
   user's call, not something to automate. Exact commands and caveats are written up in
   `docs/vault/REMEDIATION.md` for the user to run manually.

## Verification

`./gradlew :app:signingReport` run twice:
- **Without** `keystore.properties` present: `release` variant resolves to `Config: null
  / Store: null` (signing skipped, no crash, no fallback value) — `debug` resolves to the
  default `~/.android/debug.keystore`.
- **With** `keystore.properties` present (containing the real, now-to-be-rotated
  password): `release` resolves correctly to `~/.android/my-release-key.keystore` /
  alias `pesa_mind`.

## Consequences

- Anyone building a release variant locally must create `keystore.properties` (or set
  `KEYSTORE_PASSWORD`/`KEY_PASSWORD` env vars / Gradle properties) — there is no
  zero-config release build anymore. This is intentional; a zero-config release build
  was the mechanism of the leak.
- CI (if/when added) must inject `KEYSTORE_PASSWORD`/`KEY_PASSWORD` via env var or Gradle
  property from a secrets store — no file needed.
- The password itself is not yet rotated by this change — see
  `docs/vault/REMEDIATION.md` step 1. Until the user rotates it, the value that was
  public in history is still the live signing password.
