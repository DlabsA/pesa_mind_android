# ADR-0004: Offline-first — Room-authoritative storage, durable sync, SMS ingestion & profile offline

**Status:** In progress. Step 1 (schema + migration) landed, but was inert — Room was
populated once by the migrator and never read again. This session: (1) shipped a
standalone hotfix so SMS ingestion stops lying about success while Room is still
inert, (2) re-sequenced the remaining roadmap from six large infrastructure-first
steps into vertical slices that each ship a working offline capability end-to-end.
See "Roadmap revision" and "Hotfix" below.
**Date:** 2026-07-22

## Context

Every write path in this app — manual "Add Transaction", SMS-triggered transaction
creation, channel edits, profile edits — currently calls the network directly
(`ApiClient.api.createTransaction()` etc.) with no local-first write. Confirmed via
`android-auditor` scoping (see the audit trail in this repo's session history) and
direct code reading:

- `TransactionViewModel.createTransaction()`, called by both `AddTransactionScreen`
  and `SMSMessageProcessor`, wraps a direct `ApiClient.api.createTransaction()` call.
  On failure (offline = `IOException`/`UnknownHostException`) it sets an error message
  and **the transaction the user typed, or the SMS that arrived, is simply discarded**
  — nothing is queued, nothing is persisted locally first.
- `TransactionManager`/`ChannelManager`/`BudgetManager`/`AccountManager` (all
  `core/storage/`, all DataStore-`Preferences`-backed) store their data as whole-list
  Gson JSON blobs under a single key each — no per-row structure, no dirty-tracking,
  no query capability. They're opportunistic caches read-then-overwritten around a
  network call, not a source of truth.
- **Discovered in scoping, not previously known:** `TransactionManager.init()` and
  `BudgetManager.init()` are never called anywhere in the app (unlike the other 5
  managers, which `PesaMindApp.onCreate()` initializes). Every method on both
  guards on `isInitialized()`, so their DataStore caching has been silently dead
  code the entire time — a second, independent reason "offline" has never worked,
  on top of the direct-network-call issue. Not fixed here: both managers are being
  retired by this migration, not repaired. The migrator (below) initializes them
  itself defensively so a retry of the one-time import doesn't depend on this bug
  being un-fixed.
- No idempotency mechanism exists anywhere in the client: zero UUID generation in
  the codebase (`grep -r "randomUUID"` — zero hits before this change), no
  `Idempotency-Key` header, no `id` field on any create request DTO. The Django
  backend's actual behavior when sent a client-supplied id is **unverified** — see
  the idempotency section below.
- No delta/since-timestamp query support exists on the read side either:
  `getTransactions()`, `getChannels()`, `getProfile()` in `ApiService.kt` take zero
  query parameters. A true "pull changes since `lastSyncAt`" is not achievable
  against today's backend contract — see the sync section below.

## The core mandate

1. **Room is the single source of truth.** Screens observe Room `Flow`s through
   `UnifiedViewModel`/`UiState`. The network never writes directly to the UI and
   never blocks a read.
2. **Client-generated stable IDs.** Every syncable row gets a UUID (v4) at creation,
   offline. Local identity is the UUID (`id`, the Room primary key); the server ID
   is a separate nullable column (`serverId`) populated once synced.
3. **Never lose a local write.** Mutations enter a durable outbox; nothing is
   hard-deleted until the server confirms (soft-delete + tombstone).
4. **Never clobber unsynced edits.** A pull must not overwrite a row where
   `dirty = true`.
5. **Refresh = sync, not replace.**

## Decisions made this session (with the reasoning, since several deviate from a naive reading of the mandate)

### Idempotency: client UUID in the request body, verified live, not blocked on backend confirmation

Chose to add `id: UUID` to create-request bodies (`TransactionRequest`,
`CreateChannelRequest`, etc. — wired in Step 3, not this commit) rather than an
`Idempotency-Key` header, and to treat the **first real sync as the verification
test** rather than pausing on backend confirmation. This is an accepted, explicit
risk: if the Django backend silently ignores the `id` field and assigns its own,
a retried create *could* still duplicate server-side. There is prior history of
UUID-validation issues with this backend (per the original task brief) that this
decision doesn't resolve — it accepts the risk and defers detection to Step 3's
live testing rather than guessing at a backend contract with no way to confirm it
from the Android client alone.

### Sync pull: full-list fetch + client-side diff, not true delta pull

Since `ApiService`'s GET endpoints take no query parameters, Step 3's sync worker
will fetch the complete list per entity and diff it against local Room state
(present in the pull + has a `serverId` but not locally known → new; known
locally + has a `serverId` but absent from the pull → server deleted it → apply a
tombstone; `dirty = true` locally → never overwritten by the pull, regardless of
what the pull contains). Heavier than a real delta pull, but it's the only thing
achievable without a backend change, and it still respects the conflict policy.

### Budgets are in scope (`MonthlyBudgetEntity`/`YearlyBudgetEntity`), added to the original entity list

`BudgetManager` has the identical blob-of-list anti-pattern as
Transactions/Channels. Added to this migration rather than left as a follow-up.

### Budget line items are a JSON column, not a normalized child table

`MonthlyBudgetResponse`/`YearlyBudgetResponse` embed a nested
`transactions: List<BudgetTransactionResponse>`, and the *only* mutation endpoint
for them is a budget-level `PATCH` carrying a `transaction_ops` add/update/delete
batch — there is no per-line-item REST route. Since the backend's real mutation
unit is "the whole budget," that's this schema's dirty/outbox granularity too:
`MonthlyBudgetEntity.transactionsJson`/`YearlyBudgetEntity.transactionsJson` store
the line items as a Gson blob, mirroring what `BudgetManager` already did. A
future line-item-level offline-edit UX (edit one income row while offline) is out
of scope for Steps 1-3 as scoped; flagging here so it isn't mistaken for an
oversight later.

### `TransactionEntity.channelId` is a nullable FK, resolved best-effort by name

`TransactionDetails` (the GET response) only ever returns `channel_details_name`
(a display string) — **never a channel id**. `TransactionRequest` (the POST body)
does carry `channel_details_id`, so locally-created transactions always have a
correct `channelId` (the creating code already knows which local channel it is).
Transactions arriving via a future full-pull, though, can only be linked back to a
local `ChannelEntity` by matching `channelDetailsName` against `ChannelEntity.name`
— and only when that name is unique among local channels (see
`resolveUniqueChannelIdsByName` in `PrefsToRoomMigrator.kt`, unit-tested). An
ambiguous or unmatched name leaves `channelId = null`; `channelDetailsName` is
always kept regardless, so the UI never loses the display value even when the FK
link can't be established. This is a real backend-contract gap worth raising with
whoever owns the Django API, not something fixable from the client.

### `ProfileEntity` uses a fixed sentinel primary key, not a generated UUID

Unlike transactions/channels, a profile is never POST-created client-side with an
idempotency key — it's always synced in from the existing server user record on
login, then edited via `PATCH /users/me`. A generated UUID would serve no purpose
here (there's no create-with-idempotency-key scenario to protect), so
`ProfileEntity.id` defaults to a fixed constant (`"local_profile"`) — there is
exactly one row, ever.

### Unique indices don't survive soft-delete — removed, deferred to the repository layer

`android-reviewer` caught this on the first pass: `serverId` (Channel/Transaction/
MonthlyBudget/YearlyBudget) and `(month, year)`/`year` (Monthly/YearlyBudget) were
originally `unique = true` indices. Since rows are soft-deleted (retained,
`deletedAt` set) rather than removed, a table-wide unique constraint would block
ever creating a new 2026 yearly budget after an old one for that year was deleted
— the constraint doesn't distinguish live rows from tombstoned ones. Room's
`@Index` can't express a partial/filtered index (`WHERE deletedAt IS NULL`), so
these are now plain (non-unique) indices, kept for query performance only;
"at most one live row per year/month+year/serverId" becomes a repository-layer
check in Step 2/3, not a schema-enforced one. `smsSourceKey`'s uniqueness is
*not* changed by this — SMS dedup deliberately needs to hold across a
soft-deleted row too (reprocessing an SMS whose resulting transaction the user
since deleted must stay a no-op, not resurrect a duplicate), so that index stays
`unique = true` as originally designed. `OutboxEntry`'s `(entityType, entityId)`
uniqueness is unaffected for the same reason — outbox rows are hard-deleted on
completion, not soft-deleted.

### `TokenManager`'s plaintext secrets: found, flagged, not touched

Discovered in scoping that `TokenManager` stores JWT/PIN/pattern in plain
DataStore, not `androidx.security.crypto`-encrypted storage — a real, pre-existing
violation of this repo's own secrets rule. Deliberately out of scope for this
task (auth/lock hardening is a different subsystem than offline data); left as a
known follow-up, not bundled in here.

## Prefs vs. Room classification (Step 0 audit, confirmed against source)

| Store | Category | Notes |
|---|---|---|
| `TransactionManager` | → Room (`TransactionEntity`) | blob-of-list, never actually initialized (see above) |
| `ChannelManager` | → Room (`ChannelEntity`) | two blobs merged into one `smsNotificationEnabled` column |
| `AccountManager` | → Room (`ProfileEntity`, single row) | 6 scalar keys, not a blob |
| `BudgetManager` | → Room (`MonthlyBudgetEntity`/`YearlyBudgetEntity`) | two list blobs + two redundant "current" caches (not migrated separately — already covered by the list) |
| `NotificationStorage` pending-messages | Not migrated this step | newline-concatenated (not valid JSON-array), write-only today (nothing reads it back) — Step 4's SMS dedup key replaces its purpose, doesn't inherit its shape |
| `ThemeManager` | Stays DataStore (genuine setting) | uses raw `SharedPreferences`, not DataStore — pre-existing inconsistency, not touched |
| `SyncPolicy`, `*_last_sync` scalars | Stays DataStore (genuine setting) | `lastSyncAt`-style timestamps are settings, not relational data |
| `StreakSessionCache` | Neither (RAM-only today) | no persistence at all currently; out of scope |
| `TokenManager` | Stays encrypted-storage (secrets) | currently plaintext DataStore — flagged above, not fixed here |

## Step 1 (this commit): schema + migration

**Entities** (`core/database/entity/`): `TransactionEntity`, `ChannelEntity`,
`ProfileEntity`, `MonthlyBudgetEntity`, `YearlyBudgetEntity`, `OutboxEntry`,
`Tombstone`. Every syncable *deletable* entity carries `id: String` (UUID PK),
`serverId: String?`, `syncStatus: SyncStatus` (PENDING/SYNCING/SYNCED/FAILED),
`dirty: Boolean`, `updatedAt: Long`, `deletedAt: Long?` and (except `ProfileEntity`
— see below) `createdAt: Long`. `ProfileEntity` is the one exception: no
`createdAt` (there's no meaningful "row creation time" for a synced-in profile)
and no `deletedAt` (a profile is never deletable — there's always exactly one row
for as long as the user is logged in). `TransactionEntity.channelId` and
`MonthlyBudgetEntity.yearlyBudgetId` are `@ForeignKey`s with `onDelete =
SET_NULL` (soft-delete is the norm; a hard parent delete should never cascade-lose
a child row). Indices on every FK, `syncStatus`, `updatedAt`, and `serverId` (plain,
not unique — see "Unique indices don't survive soft-delete" below), plus
`TransactionEntity.smsSourceKey` (unique, nullable) for Step 4's dedup and
`OutboxEntry`'s `(entityType, entityId)` (unique) so a repository upserts one
outbox row per affected entity rather than piling up one per edit.

**DAOs** (`core/database/dao/`): one per entity. `TransactionDao.pagingSource()`
returns `PagingSource<Int, TransactionEntity>` (Room Paging 3 integration) since
the list can be thousands of rows; `observeAll()` also exists for screens that
need reactive totals without paging. Each DAO exposes `getDirty()`/`getByStatus()`
for the Step 3 sync worker and `getAllIncludingDeleted()` for the full-pull diff.

**`PesaMindDatabase`** wires all 7 entities + a `Converters` class (enum
`TypeConverter`s for `SyncStatus`/`OutboxEntityType`/`OutboxOperation`), provided
via a new Hilt `DatabaseModule` (`core/di/`) — matching where Hilt is already
established (`NetworkModule`), not the plain-singleton-object pattern the
DataStore managers use, since Room is new cross-cutting infrastructure in the same
category as the network layer, not a manager being added to an already-large set.

**Migration** (`PrefsToRoomMigrator`, `core/database/migration/`): one-time,
idempotent. Guarded twice — a DataStore flag (fast path once done), and a
"does Room already have rows" check beneath it, so a process death landing
between the Room transaction committing and the flag being written can't cause a
retry to double-insert. Count verification (source DTO count vs. Room's own
post-insert `count()`, not the in-memory list we built — a serverId collision
under `OnConflictStrategy.REPLACE` would silently drop a row, which comparing our
own list against itself would never catch) happens **inside** the same Room
transaction as the inserts, so a failed check rolls back everything atomically
instead of leaving partial data that the "already populated" guard would then
mistake for a completed run. Wired into `PesaMindApp.onCreate()`, fire-and-forget
on a background dispatcher.

**Known migration limitations, by design, not oversight:**
- Migrated rows get `System.currentTimeMillis()` as `createdAt`/`updatedAt` —
  the source DTOs never captured a true original timestamp, so "now" is the best
  available value.
- `smsSourceKey` is `null` for every migrated transaction — retroactive SMS
  dedup keys aren't derivable from already-created rows; the dedup mechanism only
  applies going forward (Step 4).
- Migrated rows are inserted as `syncStatus = SYNCED, dirty = false` — they came
  from the server already, so there's nothing to push.

**Discovered incompatibility, fixed:** Hilt's `@Inject lateinit var` field
injection into `PesaMindApp` failed the build (`hiltJavaCompileDebug`:
`IllegalStateException: Unable to read Kotlin metadata due to unsupported
metadata version`) — Dagger 2.51.1's bundled Kotlin-metadata reader can't parse
the metadata format Kotlin 2.1.0 emits for field-injection sites specifically
(nothing else in this codebase currently uses `@Inject lateinit var`;
`MainActivity`'s `@AndroidEntryPoint` has no injected fields, only
`hiltViewModel()` calls, which use a different codegen path). Worked around with
`@EntryPoint`/`EntryPointAccessors` (`DatabaseEntryPoint.kt`) instead of a field —
a different Dagger codegen path that isn't affected. This is also the correct
long-term pattern for Step 4, where `SmsReceiver` (a `BroadcastReceiver`, not
naturally constructor-injectable) will need the same kind of access to a
repository. Upgrading the pinned Hilt version was considered and rejected as
out-of-scope risk for this commit — it would touch the DI setup broadly for a
problem this narrower fix fully resolves.

**Verification:** 9 JVM unit tests (`PrefsToRoomMigratorTest`) cover the pure
DTO→Entity conversion functions and the name-based channel-resolution logic
(unique match, ambiguous match, no match) without needing an Android/SQLite
runtime. The transactional/idempotency behavior (`db.withTransaction`, the
"already populated" guard) needs a real database and is exercised by the manual
airplane-mode test below, not an automated instrumented test in this commit.

**`android-reviewer` findings, fixed before commit:**
- (confidence 82) The migration coroutine in `PesaMindApp.onCreate()` was bare
  `CoroutineScope(Dispatchers.IO).launch { }` with no exception handling — any
  throw (including the deliberate in-transaction `check()` failures designed to
  force a rollback) would crash the process on an unsupervised startup coroutine.
  Since `markDone()` never runs on failure, a deterministic cause would crash
  every subsequent launch too — the app couldn't boot. Fixed with a `try/catch`
  that logs and lets the app continue; the migration simply retries next launch.
- (confidence 62) `serverId = id` copied the DTO's id verbatim for
  channels/transactions/budgets, but every DTO defaults `id` to `""`, and only
  the profile conversion guarded with `.ifBlank { null }`. A blank id from a
  corrupt cache blob would've been stored as a real `serverId` value and could
  collide with another blank-id row. Fixed to match the profile path everywhere.

## Roadmap revision (this session)

The original Step 2→6 ordering shipped two large infrastructure commits (Step 2:
repositories, Step 4: SMS) before anything actually worked offline, and Step 2+4
*alone* still couldn't fix SMS — SMS needs offline channel auto-creation **and**
offline transaction creation together, which the old split put in different steps.
Replacing the roadmap with vertical slices, each independently shippable and each
proving the write→outbox→sync pattern before the next slice reuses it:

- **Hotfix** (this commit) — SMS false-success notification. No Room. Ships
  immediately, independent of everything else below.
- **Slice A** — the SMS capture path, offline end-to-end: `ChannelRepository` +
  `TransactionRepository` + outbox + sync worker + SMS rewire + sync-status UI.
  Broken into three independently-green commits (A1 repositories, A2 outbox+sync
  worker, A3 SMS rewire+UI) rather than one large commit, per the constraint that
  writes queue after A1 but nothing drains them until A2 — not shippable mid-slice,
  fine on a branch.
- **Slice B** — profile + budgets offline-first, reusing Slice A's now-proven
  repository/outbox/sync pattern.
- **Slice C** — refresh UX polish, dead-code removal (`TransactionManager`,
  `BudgetManager`, `NotificationStorage`), remaining screens.

This supersedes the old "Step 2/Step 3/.../Step 6" numbering below; those sections
are kept as historical record of the original (superseded) plan and the Step 1
work that already landed.

## Step 0 verification (this session, before Slice A)

Re-verified three Step 1 loose ends and the migration's real-world effect, using
`data-path-tracer` for the 8 core user-action paths instead of hand-grepping each:

- **`fallbackToDestructiveMigration()`**: not present in `DatabaseModule.kt`. Already
  clean — no fix needed.
- **`exportSchema`**: flipped `false` → `true` this commit. Added
  `room.schemaLocation` via `defaultConfig.javaCompileOptions.annotationProcessorOptions`
  in `app/build.gradle.kts` (kapt reads this the same way ksp reads its schemaArgs —
  no ksp in this project, Room uses kapt). Generated and committed
  `app/schemas/cc.dlabs.pesamind.core.database.PesaMindDatabase/1.json`. Free at
  version 1; would have been unreconstructable once a v2 migration shipped without it.
- **`@HiltWorker` + `@AssistedInject` spike**: compiles clean under this project's
  pinned Kotlin 2.1.0 / Hilt 2.51.1 / kapt setup. kapt falls back to Kotlin
  language-version 1.9 for stub generation (`w: Support for language version 2.0+ in
  kapt is in Alpha`), which sidesteps the metadata-version bug that broke
  `@Inject lateinit var` field injection (documented in Step 1 above) — assisted
  injection uses a different codegen path, same as the `@EntryPoint` workaround did.
  **Decision: no fallback `WorkerFactory` needed.** Slice A's sync worker can use
  `@HiltWorker`/`@AssistedInject` directly. Spike file and its temporary
  `androidx.work`/`androidx.hilt:hilt-work` dependency additions were deleted/reverted
  after confirming — those dependencies land for real in Slice A2 when the sync
  worker is actually written.
- **Did the Step 1 migration move any real rows?** Unvalidated by any live run (no
  device/emulator available in this session to check `adb shell run-as ... sqlite3`).
  Static analysis is conclusive enough to report though: `PesaMindApp.onCreate()`
  initializes `TokenManager`, `AccountManager`, `ChannelManager`, `NotificationStorage`,
  `ThemeManager` — **not** `TransactionManager` or `BudgetManager`. Every method on
  both of those guards on `isInitialized()` and silently no-ops otherwise, and this
  is true even though `BudgetManager` and `TransactionManager` **are** called
  throughout `BudgetViewModel`/`SetMonthlyBudgetViewModel`/`YearlyBudgetViewModel`/
  `TransactionViewModel` in real app code today — every one of those calls has
  always been a silent no-op. `AccountManager` and `ChannelManager`, by contrast,
  are properly initialized and populated by real usage (login writes the account
  cache, `loadChannels()` writes the channel cache). Conclusion: the migration
  likely moved real profile and channel rows on a device that has actually been
  used, but **zero transactions and zero budgets**, regardless of how much real
  transaction/budget history that device has — first sync in Slice A/B remains the
  true population event for those two entities, not something already exercised by
  Step 1. Do not treat Step 1's migration as validated; it has never had real data
  to move.

## Hotfix (this commit): honest SMS-ingestion failure reporting

`SMSMessageProcessor.processMessage` called `viewModel.createTransaction(...)` —
which isn't even `suspend`, it just launches its own `viewModelScope.launch` — and
then unconditionally persisted a pending-message record and fired a "Spent/Received
X UGX" notification, regardless of whether the network call inside `createTransaction`
ever completed or succeeded. Offline, or on any transient network failure, this
meant a confirmed-looking success notification for a transaction that was silently
discarded.

Fix: split `TransactionViewModel`'s transaction-creation logic into a shared private
`performCreateTransaction()` plus two public entry points — `createTransaction()`
(unchanged fire-and-forget behavior for `AddTransactionScreen`, still backed by
`viewModelScope.launch`) and a new suspending `createTransactionAwaited()` that
returns a `TransactionCreationResult` (`Success`/`Failure`) instead of only mutating
`_state`. `SmsReceiver` already constructs a throwaway `TransactionViewModel()`
instance per message inside its own IO-dispatcher coroutine — it isn't attached to
any Compose screen — so `SMSMessageProcessor` can call `createTransactionAwaited`
directly and inspect the real result before doing anything user-visible.
`NotificationStorage.savePendingMessage` and the local notification now both live
inside the success branch only; on failure, the processor logs a warning and
returns, doing neither. No queueing or retry — Slice A removes this failure mode
entirely by making the write local-first; this hotfix only makes today's failure
mode honest instead of ships-with-a-lie.

`offline-sync-reviewer` caught that an early version of this refactor treated a 2xx
response with an empty/unparseable body as `Failure`, where the pre-refactor code
treated it as success (no state/list update, but still a success message and a
published event with an empty transaction id). Since there's no idempotency key
yet, flipping that to `Failure` would make `AddTransactionScreen` show an error on
an HTTP-success response — inviting a user retry that POSTs a real duplicate.
Fixed by making `TransactionCreationResult.Success.transaction` nullable so an
empty-body 2xx still resolves to `Success`, matching old behavior exactly. With
that fix, this commit has no behavior change beyond the SMS path's honest failure
reporting; No Room dependency, standalone, independently revertable.

`android-reviewer` additionally caught that `createTransaction()` and
`createTransactionAwaited()` had forked, byte-for-byte-duplicated `_state`
transition logic (the same `isSaving`/`message`/`error` copies written in two
places). Collapsed `createTransaction()` to `viewModelScope.launch { createTransactionAwaited(...) }`
and discard the result — `createTransactionAwaited` is now the single owner of
those state transitions. Doing this exposed a real bug in my own first pass: the
awaited function's validation-failure early-return returned `Failure` but never
wrote the message into `_state`, which would have silently broken
`AddTransactionScreen`'s validation-error UI (blank note, non-positive amount,
etc. would fail with no visible feedback). Fixed by setting `_state` on that
branch before returning.

## Airplane-mode acceptance test (target state — full test only meaningful once Steps 2-4 land; not yet exercisable from Step 1 alone)

1. Enable airplane mode.
2. Create a transaction manually — appears instantly in the list.
3. Tap a captured SMS — appears instantly in the list.
4. Edit the profile — new values shown immediately.
5. Edit a channel — new values shown immediately.
6. Kill the app process entirely.
7. Re-launch (still offline) — all four edits are still present, unsynced.
8. Re-enable network.
9. Confirm: all four sync exactly once (check server-side for duplicates — this is
   where the idempotency risk noted above would surface), and none of the four
   edits gets clobbered by the subsequent pull.

## Consequences

- `TransactionManager`/`ChannelManager`/`BudgetManager`/`AccountManager` remain in
  place and still work exactly as before for now — Step 2 is what actually
  rewires the ViewModels to read/write through Room instead. This commit only
  adds the schema and a one-time import; it does not yet change any screen's
  behavior.
- Schema is version 1. `exportSchema` was `false` at Step 1 (no migration-testing
  infrastructure set up yet); flipped to `true` in the Step 0/hotfix commit above,
  with `app/schemas/…/1.json` committed — this section is kept for historical
  accuracy about what Step 1 itself shipped.
- Follow-ups tracked, not actioned here: `TokenManager` encryption,
  `NotificationStorage`'s non-functional pending-message store, `ThemeManager`'s
  raw-`SharedPreferences` inconsistency, budget line-item-level offline editing.
- `android-reviewer` also flagged (this session) that `TransactionViewModel` and
  its new `TransactionCreationResult` type live under `core/utils/`, violating the
  target contract's "no feature-specific ViewModels/types under `core/`" folder
  rule — pre-existing drift, not introduced by the hotfix, and relocating it
  touches enough call sites (home, transactions list, SMS) that it doesn't belong
  in a standalone hotfix. Tracked as part of Slice C's cleanup.
