# ADR-0004: Offline-first — Room-authoritative storage, durable sync, SMS ingestion & profile offline

**Status:** In progress. Step 1 (schema + migration), the hotfix, Slice A1
(Channel/Transaction repositories), Slice A2 (outbox drain + sync worker), and the
duplicate-channel/duplicate-transaction fix (case-insensitive channel resolution +
TID/smsSourceKey transaction dedup) have landed. Channels and transactions now
read/write through Room *and* push to / pull from the server. The backend contract
A2 shipped against unverified is now fully verified (see "Backend-contract
verification" below): the client-UUID idempotency gap and the `ChannelDesc`
question are both **closed** — fixed (idempotency) or confirmed already-safe
(`ChannelDesc`), each backed by real tests (Postgres integration + HTTP-level) and
a live curl-based test against a locally-running instance of the real backend
binary, all **in the backend repo, on a local branch, not yet deployed to
`api.dlabs.cc`**. The `DeletedAt`/soft-delete question is assessed (full
blast-radius across every backend domain) and correctly re-scoped as its own
follow-up rather than fixed inline. The delta-pull/cursor gap is confirmed absent
and remains its own scoped backend task, not started — Part 2 (a real delta pull)
stays blocked-on-backend. **Slice A3's SMS dedup-key wiring has now landed in full**
(case-insensitive channel resolution + TID-based transaction dedup — see
"Duplicate channels + duplicate transactions" below); SMS auto-create is still
network-first (not yet rewired local-first) and sync-status UI is still not done —
both remain Slice A3's open remainder. See "Roadmap revision", "Hotfix", "Slice A1",
"Slice A2", "Backend-contract verification", and "Duplicate channels + duplicate
transactions" below.
**Date:** 2026-07-23 (Slice A2); backend-contract verification + fix + tests +
live test, same day, follow-up session; duplicate-channel/duplicate-transaction fix,
2026-07-24, follow-up session; committed 2026-07-25

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
  Broken into three independently-green commits (A1 repositories — landed, A2
  outbox+sync worker — landed, A3 SMS rewire+UI — remaining) rather than one large
  commit, per the constraint that writes queue after A1 but nothing drains them
  until A2 — not shippable mid-slice, fine on a branch.
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

## Slice A1: Room-backed Channel/Transaction repositories

**Not shippable alone.** After A1, `createChannel`/`updateChannel`/`deleteChannel`/
`setSmsNotificationEnabled`/`createTransaction` all write to Room + a coalesced
outbox entry in one transaction, and nothing drains the outbox — no network push
exists until Slice A2. Fine on a branch; the app is fully usable offline (data
persists and the UI is reactive), but zero of it reaches the server yet.

### New files
- `core/database/OutboxCoalescer.kt` — pure decision function (`CoalesceDecision`:
  `WriteOutbox(operation)`/`HardDeleteNoOutbox`/`LeaveInFlight`) implementing the
  four coalescing rules (CREATE+UPDATE→CREATE, CREATE+DELETE→hard-delete+no-outbox,
  UPDATE+DELETE→DELETE, any-op-while-SYNCING→leave untouched). Zero Room/Android
  dependency, 13 JVM tests (`OutboxCoalescerTest`) written and passing *before* any
  repository call site used it, per the task's own ordering.
- `core/data/ChannelRepository.kt` / `core/data/TransactionRepository.kt` — plain
  singleton `object`s + `init(context)`, mirroring `ChannelManager`/`TokenManager`'s
  existing pattern rather than Hilt constructor injection, because
  `ChannelViewModel`/`TransactionViewModel` are plain `ViewModel()`s reached via
  `viewModel()` in Compose, not `hiltViewModel()` — converting every call site to
  Hilt-injected ViewModels was judged a wider change than this slice's scope. Room
  access itself still goes through the Hilt-provided `PesaMindDatabase` via the
  existing `DatabaseEntryPoint`, the same pattern `PrefsToRoomMigrator` established
  in Step 1. `android-reviewer` confirmed this is consistent with root CLAUDE.md's
  "follow the existing pattern in a given file rather than introducing DI
  inconsistently," not a violation.
- Repository-layer justification (`.claude/CLAUDE.md`: no repository for a single
  feature in isolation, only for real cross-feature reuse or multi-source merging):
  `android-reviewer` reviewed this specifically and confirmed it clears the bar —
  `ChannelRepository` has two genuine consumers (`ChannelViewModel` and the SMS
  auto-creation lookup in `ChannelManager.isSmsAllowedForSender`, both live code in
  this commit, not aspirational), and `reconcileFromServer`'s dirty/soft-delete
  conflict logic is real merge substance, not CRUD passthrough. Caveat honestly
  noted: neither repository imports `ApiClient` yet — the "network + cache merge"
  the rule names is still split across `ChannelManager`/`ApiClient` today and lands
  fully in Slice A2. The outbox + conflict-policy logic already present is enough
  to justify the layer now, not the complete merge.

### Deviations from the original plan (each surfaced by a review agent, all fixed)

- **Transaction list stays `Flow<List<TransactionDetails>>`, not Paging 3**, despite
  the ADR's original "transaction list uses the existing `PagingSource`" line.
  `TransactionListScreen` computes income/expense/saving totals and does
  client-side search/filter over the *entire* list, not just loaded pages — a real
  Paging 3 migration would need a second full-list flow just for totals anyway,
  which is a materially bigger, riskier screen rewrite than "channels/transactions
  read/write locally." Asked the user directly rather than guessing; confirmed to
  defer Paging 3 until the list is actually large enough to need it.
- **Filtered channel views were getting silently clobbered by the live Room
  collector** — `compose-perf` and `android-reviewer` independently caught this.
  `ChannelViewModel`'s `init` subscribes to `ChannelRepository.observeChannels()`
  for the ViewModel's whole lifetime; a one-shot `loadChannelsByType`/
  `loadChannelsByStatus` write into `_state.channels` was getting overwritten back
  to the full list the instant *anything* wrote to the channels table — which
  happens concurrently and often, since SMS auto-creation
  (`ChannelManager.isSmsAllowedForSender` → `ChannelRepository.reconcileFromServer`)
  writes to that same table from a background service, entirely independent of
  what's on screen. Fixed by tracking the active filter
  (`activeTypeFilter`/`activeStatusFilter`) and having the collector re-apply it on
  every emission, instead of a one-shot query racing an always-on collector.
- **`reconcileFromServer` could resurrect a soft-deleted row as a duplicate** —
  `offline-sync-reviewer` caught that `findByServerId`'s query filtered
  `deletedAt IS NULL`, so a channel/transaction the user deleted locally (but whose
  DELETE hasn't reached the server yet, since A2 doesn't exist) was invisible to
  the lookup — a subsequent `reconcileFromServer` call (reachable today via
  `isSmsAllowedForSender`'s auto-create path) would insert a fresh live row for the
  same `serverId`, resurrecting what the user deleted. Fixed by removing the
  `deletedAt IS NULL` filter from `findByServerId` (its only consumer needs to see
  tombstones) and changing the "leave untouched" condition in both repositories'
  `reconcileFromServer` from `existing.dirty` to `existing.dirty ||
  existing.deletedAt != null`.
- **SMS-created transactions would land with `smsSourceKey = null`, making the dedup
  unique index inert** — `offline-sync-reviewer` noted that A1 is the commit that
  actually routes `SMSMessageProcessor`'s writes into Room (the hotfix already
  wired it to `createTransactionAwaited`), so the duplication window that Slice
  A3's dedup key generation is meant to close opens now, not later. Added a
  dedup-safety check in `TransactionRepository.createTransaction` — when a caller
  supplies a non-null `smsSourceKey`, an existing row with that key is returned
  as-is (no-op) instead of inserting a duplicate — but deliberately did NOT wire
  `SMSMessageProcessor` to generate and pass a real key in this commit; that's
  still Slice A3's job per the original scoping, and doing it here would be scope
  creep into A3's commit. **Accepted, time-boxed risk:** between A1 landing and A3
  landing, an SMS reprocessed for any reason (redelivery, service restart) can
  still create a duplicate transaction, because nothing calls
  `createTransaction`/`createTransactionAwaited` with a real key yet. Closes as
  soon as A3 ships.
- **Live collectors had no error handling** — `android-reviewer` noted that if
  `ChannelRepository.observeChannels()`/`TransactionRepository.observeTransactions()`
  ever threw mid-collection, the collector coroutine would die silently and the
  screen would freeze on stale data with no visible error — worse than the old
  network code's UX, which at least surfaced a failure. Wrapped both `init`
  collectors in try/catch, setting `state.error` on failure.
- **`getAllChannels()`/`getAllTransactions()` were `observeXxx().first()`** —
  `android-reviewer` nit: this spins up and immediately cancels a fresh Flow
  subscription just for one snapshot. Replaced with dedicated one-shot DAO queries
  (`ChannelDao.getAllActive()`/`TransactionDao.getAllActive()`).

### Known, accepted gaps (tracked, not fixed here)

- **`ChannelRepository`/`TransactionRepository.reconcileFromServer` ship untested**
  — `android-reviewer` flagged this (`TransactionRepository`'s is also uncalled in
  A1, written now per this task's own instruction so Slice A2's full pull doesn't
  have to retrofit the conflict-policy primitive later). Both repositories'
  `database` field was changed from `private` to `internal lateinit var`
  specifically so a JVM test could inject a mocked `PesaMindDatabase`/DAO (no
  device/emulator was available in this session to run a real Room
  in-memory-database `androidTest`), and a `sync-test-author` task was dispatched
  to write mock-based JVM tests for the conflict policy (dirty-row-preserved,
  soft-deleted-row-not-resurrected, clean-row-updated, new-row-inserted, plus the
  SMS-dedup-safety no-op). That task did not complete in this session — mocking
  Room's `withTransaction` suspend extension from a plain Mockito test is a real
  obstacle, and rather than block Slice A1 on it indefinitely, shipping A1 with
  this gap explicitly open rather than silently claiming coverage that doesn't
  exist. The `internal` visibility seam stays in place for whoever picks this up
  next (either resolve the `withTransaction` mocking, or add Robolectric so a real
  in-memory Room database can be used instead of mocks — likely the more robust
  fix, and something Slice A2's sync worker will need anyway for its own DAO-level
  tests).
- **Any op arriving while the outbox row is `SYNCING` (`LeaveInFlight`) leaves the
  entity soft-deleted/edited but the outbox row untouched** — `offline-sync-reviewer`
  confirmed this is correctly wired (not a bug), but flagged it as the single
  load-bearing assumption Slice A2 must honor: A2's sync worker MUST re-check
  `dirty` before clearing it on a successful push, or a newer edit made mid-flight
  is silently lost instead of triggering a follow-up push. `OutboxCoalescer`'s own
  doc comment states this explicitly. Nothing to fix in A1; a landmine documented
  for A2's reviewer.
- **`TransactionViewModel.onStateEvent(UserLoggedOut)` clears `transactions` in
  state, but the live collector will repopulate it from Room on the next write** —
  `compose-perf` flagged this as the same "live collector fights a one-shot write"
  defect class as the channel-filter bug, but for logout rather than filtering.
  Not fixed here: whether logout should wipe local Room data at all is a broader
  auth-hardening question (adjacent to the already-flagged `TokenManager`
  plaintext-secrets gap), out of scope for a repository-wiring slice.
- **`ChannelRepository.createChannel`'s `userId = ""` and
  `TransactionViewModel.currentUsername()`'s exception-swallowing to `""`** —
  `android-reviewer` flagged that locally-created rows carry no real owner
  identity, which A2's outbox push may need to backfill from the auth session
  rather than assume is already correct. Noted as a TODO tied to A2, not fixed
  here.
- **`TransactionViewModel` remains under `core/utils/`**, violating the target
  contract's folder rule (no feature-specific ViewModels under `core/`) — flagged
  again by `android-reviewer`, pre-existing drift this slice didn't introduce.
  Relocating it touches enough call sites (home, transactions list, SMS) that it
  doesn't belong in this slice; tracked for Slice C's cleanup pass alongside the
  same finding already recorded in the Hotfix section above.

### Gates run
`offline-sync-reviewer`, `android-reviewer`, and `compose-perf` all reviewed the
diff; every actionable finding above was fixed before commit. `ktlintFormat`/
`ktlintCheck`/`test`/`assembleDebug` all green.

## Slice A2: outbox drain + sync worker

**Ships push + pull for Channel/Transaction.** After A2, `SyncWorker` drains the
outbox A1 started filling (periodic every 30 min + expedited on connectivity regain,
both `NetworkType.CONNECTED`) and reconciles a full-list pull against Room. Budgets/
profile stay out of scope (Slice B); SMS dedup-key generation and the SMS
auto-create path staying network-first are still Slice A3 (see "Deliberate scope
boundaries" below).

### Backend-blocker status: unconfirmed, not silently skipped

The task brief for this slice named three specific Go-backend blockers to check
before attempting a live end-to-end sync test (`ChannelDesc` closed-enum validation,
missing upsert-on-conflict for client-UUID creates, `DeletedAt *time.Time` vs
`gorm.DeletedAt`). None of these have any record in this repo, this ADR, or this
session's memory, and no backend repository is locally accessible to check against
directly — grepped this repo and adjacent `~/Github/dlabs/*` directories, found
nothing. **Their status could not be verified this session.** Per the brief's own
fallback, this slice was built against the *documented* backend contract
(`ApiService.kt` as it exists, read directly — not assumed) rather than blocked on
them, and no live end-to-end sync test was attempted as a result. This is a real
gap in verification, not an oversight: the "Airplane-mode acceptance test" below
remains unexercised beyond Room-level behavior until either those blockers are
confirmed resolved or a live test environment is available.

### Push

`SyncWorker.pushOutbox()` drains channel outbox rows before transaction rows (a
transaction create needs its channel's `serverId` to populate
`channel_details_id`, which only exists once the channel's own row has synced).
Per row: re-reads the *current* outbox row fresh (not a stale loop snapshot — see
"Findings fixed" below for why that distinction matters), marks it `SYNCING`
(so `OutboxCoalescer`'s `LeaveInFlight` rule protects it from a concurrent
repository write), sends the network call with the entity's client UUID as `id`
(the idempotency key — added as a new nullable-with-default field to
`TransactionRequest`/`CreateChannelRequest`, additive-only, verified against the
one existing `CreateChannelRequest` call site), then re-reads the entity's
`updatedAt` and compares it to the value captured just before dispatch. A mismatch
means a write landed mid-flight; `dirty` stays `true` and the row is requeued
(CREATE → UPDATE, since the row now exists server-side) instead of being silently
cleared. This decision — the exact re-check `OutboxCoalescer`'s `LeaveInFlight`
doc comment already required of this slice — is a new pure function,
`PushCompletionResolver`, not inlined, so it has direct unit coverage.

4xx → outbox row `FAILED`, entity `syncStatus = FAILED`, error persisted, **no
automatic retry**. 5xx/`IOException` → outbox row back to `PENDING` with bumped
`attempts`, and the whole worker run returns `Result.retry()` so WorkManager
backs off and retries later (4xx does *not* trigger this — confirmed only
`TRANSIENT_FAILURE` clears the "clean" flag `doWork()` checks). A successful
DELETE hard-deletes the local row — mandate #3 ("nothing hard-deleted until the
server confirms") means this *is* that confirmation.

### Pull

`api.getChannels()`/`api.getTransactions()` — full-list, not delta. Confirmed by
reading `ApiService.kt` directly: both GET endpoints take zero query parameters,
so there is no server-supplied delta cursor to fetch against. Per the task
brief's own instruction, this was **not** worked around by fabricating a
client-clock-based delta filter — pull stays a full-list fetch + client-side
diff, exactly as this ADR's original "Sync pull" decision (Step 1) already
predicted. `SyncMetadataManager.lastFullPullAt` is bookkeeping only (advanced
after a fully successful pull, for a future "last synced" UI indicator) — never
sent as a request parameter.

Each pulled row reconciles via `ChannelRepository`/`TransactionRepository.
reconcileFromServer` (existing since A1). A row present in a prior sync
(has `serverId`), not dirty, and absent from the current pull is treated as a
server-side deletion and soft-deleted locally — except for a row *this same
run's push phase* just confirmed with a 2xx (see Finding 2 below for why that
exclusion exists).

### `reconcileFromServer`'s "ships untested" gap: closed, not carried forward

A1 shipped `reconcileFromServer` with zero test coverage — mocking Room's
`withTransaction` suspend extension from Mockito was a documented blocker. Rather
than retry that blocker or add Robolectric for an in-memory Room instance (the
two options the task brief offered), the actual conflict-check that was inline
inside the `withTransaction` block (`existing.dirty || existing.deletedAt !=
null` → skip) was extracted into a new pure function, `ReconcileResolver`, with
its own JVM tests. `ChannelRepository`/`TransactionRepository.reconcileFromServer`
now delegate to it instead of inlining the check — same behavior, now testable
without touching Room at all. The DAO-glue that remains (look up by `serverId`,
branch on the decision) is the same trust tier `OutboxCoalescer`'s call sites
already have.

### `userId = ""` gap: fixed

`ChannelRepository.createChannel` now reads `AccountManager.getAccount().id` via
a new `currentUserId()` helper (swallowing to `""` on failure, mirroring
`TransactionViewModel.currentUsername()`'s existing pattern) instead of hardcoding
an empty string.

### Findings fixed (`offline-sync-reviewer` gate, before commit)

- **(confidence 88) A `SYNCING` outbox row orphaned by process death never synced
  again.** The drain only ever read `getByStatus(PENDING)`; nothing reaped a stale
  `SYNCING` row left behind by a run that died between claiming it and resolving
  it (app killed mid-push). That row — and the entity behind it — would never be
  retried again, and if the POST had actually reached the server before the
  kill, this was split-brain: server has the row, client never learns its
  `serverId` or clears `dirty`. Fixed: `reclaimStaleSyncingRows()` resets any
  `SYNCING` row back to `PENDING` at the start of each push phase — safe because
  a fresh `SyncWorker` instance is constructed per run, so anything still
  `SYNCING` when a new run starts can only be a leftover from a run that never
  finished resolving it.
- **(confidence 70) A transaction blocked on a permanently-failed (4xx) channel
  was stuck `PENDING` forever with no visible signal.** The skip-until-channel-
  synced branch didn't distinguish "channel not synced yet" from "channel synced
  and will never sync" (4xx is terminal, never auto-retried). Fixed:
  `pushTransactionEntry` now checks the blocking channel's `syncStatus`— if
  `FAILED`, the transaction is marked `FAILED` too (with an error naming the
  blocking channel) instead of being silently re-skipped every run.
- **(confidence 65) Same-run push-then-pull could soft-delete a row this run
  just synced, permanently.** `doWork()` pushes then pulls in one run. If a
  freshly-created channel's push succeeded (2xx, `serverId` assigned) but the
  same run's `getChannels()` call raced a replication-lagged backend that
  hadn't yet observed it, the row would look server-deleted (`serverId` not in
  the pull) and get soft-deleted locally — and since `ReconcileResolver` refuses
  to touch a tombstoned row, a later pull that *did* see the row could never
  un-delete it. The backend's actual replication/consistency behavior is
  unverified (see "Backend-blocker status" above), so this couldn't be ruled out
  by checking the backend directly. Fixed: `SyncWorker` now tracks
  `justSyncedChannelServerIds`/`justSyncedTransactionServerIds` (populated
  whenever this run's own push phase clears `dirty` on a row) and excludes them
  from the same run's server-side-deletion check, regardless of what the
  backend's actual consistency behavior turns out to be.
- **(confidence 52) The `SYNCING`-claim write could drop a concurrently-coalesced
  operation.** The original loop captured a snapshot of pending outbox rows once,
  then claimed each one by writing back that same stale snapshot. A repository
  write that coalesced the row (e.g. UPDATE→DELETE) in the window between the
  snapshot and the claim would be overwritten by the claim, silently reverting
  the row to the stale pre-coalesce operation — concretely, a user's offline
  delete of an already-synced channel could be lost, leaving the channel alive
  on the server forever. Fixed: the claim step now re-reads the *current* outbox
  row via `outboxDao.findFor(...)` immediately before claiming it (and no-ops if
  it's no longer `PENDING`), so the operation actually pushed is always the
  latest coalesced one.

### Deliberate scope boundaries (not gaps rediscovered — named on purpose)

- No update/delete endpoint exists for transactions (`ApiService` has none) —
  the worker only ever pushes transaction CREATEs; any other transaction outbox
  operation (unreachable from any current repository call site) fails loudly
  rather than crashing.
- SMS dedup-key generation (`smsSourceKey`) still isn't wired into
  `SMSMessageProcessor` — Slice A3.
- `ChannelManager.isSmsAllowedForSender`'s auto-create path still calls
  `ApiClient.api.createChannel()` directly (network-first, fails offline) —
  rewiring it local-first is Slice A3's "SMS rewire," not this slice.
- The `Tombstone` entity/table from Step 1's schema remains completely unused —
  server-side deletions are applied by soft-deleting the Channel/TransactionEntity
  row directly. Confirmed via grep that nothing writes to `tombstoneDao()`
  anywhere. Flagging for whoever picks this up next to either wire it to a real
  purpose or remove it — not fixed here, budget-conscious per this slice's own
  scope.
- Two different WorkManager unique-work names (`pesamind_sync_periodic`,
  `pesamind_sync_one_shot`) can in principle run `SyncWorker.doWork()`
  concurrently (WorkManager's `KEEP` policy only dedupes *within* one unique
  name). The push/pull logic doesn't wrap outbox claims in a database-level
  transaction, so two concurrent runs racing the same outbox row is a real,
  un-fixed edge case — not caught by this slice's review pass, noted here as a
  follow-up rather than silently left undocumented.

### Gates run
`offline-sync-reviewer` reviewed the diff (per the task brief's own instruction —
one gate for this slice, not three, since the diff doesn't touch a ViewModel or
Composable). All four findings above were fixed before commit.
`ktlintFormat`/`ktlintCheck`/`test`/`assembleDebug` all green, including after the
fixes.

## Backend-contract verification (2026-07-23, follow-up session)

A2 shipped with the backend contract explicitly unverified ("Backend-blocker
status" above) because no backend repository was locally accessible from the
`pesa_mind_android` working directory — every `~/Github/dlabs/*` directory was
checked and none of them is the Go backend. **That conclusion was wrong**: the
backend (Go/Gin/GORM, module `pesa-mind`) lives at `~/Github/Personal/pesa-mind`
— outside `~/Github/dlabs/` entirely, hence missed by every prior grep. Now
recorded in this session's agent memory (`ref-backend-repo-location`) so it isn't
re-lost. In that repo, "Channel" is Android-only naming — the backend calls the
same table/domain `ChannelDetails`, under the `category` package, exposed at
`/api/v1/categories` (`cmd/api/main.go:89-94`), not a `channel` package.

This section resolves the five specific unknowns the A2/A3 task brief named,
each checked against source, not inferred from `ApiService.kt`:

**Status of the three Part 1 items: 1 and 2 closed (fixed + tested + live-verified,
not yet deployed), 3 closed (already safe, locked in with a test), 4 (`DeletedAt`)
scoped as a follow-up, not fixed inline as instructed. Cursor/delta support (item 5
below, needed for Part 2) confirmed absent — its own future backend task.**

**1+2. Client-supplied `id` binding + upsert-on-conflict — confirmed broken,
now fixed and verified with real tests (branch
`fix/idempotent-channel-transaction-create`, commits `61802b2` + `ecacecf`, not
deployed).** Neither `dto.CreateTransactionRequest`
(`internal/interfaces/http/dto/transaction_dto.go`, pre-fix: lines 3-8) nor
`dto.CreateChannelDetailsRequest` (`internal/interfaces/http/dto/category_dto.go`,
pre-fix: lines 18-24) declared an `id` field. Gin/`encoding/json` binding silently
drops JSON keys with no matching struct field (no `DisallowUnknownFields` anywhere
in the repo — confirmed by grep), so the `id` Android has been sending since A2
(`TransactionRequest.id` / `CreateChannelRequest.id` in `ApiModels.kt`) was read by
nobody. Both `Service.Create` methods (`transaction/service.go:29-38`,
`category/service.go:18-27`, pre-fix) constructed their model without ever touching
`BaseModel.ID`, and `BaseModel.ID` (`utils/model.go:10`) carries
`gorm:"...default:uuid_generate_v4()"`, so GORM always let Postgres mint a fresh
UUID — every create, every time, idempotency key or not. Neither repository's
`Create` (`transaction/gorm_repository.go:16-18`, `category/gorm_repository.go:18-20`,
pre-fix) had an `ON CONFLICT` clause either — moot pre-fix, since the server never
saw a client ID to collide on. **This combination is a worse failure mode than a
500 on retry**: a timed-out POST that actually succeeded server-side, followed by a
client retry, doesn't error — it silently creates a second row with a different
server ID.

Fix: `id` is now an optional, `uuid4`-validated field on both request DTOs; both
handlers parse it and pass it through; both `Service.Create` methods take a new
`id *uuid.UUID` param and set it on the model before insert when supplied; both
`GormXxxRepository.Create` methods now use `.Clauses(clause.OnConflict{Columns:
[]clause.Column{{Name: "id"}}, DoNothing: true})` and, when `RowsAffected == 0` (a
genuine retry), re-fetch and return the row actually persisted from the first
attempt instead of the retried call's stale local timestamps.

Verification, in increasing order of realism:
- `internal/domain/transaction/gorm_repository_test.go` — two new tests against a
  real local Postgres (the `pesa-mind-db-1` docker-compose container, not a mock or
  a different SQL dialect): `TestGormTransactionRepository_Create_IdempotentOnClientID`
  (create twice with the same client id → one row) and
  `_NoClientID_StillGetsFreshID` (the no-id path is unaffected). Skips gracefully if
  no local DB is reachable — matches the repo's existing `db_test.go` convention.
- `internal/interfaces/http/handlers/category_handler_test.go` — two new tests
  (sqlite in-memory, no Docker needed) exercising the *full* HTTP path (JSON body →
  binding → handler → service → repository):
  `TestCategoryHandler_Create_IdempotentOnClientID` and
  `TestCategoryHandler_Create_AcceptsArbitraryChannelDesc` (folds in finding #3
  below). This is where a real, unrelated GORM behavior surfaced: `ChannelDetails`
  has a non-nil `User *user.User` belongs-to association once the handler builds it,
  and GORM's `Create` cascades an `INSERT ... ON CONFLICT DO NOTHING` into the
  `users` table by default — confirmed by the test 500ing with "no such table:
  users" before a `users` table was added to the test schema. Pre-existing behavior
  (not introduced by this fix, not present in the diff, safe in production since a
  real user row with that ID already exists there, but a real per-create overhead
  worth someone eventually noting — not investigated further here).
- **Live, on a locally-running instance of the actual compiled `cmd/api` binary**,
  pointed at the same local Postgres, not a test harness: registered a real user,
  logged in for a real JWT, then `curl -X POST /api/v1/categories` and
  `/api/v1/transactions` twice each with the same client-generated `id` (simulating
  exactly what `SyncWorker` does after a timed-out-but-actually-succeeded push).
  Both endpoints: two 2xx responses, identical `created_at` in both responses
  (confirming the retry returned the original persisted row, not a fresh insert),
  and `SELECT count(*) ... WHERE id = '<client-id>'` against the DB directly
  returned exactly `1` in both cases. Test rows and the test user were deleted
  afterward; the local server process was stopped. This is the closest this session
  could get to Part 3's "on-device" ask without a real Android device/emulator
  (still unavailable, per Step 0's note) — it verifies the real backend mechanism
  the SyncWorker depends on, using the exact request shape Android sends, against
  the real compiled binary and the real DB engine, just not literally fired from
  the Android app.

**Contract-change discipline (additive, not breaking):** `git diff 52e3006 61802b2
-- internal/interfaces/http/dto/` shows the *only* change is one new optional field
(`ID *string`) added to each of the two create-request structs — no field renamed,
removed, or retyped, and neither response DTO
(`ChannelDetailsResponse`/`TransactionResponse`) changed at all. A client that omits
`id` gets byte-identical behavior to before this fix (nil → server mints a fresh
UUID, exactly as it always has). Confirmed the field name/location Android already
uses matches exactly: `id` (JSON key), `ApiModels.kt:195` (`TransactionRequest.id`)
and `:269` (`CreateChannelRequest.id`) — **Android needs no further change**, the
field it's already been sending is now finally read. Checked for other consumers
rather than assuming none exist: `~/Github/dlabs/pesa_mind_ios` is a real second
consumer of both endpoints (`APIEndpoints.swift:73,91` → `POST transactions`/
`POST categories`), confirmed via its `CreateTransactionRequest`/
`CreateChannelRequest` Swift structs (`TransactionModels.swift`,
`ChannelModels.swift`) — neither sends an `id` field today, so both get the
pre-existing behavior unchanged, unaffected by this fix. (A `CORS_ORIGINS` entry
for `https://pesamind.app` in `.env` hints at a web frontend too, but no
`pesa_mind_frontend`-type repo was found locally to check directly — noting this
rather than assuming there's no third consumer.)

**3. `ChannelDesc` enum validation — confirmed safe, free text, now locked in by a
test.** `ChannelDetails.ChannelDesc` (`category/model.go:16`) is
`string \`gorm:"null"\``, and `CreateChannelDetailsRequest.ChannelDesc` only carries
`binding:"required"` (non-empty, not a member of any enum). A `utils.ChannelDesc`
closed-string-enum type *does* exist (`utils/model.go:61-70`, six known
Uganda-market values) but nothing on the create path references it. No fix needed —
verified live too: `curl`-created a channel with `channel_desc: "XYZ Micro-Lender
Uganda Ltd SMS Alert"` (not one of the six enum values) against the locally-running
server and got `201`. SMS auto-creating a channel for an unrecognized sender name
will not 400 — A3 is not blocked by this.

**4. `DeletedAt` — confirmed plain `*time.Time` across every `BaseModel`-derived
domain, not fixed inline (correctly out of scope), full blast-radius assessed.**
`BaseModel.DeletedAt` (`utils/model.go:13`) is `*time.Time \`gorm:"index"\`` — not
`gorm.DeletedAt`. GORM's automatic soft-delete behavior (auto-`UPDATE deleted_at` on
`.Delete()`, auto-`WHERE deleted_at IS NULL` scoping on every query) only activates
for fields of type `gorm.DeletedAt` specifically; a plain `*time.Time` gets neither.
Every domain built on `BaseModel` shares this — confirmed by grep across
`transaction`, `category`, `user`, `automation`, `analytics`, `budget`. Two domains
(`notification`, `automation`) already know this and compensate with an explicit
`WHERE ... AND deleted_at IS NULL` in their own read queries; `transaction`/
`category`'s `FindByID`/`FindByUserID`/etc. do not filter on `deleted_at` at all —
harmless today only because a Channel/Transaction row with `deleted_at` set never
exists (see below), not because the queries are correct.
  - **Channel/Transaction deletes are real hard deletes.** `category/gorm_repository.go`
    and `transaction/gorm_repository.go`'s `Delete` both call bare
    `r.DB.Delete(&Model{}, "id = ?", id)`, which — because the field isn't
    `gorm.DeletedAt` — executes a real SQL `DELETE FROM`, permanently removing the
    row. Neither ever calls `BaseModel.SoftDelete()`.
  - **New finding this pass: `transactions.channel_details_id` has an
    `ON DELETE CASCADE` FK to `channel_details.id`** (confirmed via
    `docker exec pesa-mind-db-1 psql ... \d transactions`) — so today, hard-deleting
    a channel silently hard-deletes every transaction that references it too, via
    the DB, not application code. Worth knowing before anyone builds a "safer
    channel delete."
  - **`user` is the one domain with genuinely correct soft-delete — but only
    through one of its three delete-shaped methods.** `user.Service.Delete()`
    (`user/service.go:179-196`) is correct: it calls `u.SoftDelete()` in memory then
    `repo.Update(u)` → `r.DB.Save(user)`, a plain UPDATE that doesn't depend on
    GORM's automatic soft-delete hook at all. But `GormUserRepository` also has
    `Delete(id)` (hard delete) and a method literally named `SoftDelete(user)`
    (`user/gorm_repository.go:242-248`) whose own doc comment claims "GORM's Delete
    automatically performs soft delete if DeletedAt field exists" — **false**, per
    the same reasoning above; calling that method would hard-delete despite its
    name. Confirmed by grep that neither of these two `GormUserRepository` methods
    (nor `HardDelete`) is called from anywhere — dead code, not a live bug, but a
    real landmine for whoever reaches for it next, and living evidence the
    `*time.Time`-vs-`gorm.DeletedAt` distinction is already a source of confusion in
    this codebase, not just a theoretical risk.
  - **Net effect on this ADR's design:** does **not** block Slice A2's current
    full-list-diff pull — a row absent from `GET /categories`/`GET /transactions`
    looks the same to the client whether the server hard- or soft-deleted it, so
    "absent from pull → tombstone locally" still works exactly as shipped. It
    **does** block a future real delta/cursor endpoint (item 5): a delta response
    needs some way to report "this id was deleted since your last sync," and since
    deletes are unrecoverable today there's no data source to report deletions
    from. Whoever scopes that endpoint needs to resolve this first — either migrate
    `DeletedAt` to `gorm.DeletedAt` (a real migration: schema change +
    `Unscoped()` everywhere a tombstone must stay visible + a decision on the FK
    cascade above) or add a dedicated tombstone/audit table. **Not attempted here**,
    per this session's own instruction not to wing a `DeletedAt` migration inline.

**5. Delta/cursor pull support — confirmed does not exist, its own scoped backend
task.** `grep -rn "updated_since\|cursor\|server_time" internal/` (excluding tests)
returns zero hits anywhere in the backend. `GET /categories` and `GET /transactions`
(`cmd/api/main.go:89-99`) take no query parameters — confirmed at the
route-registration level, not just `ApiService.kt`. Per this session's instruction,
**Part 2 (delta pull) is not attempted** — building one client-side workaround, or
even scoping the endpoint itself, is out of bounds for this pass. Full-list-plus-diff
(as A2 shipped it) remains the only option until the backend adds: a new
`updated_at`-indexed query path per domain, an `updated_since` query param, and (per
finding #4) a real answer for how deletions get represented in that response.

### Pre-existing breakage found in the backend repo (all confirmed present on
`main`, none introduced by this fix — `git diff main -- <path>` empty for every
file below before it was touched)

`go build ./cmd/api/...` (the real server binary) and `go vet`/`go test` on every
package this fix touches are all clean, before and after. Elsewhere in the repo,
confirmed pre-existing and out of scope:
- `internal/domain/model/model.go:12` imports a `savingsgoal` package that doesn't
  exist in the module — breaks `go build ./...`/`go vet ./...` at the repo root.
  Nothing imports this package (`grep` confirms zero importers) — dead code.
- `internal/interfaces/http/routes.go` doesn't compile (`budget.Create`/`.List`/etc.
  undefined) and isn't imported by `cmd/api/main.go` — dead code, real routes are
  registered directly in `main.go`.
- `internal/domain/budget/budget_test.go:11` — `undefined: Budget`, pre-existing
  broken test, unrelated domain.
- `cmd/seeder/` — `main` redeclared across two files, won't build.
- `test/api_register_test.go`'s `TestAPIRegisterWithProfile` panics — its own
  in-memory sqlite table for `users` is missing a `provider` column the real
  repository now inserts. Unrelated domain (registration, not transaction/channel
  create), file untouched by this session.
- This session **fixed** (not just found) three test-compile breaks that were
  blocking `go test` on the packages this fix actually touches, since leaving them
  broken would have made "verify with a real test" impossible: `category_test.go`
  referenced `user.ChannelType`/`ChannelTypeCash` (the real type lives in `utils`,
  not `user`), `transaction_test.go`'s mock was missing `FindByUserIDAndType` (and
  its one test was calling `Service.Create(nil, nil, ...)`, which would nil-pointer
  panic independent of the mock, not just fail to compile — fixed to pass real
  `user`/`category` structs), `dto_test.go` asserted a `req.UserID` field that
  `CreateTransactionRequest` has never had (removed the incorrect assertion, added
  a new test that correctly covers the new `id` field's binding instead).

### Go-side gate (this repo has none today — set up ad hoc for this change,
same discipline as `ktlintCheck` on the Android side)

No `.golangci.yml`/linter config and no `golangci-lint` binary installed — a real,
noted gap, not silently skipped. Ran, before every commit on this branch:
`go build ./cmd/api/...` (the real binary — `go build ./...` at the repo root
always fails on the unrelated `savingsgoal` import above, so this is the
meaningful build target), `go vet` scoped to every package touched (clean),
`gofmt -l` scoped to every package touched (clean, no `-w` needed), and
`go test` scoped to every package touched (all green, including the two new
Postgres-integration tests). Two commits on `fix/idempotent-channel-transaction-create`:
`61802b2` (the fix itself) and `ecacecf` (the tests + pre-existing test-compile
fixes above). **Neither commit is pushed or deployed.**

## Airplane-mode acceptance test (target state for Channel/Transaction — steps 1-2 and 6-7 are exercisable now that A2 has landed; steps 3-4 need Slice A3 (SMS, profile). Step 9's "no duplicates" concern — the backend-blocker from Slice A2 above — is now backend-side closed: see "Backend-contract verification" for a live curl-based create-twice-same-id test against the real backend that found exactly one row both times. What's still not done is firing this test from the real Android app / a real device (none available this session) and the backend fix is still undeployed, so this remains unattempted **on-device** end-to-end)

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

## Duplicate channels + duplicate transactions: case-insensitive resolution + TID dedup

**Status: landed.** Two related bugs reported by the user: (1) SMS-triggered channel
auto-creation could create a duplicate channel for the same real sender ("case-sensitive
sender matching" was the initial hypothesis); (2) some providers (confirmed: Airtel
Uganda) send two separate SMS for one real transaction sharing a `TID`, which the
pre-existing dedup key didn't catch since the two message bodies differ. Fixed
together, atomically (one schema migration, one review pass), per the task's own
instruction not to split them.

### Phase 0 — measured before any change, via `data-path-tracer`

The channel-resolution bug was **worse than case-sensitive** — a genuine key mismatch,
not just a casing mismatch. `ChannelManager.isSmsAllowedForSender`'s lookup branch keyed
off `ChannelDescMobileMoney.AIRTELMONEY`/`.MTNMOBILEMONEY` ("Airtel Money"/"MTN Mobile
Money") for mobile money but `ChannelTypes.BANK` (a channel *type* string, "Bank" — not a
description at all) for **both** bank senders, while the auto-create branch
(`determineChannelTypeAndDesc`) created channels using yet a **third**, different
constant set (`MessageSender.AIRTEL_MONEY` = "airtelmoney", `MessageSender.STANBIC_BANK`
= "stanbicbank", etc.). Three different constant sources for what should have been one
canonical key meant the local Room lookup could **never** succeed for a channel this
same method had just auto-created — every incoming SMS silently fell through to a
direct `ApiClient.api.createChannel()` network call every time, forever, making the
"local-first" channel lookup a network-only path in practice that failed silently
offline (caught and logged, never surfaced to the user).

Transaction dedup was **not implemented at all**, contrary to what a first reading of
the schema suggests: `TransactionEntity.smsSourceKey` has carried a real unique index
since Slice A1, and `TransactionRepository.createTransaction` already had a working
no-op-on-match check against it — but nothing above that layer (`TransactionViewModel`,
`SMSMessageProcessor`) ever had a parameter to pass a real key through, so every call
site left it `null`, and SQLite allows unlimited `NULL`s in a unique index. The
protection existed in the schema and was completely inert in practice, exactly as
Slice A1's own commit message predicted ("no caller passes a real key yet ... currently
inert in practice").

**Real SMS sample data:** none found anywhere in this repo — checked `app/src/main/**`,
`app/src/test/**`, and every root/`docs/*.md` file. The only in-repo evidence of
Airtel's `TID` field's *existence* is the pre-existing amount-parsing regex
`SENT\.TID.*?UGX` in `SMSMessageProcessor.kt` — it confirms `TID` and `UGX` co-occur in
Airtel expense messages, but says nothing about the TID value's exact delimiter, length,
or character set (numeric-only vs alphanumeric). Zero MTN sample text of any kind exists
in this repo. **Duplicate counts:** not reachable — no device/emulator, no exported
production database, no bundled/fixture SMS data anywhere in this session's environment.
Reported here plainly rather than fabricated, per the task's own instruction.

### Phase 1 — case-insensitive channel resolution

- `ChannelManager.isSmsAllowedForSender`/`determineChannelTypeAndDesc` now derive
  `(channelType, channelDesc)` from **one shared function** for every known sender
  (MTN, Airtel, Stanbic, Centenary) — the lookup and the auto-create branch can no
  longer drift apart, closing the root cause directly, independent of casing.
- `ChannelEntity.normalizedSenderKey: String?` (new, nullable) — a trim+lowercase fold
  of `channelDesc`, unique-indexed, populated only for non-CASH (`channelType != "Cash"`)
  channels. CASH channels legitimately share `channelDesc = "Cash"` across many rows and
  must never be forced unique — this is why the column is a separate nullable field
  rather than a uniqueness constraint on `channelDesc` itself.
- **Uniqueness scope decision:** unique *across* soft-deletes, mirroring this ADR's own
  `smsSourceKey` precedent (dedup that must survive a tombstone) rather than its
  `serverId`/`(month, year)` precedent (uniqueness that's dropped because a table-wide
  constraint can't distinguish live from tombstoned rows). Justification: resurrecting
  a second live channel for a provider whose channel the user already deleted is
  exactly the duplicate-channel bug this column exists to prevent, so a tombstone match
  is deliberately treated as "leave it deleted," not "make a new one" — confirmed by
  both review passes as the correctly-applied precedent (not misapplied).
- **`isProviderChannelType` is derived internally** (`channelType != "Cash"`) rather than
  a caller-supplied flag — an earlier version of this fix took an explicit
  `isProviderChannel: Boolean` parameter on `createChannel`/`reconcileFromServer`, and
  `offline-sync-reviewer` caught (confidence 70) that both `SyncWorker`'s full-pull
  reconciliation (`SyncWorker.kt:501`) and `PrefsToRoomMigrator`'s one-time DataStore
  import simply never passed `true` — meaning **every already-synced or migrated
  provider channel kept `normalizedSenderKey = null` forever**, silently making the
  entire dedup fix inert for the population of users this task exists to help most:
  anyone with pre-existing data, not just fresh installs. Deriving the flag internally
  from `channelType` closes this for `SyncWorker`'s ongoing full-pull path automatically
  (no `SyncWorker.kt` change needed).
- **Deliberately NOT applied to `PrefsToRoomMigrator`'s one-time bulk import.**
  `ChannelDetails.toEntity` still hardcodes `normalizedSenderKey = null`. Reason: the
  migrator inserts via a single bulk `channelDao.upsertAll(...)` call
  (`OnConflictStrategy.REPLACE`), not the per-row `insertIgnore`-with-safe-fallback every
  other write path in this fix uses. If a migration batch already contains two real
  duplicate channels for the same provider — plausible, not hypothetical, given the
  confirmed severity of the pre-fix bug (see Phase 0) — populating this column there
  would make `REPLACE` **silently delete one of them mid-migration**, violating this
  task's own explicit constraint ("must not silently delete already-synced rows without
  a user-facing or logged confirmation step"). Left null; safely backfilling already-
  migrated rows (with real duplicate-collision/merge handling, and a user-facing or
  logged confirmation step) is Phase 3's job, not this pass's.
- **Tombstone-vs-live lookup split**, added after both `android-reviewer` and
  `offline-sync-reviewer` independently found the same bug (confidence 62 / 58): the
  first version of this fix's `findByNormalizedSenderKey` included soft-deleted rows
  (needed for insert-time conflict resolution) and `ChannelManager` used its result
  unconditionally as an active SMS destination — so a channel the user had deleted
  could silently receive a *new* transaction, which then hit `SyncWorker`'s channel-
  before-transaction push ordering and the backend's real hard-delete-with-
  `ON DELETE CASCADE` (see "Backend-contract verification" above), losing the
  transaction entirely. Fixed by splitting the DAO query in two:
  `findLiveByNormalizedSenderKey` (`deletedAt IS NULL`) for "does an active channel
  exist" decisions, and the original `findByNormalizedSenderKey` (tombstone-inclusive)
  kept for insert-time conflict resolution only. `ChannelManager.isSmsAllowedForSender`
  also re-checks liveness *after* the network-create-and-reconcile branch, since
  `reconcileFromServer`'s own conflict guard can still resolve to an existing tombstoned
  row instead of the just-created server channel.
- **`reconcileFromServer`'s normalizedSenderKey check moved into the `InsertNew` branch
  only**, not applied before the `serverId` lookup as an earlier version of this fix
  had it. Two reasons, found while implementing the fix above: (1) checking it first
  meant a row that already has this key set would find *itself* on every later pull and
  short-circuit before ever reaching `UpdateExisting`'s actual field refresh, freezing
  that row's name/description/status against real server-side edits forever; (2)
  backfilling the key onto an already-*existing* row inside `UpdateExisting` was also
  considered and rejected — `channelDao.update` has no `onConflict` strategy (Room
  defaults `@Update` to `ABORT`), so if two already-locally-known duplicate rows both
  got backfilled across separate pull cycles, the second `update()` would throw a
  `SQLiteConstraintException` instead of resolving gracefully. Only `InsertNew` uses the
  atomic `insertIgnore`-or-discard path actually built to handle that collision safely.
- `ChannelRepository.createChannel`/`reconcileFromServer` both switched from an
  unconditional insert to atomic check → `insertIgnore` → re-query-on-conflict, all
  inside one `database.withTransaction { }` — the real race-closing mechanism is the
  DB-level unique index plus the insert's own return value (`-1L` on conflict), not the
  pre-check, which is a fast-path optimization only.
- **`ChannelRepository.createChannel`'s return type changed** from a bare
  `ChannelDetails` to a new `ChannelCreateOutcome` (`Created`/`AlreadyExists`) sealed
  class. `android-reviewer` found (confidence 58) that the first version of this fix
  let a user manually create a *second* channel for an already-tracked bank/mobile-money
  provider silently dedupe against the existing one while `ChannelViewModel` still
  reported "Channel created successfully" and published a `ChannelCreated` event for a
  row that was never inserted — discarding the user's custom name/description with a
  false-success message. `ChannelViewModel.createChannel` now branches on the outcome
  and shows an accurate "A channel for this provider already exists: …" message instead.
- **Known, accepted gap — no "revive" path for a deleted-then-recreated provider.** If
  a user deletes a provider channel and a later create/reconcile attempt conflicts with
  that tombstone, the current code returns the dead row as-is (matching this ADR's
  existing `ReconcileResolver.SkipDirtyOrDeleted` precedent) rather than un-deleting it.
  A real "revive" (clear `deletedAt`, re-queue an outbox update) was considered and
  rejected for this pass: `OutboxCoalescer`'s own doc comment confirms a `DELETE` outbox
  row stays `DELETE` regardless of a later `UPDATE` attempt ("nothing in this repository
  layer re-mutates a soft-deleted row"), so a real fix needs a new coalescing rule,
  careful reasoning about whether the delete already reached the server, and is a real
  product question (should deleting a provider channel be permanent, or just a reset?)
  — not something to wing inline here. Flagged, not fixed.
- **Known, accepted gap — SMS auto-create's network channel-creation call still has no
  client idempotency id.** `offline-sync-reviewer` found (confidence 48, sub-threshold)
  that `ChannelManager.isSmsAllowedForSender`'s `CreateChannelRequest` never sets `id`,
  so two SMS from a brand-new provider processed concurrently can each independently
  miss the local lookup and both `POST` — the *local* race is still closed (one wins via
  `insertIgnore`), but the orphaned second server-side channel will reappear as a
  duplicate on a later full pull. The real fix is removing this network round-trip
  entirely (SMS auto-create rewired local-first) — that's still Slice A3's open
  remainder, not this pass's.

### Phase 2 — TID extraction + atomic transaction dedup

- `TransactionEntity.providerTransactionId: String?` (new, nullable), unique-indexed on
  the **composite** `(channelId, providerTransactionId)`, not globally. Justification
  (per the task's own instruction to default to the safer scope unless cross-provider
  uniqueness is confirmed): no real MTN sample exists anywhere to confirm TIDs are
  unique across every supported provider, so scoping by channel (one channel per real
  provider, thanks to Phase 1) is the conservative default.
- **Airtel TID extractor**: `Regex("\\bTID\\b[:\\s]+(\\w{4,})", RegexOption.IGNORE_CASE)`
  in `SMSMessageProcessor.extractAirtelTid`. Built from the only real evidence available
  (the pre-existing `SENT\.TID.*?UGX` amount regex, confirming `TID`/`UGX` co-occurrence
  but not the exact delimiter) — **explicitly not verified against a real Airtel
  sample**. Tightened (word boundaries around `TID`, a 4-character minimum on the
  captured value) after both review passes flagged the same risk: a false, constant
  extraction would **silently and permanently discard every subsequent genuinely
  different transaction** on that channel, a materially worse failure mode than simply
  failing to extract (which safely falls back to `smsSourceKey`-only dedup). Flagged for
  confirmation once real discarded-duplicate log lines are observed in production (see
  the logging below).
- **No MTN TID extractor** — per the task's explicit "confirm MTN's format... don't
  guess" instruction, and Phase 0 confirming zero real MTN samples exist in this repo
  (checked twice, independently, in this session). MTN messages fall back to the
  `smsSourceKey`-only path.
- **`smsSourceKey` fallback now actually computed** for every sender (previously always
  `null`, so the pre-existing unique index was permanently inert — see Phase 0):
  `"$normalizedSender:$timestamp:${content.trim().hashCode()}"` in
  `SMSMessageProcessor.processMessage`. Protects against exact redelivery/reprocessing
  (e.g. a service restart reprocessing a queued broadcast) even when no TID is
  extractable — it does **not**, and by design cannot, catch Airtel's two-different-
  bodies-for-one-transaction pattern, since its inputs differ between the two messages;
  that gap is exactly what `providerTransactionId` exists to close instead.
- `TransactionRepository.createTransaction` switched to the identical atomic pattern as
  Phase 1's channel writes: check (fast-path only) → `insertIgnore` → re-query-on-
  conflict, now returning a new `TransactionInsertOutcome` (`Inserted`/
  `DuplicateDiscarded`) instead of a bare `TransactionDetails`. This is the mechanism
  the task's own test spec required be provably atomic (two coroutines racing an insert
  for the same TID before either completes) — the real correctness guarantee is the
  insert's own return value, not a SELECT performed beforehand.
- Threaded through `TransactionViewModel` (`createTransactionAwaited`/
  `performCreateTransaction` gained `smsSourceKey`/`providerTransactionId` params;
  `TransactionCreationResult.Success` gained `wasDuplicate: Boolean = false`) and
  `SMSMessageProcessor` (computes both keys; on `wasDuplicate = true`, logs the TID/key
  plus **both raw message bodies** — this message's content and the already-recorded
  transaction's `note`, which for every MTN/Airtel parse branch is always the full raw
  SMS — instead of silently dropping the duplicate, per the task's own requirement).
  `StateEvent.TransactionCreated` is only published on a genuine `Inserted` outcome, not
  a discarded duplicate.

### Tests (`sync-test-author`)

`ChannelSenderKeyDedupTest.kt` and `TransactionProviderIdDedupTest.kt`
(`app/src/test/java/cc/dlabs/pesamind/core/data/`), 8 tests total, backed by a **real
in-memory Room database** via Robolectric (`org.robolectric:robolectric` +
`androidx.test:core` added as new `testImplementation`-only dependencies — no
production dependency changed) rather than mocks, since the whole point of several of
these tests is proving genuine SQLite unique-constraint behavior under real concurrent
transactions, which a mocked DAO would trivially "pass" without exercising. Covers all
four required scenarios: case-variant channel resolution converging on one row (both
orderings, plus a concurrent-creates variant); two SMS bodies sharing one TID yielding
exactly one transaction row, asserted via the real `TransactionInsertOutcome`/raw
`insertIgnore` return value, not a count query; a no-TID message still deduping via the
`smsSourceKey` fallback; and two coroutines racing an insert for the same TID on
`Dispatchers.IO` before either completes, asserting exactly one row survives — the test
that actually proves the race is closed, not just that sequential dedup works.

### Gates run

1. `ktlintFormat` → `ktlintCheck` → `test` → `assembleDebug`, all green — run after the
   initial implementation, after `sync-test-author`'s test suite landed, and again after
   every review-driven fix below (final run: 113 tasks, all green).
2. `offline-sync-reviewer` on the diff — found 3 real findings (the migrated-channel
   `normalizedSenderKey` gap above, confidence 70; the tombstone-lookup misattachment
   gap above, confidence 58; the unverified/unbounded Airtel TID regex, confidence 55;
   plus a confidence-48 sub-threshold idempotency gap folded into the accepted-gaps
   list above). All addressed as described in Phase 1/2 above, or explicitly
   accepted-and-documented where a full fix required out-of-scope work (a new
   `OutboxCoalescer` rule, or removing the SMS network-create round-trip entirely).
3. `android-reviewer` on the diff (touches `TransactionViewModel.kt`/
   `ChannelViewModel.kt`) — found 2 real findings (the same tombstone-lookup gap,
   confidence 62, independently; the false-success manual-channel-dedup message,
   confidence 58) plus confirmed the same unverified-TID-regex risk (confidence 55).
   Also explicitly cleared: no UiState/StateFlow violation from `wasDuplicate`, no
   Composable/color-literal changes (none touched), `AddTransactionScreen`'s manual
   path behaves identically to before this diff, and the standing `runTest`-vs-
   `runBlocking` testing rule was followed correctly by the new tests.
4. No Composable/screen file changed in this diff (confirmed via `git diff --stat`) —
   there is no new UI to click through; the closest thing to an end-to-end check is the
   Robolectric-backed real-Room-database test suite above. Not claiming a UI
   verification that wasn't done, per this repo's own instruction to say so explicitly
   when a budget/behavior can't be measured directly.

### Phase 3 (historical duplicate cleanup): confirmed not started, scoped separately

Explicitly out of scope for this pass, per the task's own constraint. Concretely still
open, now with more precision than before this pass:
- **Already-migrated channels** (`PrefsToRoomMigrator`, pre-Room DataStore-blob origin)
  keep `normalizedSenderKey = null` forever until a real Phase 3 backfill runs — see
  "Deliberately NOT applied to `PrefsToRoomMigrator`'s one-time bulk import" above for
  why this pass couldn't safely do it inline (bulk `REPLACE` risk). A real Phase 3 needs
  to: detect existing duplicate channels sharing a normalized `channelDesc`, decide a
  merge/keep-one policy (which row survives; do existing transactions' `channelId`s get
  re-pointed), and get an explicit user-facing or logged confirmation before deleting
  anything — not a silent bulk operation.
- **Already-persisted duplicate transactions** (two rows for one real Airtel
  transaction, created before this fix shipped) are completely untouched by this pass —
  no code here reads, merges, or dedupes already-persisted transaction rows.
- Field-enrichment-on-duplicate (merging balance/fee from a second matching SMS into the
  first-recorded transaction) remains unimplemented, exactly as scoped — flagged as a
  possible follow-up once the schema/dedup mechanism has real production data behind it,
  not attempted here.
