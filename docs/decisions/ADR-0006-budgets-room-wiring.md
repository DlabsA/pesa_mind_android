# ADR-0006: Budgets — wire the already-complete Room schema into the live path (A9 / Slice B)

**Status:** Proposed — design plan only, no code written yet.
**Date:** 2026-07-26

## Scope recap

Wire `MonthlyBudgetEntity`/`YearlyBudgetEntity` (already schema-complete at Room DB version
2) into the live write/read/sync path, replacing `BudgetViewModel`/`SetMonthlyBudgetViewModel`/
`YealyBudgetViewModel`'s direct `ApiClient.api` + dead-`BudgetManager` usage with a
Room-backed repository, following the `ChannelRepository`/`TransactionRepository` +
`SyncWorker` pattern from ADR-0004 Slice A. No Room migration is required — this plan reuses
the existing `transactionsJson` String column with a richer serialized shape, not a new
column (see Decision 3).

This is the work ADR-0004 already named "Slice B" but never scoped in detail — this ADR is
that scoping. See `docs/vault/05-debt-burndown.md` item A9 for the bug-finding context this
plan resolves (`BudgetManager.init()` never called from `PesaMindApp.onCreate()`, so its
entire cache layer is a silent no-op today — zero working offline fallback for budgets).

## Preconditions — checked before this plan is handed off, both satisfied

1. **`StateEvent.SyncCompleted` (A9's Phase 3 assumes this exists from A10) — confirmed
   present.** `StateEvent.kt:83` declares it; `SyncWorker.kt:72` publishes it
   unconditionally after every `doWork()` run; `DashboardViewModel`/`AnalyticsViewModel`
   both already subscribe (`DashboardViewModel.kt:123`, `AnalyticsViewModel.kt:85`). A10 is
   fully landed, not half-done — this plan's assumption that budgets "ride that existing
   signal for free" is safe to build on.
2. **Worktree conflict risk on `BudgetViewModel.onStateEvent` — checked, not just flagged.**
   `.claude/worktrees/agent-a70b7b435158d47ce` (branch `fix/streak-cache-staleness`, commit
   `9abe62a`) adds the **first** `override fun onStateEvent` to `BudgetViewModel.kt` (for
   `TransactionCreated`/`Channel*` → clear+refetch the streak cache; `UserLoggedOut` → reset
   streak state). Confirmed via `git merge-base --is-ancestor`: **this branch is not merged
   into `chore/architecture-audit` or `main` as of this writing.** `BudgetViewModel.kt` on
   the branch this plan builds on today has **no** `onStateEvent` override yet.
   - **If `fix/streak-cache-staleness` is still unmerged when this plan is executed:** add
     the first `override fun onStateEvent` to `BudgetViewModel.kt`, with just the
     `SyncCompleted` case (Phase 4). When `fix/streak-cache-staleness` merges later, its diff
     will conflict on this exact override — resolve by combining both `when` blocks into
     one, not by picking one side.
   - **If `fix/streak-cache-staleness` has since merged:** `BudgetViewModel.kt` already has
     an `override fun onStateEvent` with a `when` block handling `TransactionCreated`/
     `Channel*`/`UserLoggedOut`. Add a `SyncCompleted ->` case **into that existing `when`
     block** — Kotlin won't compile a second `override fun onStateEvent` in the same class.
   - Whoever executes this plan: check `git log --oneline chore/architecture-audit` for
     `9abe62a` (or `git merge-base --is-ancestor 9abe62a HEAD`) before touching
     `BudgetViewModel.kt`, and follow whichever branch of the above actually applies.

## Design decisions (resolved)

### Decision 1 — Repository shape: two objects, not one

Two singleton objects, `MonthlyBudgetRepository` and `YearlyBudgetRepository` (new files
under `core/data/`), each wrapping exactly one DAO — mirroring `ChannelRepository`/
`TransactionRepository`'s one-object-per-table convention exactly, not merging into a single
`BudgetRepository`.

Justification: `TransactionRepository` already reaches into `ChannelDao` read-only for FK
resolution (`SyncWorker.pullTransactions()` calls `database.channelDao().getAllActive()`
directly) — that's the established precedent for a narrow, read-only cross-repository
dependency without merging tables into one object. `MonthlyBudgetRepository` follows the
same shape: it depends on `YearlyBudgetRepository` for exactly one operation — "resolve (or
create) the local yearly-budget row for year Y" — needed when a monthly budget is created
before its yearly parent exists locally. Two objects keep each repository's
`withTransaction` scope focused on its own table (matches the existing DAO-per-repository
boundary) while the one legitimate cross-cutting need is a named, narrow call, not a merged
object.

Each exposes (domain type = the existing `MonthlyBudgetResponse`/`YearlyBudgetResponse`
network DTOs, exactly like `ChannelRepository` reuses `ChannelDetails` as both the Retrofit
response type and its own return type — no new domain-DTO layer needed):

- `observeMonthlyBudget(month, year): Flow<MonthlyBudgetResponse?>`,
  `observeYearlyBudget(year): Flow<YearlyBudgetResponse?>`
- `getMonthlyBudget(month, year): MonthlyBudgetResponse?`,
  `getYearlyBudget(year): YearlyBudgetResponse?` (one-shot)
- `addMonthlyLineItem(month, year, name, amount, type): MonthlyBudgetResponse`,
  `deleteMonthlyLineItem(monthlyBudgetLocalId, lineItemLocalId)` (and Yearly equivalents) —
  collapse today's `SetMonthlyBudgetViewModel`/`YealyBudgetViewModel` "does a local budget
  exist yet → CREATE vs PATCH" branch into the repository (see Decision 3), so the
  ViewModel no longer needs to know CREATE vs UPDATE.
- `reconcileMonthlyFromServer(details: MonthlyBudgetResponse, resolvedYearlyLocalId: String?)`,
  `reconcileYearlyFromServer(details: YearlyBudgetResponse)` — pull-side, delegate to
  `ReconcileResolver` exactly like `ChannelRepository.reconcileFromServer`.
- `internal lateinit var database: PesaMindDatabase` + `fun init(context)` via
  `DatabaseEntryPoint`, same as Channel/Transaction (`internal` visibility for the
  Robolectric in-memory-Room test seam).

### Decision 2 — SyncWorker ordering

**Push:** yearly before monthly, for the identical reason channels push before transactions
(`SyncWorker.kt:84-90`) — `MonthlyBudgetEntity.yearlyBudgetId` is a local FK that must be
translated to the yearly row's `serverId` at push time, which only exists once the yearly
row's own outbox entry has drained. Extend `pushOutbox()`'s existing
`channelsClean && transactionsClean` chain to
`channelsClean && transactionsClean && yearlyBudgetsClean && monthlyBudgetsClean`, adding
`pushYearlyBudgetOutbox()` → `pushMonthlyBudgetOutbox()` after the existing two calls —
budgets have no cross-dependency on channels/transactions, so their position relative to
that pair doesn't matter, only yearly-before-monthly within the budget family does.

**Pull:** same ordering, same reason in reverse — resolving `MonthlyBudgetResponse
.yearlyBudgetId` (a server id string) into the local FK requires the yearly row (and its
`serverId`) to already exist locally, exactly mirroring `pullTransactions()`'s existing
`channelIdByUniqueName` map built from the already-pulled channel table. Append
`pullYearlyBudgets()` → `pullMonthlyBudgets()` inside `pullChanges()`'s existing try/catch
after `pullChannels()`/`pullTransactions()`.

Both push and pull phases need their own
`justSyncedMonthlyBudgetServerIds`/`justSyncedYearlyBudgetServerIds` sets (mirroring the two
existing `justSynced*` sets) and their own `applyServerSideDeletions`-equivalent pass per
entity.

### Decision 3 — Line-item `transaction_ops` handling (the hard part)

No new column, no diff against a separate "last-synced baseline" column. Instead,
`transactionsJson` changes its serialized shape: instead of directly storing
`List<BudgetTransactionResponse>` (id/name/amount/type/createdAt, as it does today via
`PrefsToRoomMigrator`'s `Gson().toJson(transactions)`), the repository stores
`List<LocalBudgetLineItem>`, a new local-only shape with two extra fields:

```kotlin
LocalBudgetLineItem(
  id: String,                     // stable local key, client-generated UUID; never changes
  serverId: String? = null,       // null until this line item's "add" is server-confirmed
  name: String,
  amount: Double,
  type: String,
  pendingAction: String? = null,  // null (clean) | "add" | "update" | "delete"
)
```

This is a drop-in-compatible superset of the JSON `PrefsToRoomMigrator` already writes:
Gson deserializes the missing `serverId`/`pendingAction` fields as `null` (their JVM
default for a nullable type, which happens to equal the semantically-correct
"clean/synced" value) and ignores the extra `created_at` field already present in migrated
JSON. No entity/schema change, no migration bump — confirm this compatibility explicitly
with a test (see Decision 8, Test plan).

**Why not diff-against-a-baseline-column:** a second `syncedTransactionsJson` snapshot
column would need its own migration and its own dirty-tracking, doubling the surface this
bug already exists on. Storing pending-op state inline per item means the outbox row
(which — per `OutboxEntry`'s own doc comment — carries no payload and is reconstructed from
"the entity's current state" at drain time) still needs zero payload: the repository reads
`transactionsJson`, filters `pendingAction != null`, and that filtered list is the
`transactionOps` batch. This preserves the existing "outbox entry has no payload" invariant
instead of breaking it for budgets alone.

**Mutation semantics** (repository-internal, applied inside `database.withTransaction`,
same as `ChannelRepository.updateChannel`):
- **Add:** append a new `LocalBudgetLineItem(id = UUID, serverId = null, pendingAction =
  "add", ...)`.
- **Edit an item whose `serverId == null`** (still an unconfirmed add): mutate its fields
  in place, leave `pendingAction = "add"` — the server has never seen it, so it's still one
  "add," not "add-then-update."
- **Edit an item whose `serverId != null`** (already synced once): mutate fields, set
  `pendingAction = "update"`.
- **Delete an item whose `serverId == null`:** remove it from the list outright, no op
  needed (mirrors `OutboxCoalescer.HardDeleteNoOutbox` at line-item granularity — the server
  never knew it existed).
- **Delete an item whose `serverId != null`:** set `pendingAction = "delete"`, keep the row
  (so a push can still read its `serverId`); the UI-facing mapping (below) hides it
  immediately.
- Every mutation recomputes and stores `totalExpenditures`/`totalIncome`/`totalSavings`/
  `totalTransactions` on the parent entity from the currently-visible (non-pending-delete)
  items, sets `dirty = true`/`syncStatus = PENDING`, bumps `updatedAt`, and calls the
  existing per-budget `enqueueOutbox(entityType = MONTHLY_BUDGET or YEARLY_BUDGET, entityId
  = <budget's local id>, UPDATE)` — unchanged `OutboxCoalescer` usage, since there is one
  outbox row per budget, not per line item; a CREATE-not-yet-synced budget correctly stays
  coalesced to CREATE (full-list body), an already-synced one coalesces to UPDATE (ops
  body).

**UI-facing read mapping** (`entity.toDetails(): MonthlyBudgetResponse/YearlyBudgetResponse`):
filter out `pendingAction == "delete"` items (optimistic-remove), map the rest to
`BudgetTransactionResponse` — the UI never needs to know about `pendingAction`/local
id/`serverId`, exactly like `ChannelEntity.toDetails()` hides `syncStatus`/`dirty` plumbing
from `ChannelDetails`.

**Push-time body construction** (`pushMonthlyBudgetOutbox`/`pushYearlyBudgetOutbox` in
`SyncWorker`):
- **CREATE op:** `body = CreateMonthlyBudgetRequest(yearlyBudgetId = <yearly row's
  serverId>, month, year, transactions = <all items, since every item on a not-yet-synced
  budget must be pendingAction="add">)`.
- **UPDATE op:** `body = UpdateMonthlyBudgetRequest(transactionOps = <items with
  pendingAction != null, mapped to BudgetTransactionOperation(id = serverId.orEmpty(), name,
  amount, type, action = pendingAction)>)`.
- Capture, alongside the existing `dispatchedUpdatedAt`, a `dispatchedLocalIds: Set<String>`
  snapshot of exactly which local ids were included in this push's ops.

**Push completion merge** (used identically by `finishMonthlyBudgetPush`/
`finishYearlyBudgetPush`, for both `ClearAndSync` and `RequeueDirty` — same merge logic
both times, only the outer dirty/outbox handling differs, exactly like
`PushCompletionResolver` already separates those concerns):
1. Re-read the entity's current line-item list (latest, post-dispatch — may already
   include a mid-flight edit).
2. For each item in `response.body()!!.transactions`: find the existing local item whose
   `serverId` already equals this entry's id (preserves local id/Compose key across pushes
   for already-synced items); if none, this server id is new — match it against the still-
   unmatched `dispatchedLocalIds` whose action was "add" by `(name, amount, type)` equality
   (best-effort; flagged accepted gap below), first-unclaimed-match-wins;
   `buildLocalBudgetLineItem(id = <preserved or newly-minted>, serverId = entry.id,
   pendingAction = null, ...)`.
3. Append any item from `latest` whose id is **not** in `dispatchedLocalIds` and whose
   `pendingAction != null` — i.e. edits that raced this exact push (this is what makes
   `RequeueDirty` safe: those items are never silently dropped).
4. Recompute totals: use `response.body()`'s totals directly, plus the contribution of any
   leftover (step-3) pending non-delete items on top.
5. **ClearAndSync:** write the merged list, `dirty = false`, `SYNCED`, delete the outbox
   row. **RequeueDirty:** write the merged list, keep `dirty = true`/`PENDING`, requeue the
   outbox row as UPDATE (never CREATE — the budget now has a `serverId` regardless of which
   op just ran), `attempts = 0`.

**Accepted gap, name it explicitly** (do not silently fix or hide it): the `(name, amount,
type)` best-effort match in step 2 can theoretically mis-assign a local id to the wrong
server id if two different line items dispatched as "add" in the same push share an
identical name+amount+type tuple. This is cosmetically wrong (a Compose recomposition key
swap) but not a correctness bug — both items still end up present with correct
amounts/types. Same risk class already accepted elsewhere in this codebase
(`resolveUniqueChannelIdsByName`, `channelDetailsName` matching in `TransactionRepository`).

### Decision 4 — `isFromCache`/`lastUpdated`

Drop `isFromCache` as a fetched-vs-cached toggle — with a real Room-backed Flow, every read
is the cache; there is no separate "we fell back to cache" branch anymore (today's field
exists only because `BudgetManager` was a dead fallback that never actually engaged).
Repurpose `BudgetUiState.isFromCache` to mean "this row hasn't been confirmed by the server
yet" — i.e. `currentMonthlyBudget?.let { MonthlyBudgetRepository.getSyncStatus(it) !=
SyncStatus.SYNCED }` (or simplest: derive it from whether the entity backing this response
is dirty), which is a genuinely useful, now-truthful signal ("you're looking at an unsynced
edit") instead of the previously-always-either-true-or-meaningless flag. `lastUpdated` maps
directly to the entity's `updatedAt` (already a `Long` epoch-millis, no conversion needed)
instead of `BudgetManager`'s DataStore `MONTHLY_SYNC`/`YEARLY_SYNC` timestamps (which were
never written, since `BudgetManager` was never initialized). Both fields are populated by
the `observeX().collect { ... }` closure that becomes `BudgetViewModel`'s sole writer of
`currentMonthlyBudget`/`yearlyBudget` (see Decision 5/Phase 4) — no separate cache-check
call needed, since `dirty`/`updatedAt` ride along on every entity emitted by the Flow
already (exposed via a small addition to the mapped return type, or by having the
repository's observe methods return a lightweight wrapper carrying `(response, dirty,
updatedAt)` alongside `MonthlyBudgetResponse` — pick one at implementation time; either is
fine, note it as an open micro-decision, not a blocker).

### Decision 5 — Hilt inconsistency: leave it, explicitly out of scope

`BudgetViewModel` stays `@HiltViewModel`; `SetMonthlyBudgetViewModel`/`YealyBudgetViewModel`
stay plain-constructor `viewModel()`. `.claude/CLAUDE.md`'s architecture section only
mandates every *new* ViewModel extend `UnifiedViewModel` (already true for all three) — it
does not mandate Hilt for existing ones, and `ChannelRepository`/`TransactionRepository`
were deliberately built to work with a plain-constructor `viewModel()` VM
(`ChannelRepository`'s doc comment explicitly calls out "converting every call site to
Hilt-injected ViewModels is a wider change than this slice's scope" — the identical
reasoning applies here). Changing DI wiring is an orthogonal refactor with its own blast
radius (navigation call sites, `hiltViewModel()` vs `viewModel()` factory swaps in three
screens) and no A9-specific reason to bundle it. This plan makes no DI changes to
`SetMonthlyBudgetViewModel`/`YealyBudgetViewModel`.

### Decision 6 — A12's `BudgetScreen.kt:109` bypass: defer to A12, do not bundle

`BudgetScreen.kt:109`'s direct `AccountManager.getAccount()` call inside `LaunchedEffect` is
unrelated to the Room-wiring bug (it's a display-only account-blank-warning snackbar,
reading a manager that already is correctly initialized — unlike `BudgetManager`) and
touches a different violation of the "no Composable calls a manager directly" rule for a
different, already-tracked reason (`docs/vault/05-debt-burndown.md` item A12). Fixing it
here would silently expand A9's diff into A12's territory and make A12 harder to review
standalone once A9 lands. Leave it exactly as-is; A12 fixes it separately.

### Decision 7 — `PrefsToRoomMigrator`: no functional change required, but must be verified for wire compatibility

`PrefsToRoomMigrator`'s `MonthlyBudgetResponse.toEntity()`/`YearlyBudgetResponse.toEntity()`
(lines 233-269) keep writing `transactionsJson = Gson().toJson(transactions)` from a
`List<BudgetTransactionResponse>` — unchanged. Per Decision 3, this JSON happens to
deserialize correctly into `List<LocalBudgetLineItem>` (missing `serverId`/`pendingAction` →
`null`, which is exactly "clean"/"synced," correct for a migrated-and-therefore-already-
synced row). Add one explicit test (Decision 8, Phase 5) asserting a
`PrefsToRoomMigrator`-shaped JSON blob round-trips through the new `LocalBudgetLineItem`
deserializer with `pendingAction == null` for every item — do not just assume the
Gson-defaults behavior; it's a real but non-obvious compatibility point worth pinning down
in a test rather than trusting prose.

### Decision 8 — Test plan

Mirror the exact convention already established for Channel/Transaction:

**Pure-JUnit, no Room** (`app/src/test/java/cc/dlabs/pesamind/core/database/`):
- `LocalBudgetLineItemMergeTest.kt` — new pure-JUnit test of the Decision-3 merge function
  once it's extracted as a standalone pure function (recommend extracting it the same way
  `PushCompletionResolver`/`ReconcileResolver` were extracted from inline `withTransaction`
  blocks — this is exactly the kind of previously-inline, hard-to-test policy those two were
  split out for). Covers: no-mid-flight-edit full replace, mid-flight-edit-preserved
  (`RequeueDirty` case), add-then-immediate-delete-before-sync (no op emitted), delete-of-
  never-synced-item (no op emitted), name/amount/type ambiguous-match gap (document the
  accepted behavior, don't just skip it).
- Existing `OutboxCoalescerTest.kt`/`PushCompletionResolverTest.kt`/`ReconcileResolverTest.kt`
  need no changes — both resolvers are already entity-agnostic (operate on
  `OutboxOperation`/`SyncStatus`/`ExistingRowSnapshot`, no Channel/Transaction-specific
  type), confirming they're reusable as-is for budgets. Worth a one-line comment in the PR
  description noting this, not new test files.

**Robolectric + in-memory Room** (`app/src/test/java/cc/dlabs/pesamind/core/data/`, pattern
from `ChannelSenderKeyDedupTest.kt`/`TransactionProviderIdDedupTest.kt`:
`@RunWith(RobolectricTestRunner::class)`, `Room.inMemoryDatabaseBuilder(...)
.allowMainThreadQueries().build()`, swap into the repository's internal `lateinit var
database` in `@Before`, `db.close()` in `@After`, `runBlocking` — **never `runTest`**, per
`.claude/CLAUDE.md`'s Testing section landmine):
- `MonthlyBudgetRepositoryTest.kt` — create-with-first-line-item (no prior yearly budget →
  auto-resolves/creates one via `YearlyBudgetRepository`), add/update/delete line-item
  transitions through the `pendingAction` states above, outbox coalescing (add-then-delete-
  before-sync leaves no outbox row), FK to a yearly budget's local id.
- `YearlyBudgetRepositoryTest.kt` — same shape, one table.
- `PrefsToRoomMigratorBudgetCompatibilityTest.kt` (or extend the existing
  `PrefsToRoomMigratorTest.kt`) — Decision 7's wire-compatibility assertion.

**ViewModel tests:** extend/replace the existing pattern seen in
`BudgetViewModelStreakCacheTest.kt` (currently only on the unmerged
`fix/streak-cache-staleness` branch — see Preconditions above) — plain JUnit +
`runBlocking`, no `runTest`, `NetworkMonitor` mocked via `Mockito.mock` purely to satisfy
the constructor since its collector never actually runs (same "queued-coroutine-never-
executes" reasoning already documented there). Add coverage for `BudgetViewModel`'s new
`observeMonthlyBudget()`/`observeYearlyBudget()` collection replacing the old
`fetchMonthlyBudget`/`fetchYearlyBudget` network calls, and for the new `onStateEvent`
handling of `SyncCompleted` — see Preconditions above for exactly how to combine this with
(or add ahead of) the streak-cache branch's own `onStateEvent` addition.

### Decision 9 — Sequencing and BudgetManager/ApiClient fallback: keep, don't delete

Order: **repository → SyncWorker → ViewModels → screens → (BudgetManager left alone).**

`BudgetManager` deletion is explicitly ADR-0004 Slice C ("dead-code removal"), gated on
Slice B landing first — this pass is Slice B. Deleting `BudgetManager` now would be
premature: it's still the thing `PrefsToRoomMigrator` reads from on a fresh, not-yet-
migrated device (`PrefsToRoomMigrator.kt:88,92-93` — `BudgetManager.init()`/
`.getMonthlyBudgets()`/`.getYearlyBudgets()`), and that one-time-import code path must keep
working until every device has run the migration at least once. **Do not delete
`BudgetManager` or its DataStore keys in this pass.** Do also **not** re-wire
`BudgetManager.init()` into `PesaMindApp.onCreate()` "to fix the bug directly" — that would
resurrect the dead DataStore cache as a second, now-actually-live source of truth alongside
Room, which is the opposite of this fix's goal. The bug ("`BudgetManager.init()` never
called") is fixed by removing all its call sites from the live app paths (ViewModels), not
by adding the missing `init()` call — `PrefsToRoomMigrator`'s existing defensive
`BudgetManager.init(appContext)` call (line 88, already flagged in its own comment as
covering this exact bug) remains the only production caller, and that's correct and
sufficient.

## Phased implementation plan

### Phase 1 — Local line-item type + entity mapping (foundation, no wiring yet)

- New file: `app/src/main/java/cc/dlabs/pesamind/core/data/BudgetLineItem.kt` (or co-locate
  in the repository files) — `LocalBudgetLineItem` data class (Decision 3), Gson
  (de)serialization helpers, the pure merge function (`mergeAfterPush(...)`) extracted so
  it's independently unit-testable.
- Add `MonthlyBudgetEntity.toDetails(): MonthlyBudgetResponse` /
  `YearlyBudgetEntity.toDetails(): YearlyBudgetResponse` extension functions (mirrors
  `ChannelEntity.toDetails()` in `ChannelRepository.kt`) — filters `pendingAction ==
  "delete"`, maps `LocalBudgetLineItem` → `BudgetTransactionResponse`.
- No behavior change to any live path yet — this phase is purely additive types + pure
  functions, safe to land and test in isolation.

### Phase 2 — YearlyBudgetRepository and MonthlyBudgetRepository

- New files: `core/data/YearlyBudgetRepository.kt`, `core/data/MonthlyBudgetRepository.kt`.
- Structure each exactly like `ChannelRepository.kt`: `internal lateinit var database`,
  `fun init(context)`, `observeX`/`getX` reads, `addXLineItem`/`deleteXLineItem` writes
  (Decision 3's mutation semantics + `enqueueOutbox` reuse), `reconcileXFromServer`
  (Decision 1/`ReconcileResolver`).
- `MonthlyBudgetRepository.resolveOrCreateYearlyBudgetLocalId(year)`: the one narrow
  cross-repository call into `YearlyBudgetRepository`.
- Wire both into `PesaMindApp.onCreate()` (`MainActivity.kt`) alongside the existing
  `ChannelRepository.init(this)`/`TransactionRepository.init(this)` lines — this is the
  actual one-line fix for the bug title ("Room schema inert"): the schema stops being inert
  the moment something reads/writes through it in production, which happens here, not by
  touching `BudgetManager`.

### Phase 3 — SyncWorker push + pull

- Extend `pushOutbox()`: add `pushYearlyBudgetOutbox()` → `pushMonthlyBudgetOutbox()`
  (Decision 2 ordering), each structured like `pushChannelOutbox`/`pushChannelEntry` —
  `reclaimStaleSyncingRows`, per-PENDING-row `pushYearlyBudgetEntry`/`pushMonthlyBudgetEntry`
  branching on CREATE/UPDATE (no DELETE outbox op needed unless whole-budget deletion is
  exposed anywhere in the UI today — confirm; if not, treat DELETE as defensive-only like
  `pushTransactionEntry` treats non-CREATE ops).
- Extend `pullChanges()`: add `pullYearlyBudgets()` → `pullMonthlyBudgets()` (full-list
  fetch via `api.getYearlyBudgets()`/`api.getMonthlyBudgets()` — both zero-query-param
  full-list endpoints, confirmed in `ApiService.kt:140,175`, so this is a true full-list
  pull exactly like Channels/Transactions, no delta cursor to fabricate), each calling
  `reconcileXFromServer` per row + an `applyServerSideDeletions`-equivalent using the new
  `justSynced*BudgetServerIds` sets.
- Publish nothing new — `StateEvent.SyncCompleted` (confirmed present per Preconditions
  above) already fires unconditionally after every `doWork()` run; budgets ride that
  existing signal for free.

### Phase 4 — ViewModel rewiring

- `BudgetViewModel.kt`: replace `fetchYearlyBudget`/`fetchMonthlyBudget`/`loadFromCache`/all
  `BudgetManager`/`api.get*Budget*` calls with two
  `viewModelScope.launch { YearlyBudgetRepository.observeYearlyBudget(year).collect { ... } }`
  / `MonthlyBudgetRepository.observeMonthlyBudget(month, year).collect { ... }` subscriptions
  started from `init` (mirrors `ChannelViewModel`'s `observeX().collect` as sole state
  writer — no more manual `_state.update` splicing after a network call). Leave
  `fetchStreak()`/`api.getDashboard()` entirely untouched (explicitly out of scope). Add
  `onStateEvent` handling for `StateEvent.SyncCompleted` — **follow the Preconditions
  section above exactly** for whether this is the first `onStateEvent` override or an
  addition to the streak-cache branch's existing one; this is a real merge-order decision,
  not something to resolve silently either way.
- `SetMonthlyBudgetViewModel.kt`/`YealyBudgetViewModel.kt`: replace `BudgetManager`/`api.*`
  calls with `MonthlyBudgetRepository.observeMonthlyBudget`/`.addMonthlyLineItem`/
  `.deleteMonthlyLineItem` (and Yearly equivalents) — the CREATE-vs-UPDATE branch (`if
  (s.budget == null) createBudgetWithTransaction(...) else patchBudgetAddTransaction(...)`)
  collapses into a single `addMonthlyLineItem` call per Decision 3, since the repository now
  owns that branch.
- No Hilt changes (Decision 5), no `BudgetScreen.kt:109` change (Decision 6).

### Phase 5 — Tests (Decision 8, can start as soon as Phase 1 lands and proceed alongside Phases 2-4)

1. `LocalBudgetLineItemMergeTest.kt`
2. `MonthlyBudgetRepositoryTest.kt`, `YearlyBudgetRepositoryTest.kt`
3. `PrefsToRoomMigratorBudgetCompatibilityTest.kt` (Decision 7)
4. `BudgetViewModelSyncCompletedTest.kt` (or extend `BudgetViewModelStreakCacheTest.kt`
   once its merge status is resolved per Preconditions)

### Phase 6 — Cleanup (do not delete BudgetManager, Decision 9)

- Confirm no remaining production call site imports `BudgetManager` except
  `PrefsToRoomMigrator.kt`.
- Leave `Setmonthlybudgetscreen.kt`'s filename violation unfixed — out of scope for A9,
  a separate, already-tracked (`docs/vault/05-debt-burndown.md`, item A5) future cleanup,
  not bundled into this diff.

## Ambiguities still open (not resolved unilaterally, name them to whoever executes this)

1. **Line-item merge ambiguity gap** (Decision 3's `(name, amount, type)` best-effort
   match): accepted as a cosmetic-only risk in this plan; confirm that's acceptable rather
   than requiring the server to echo back a client-supplied line-item id on "add" (which
   would need a backend contract change out of this repo's control).
2. **`isFromCache` return-shape micro-decision** (Decision 4): whether
   `observeMonthlyBudget()`/`observeYearlyBudget()` return a wrapper type carrying
   `dirty`/`updatedAt` alongside `MonthlyBudgetResponse`, or the repository exposes a
   separate `getSyncMeta(id)` lookup — implementation-time choice, not architecturally
   significant either way.

## Critical files for implementation

- `app/src/main/java/cc/dlabs/pesamind/core/data/ChannelRepository.kt` (pattern to mirror)
- `app/src/main/java/cc/dlabs/pesamind/core/sync/SyncWorker.kt` (push/pull ordering to extend)
- `app/src/main/java/cc/dlabs/pesamind/core/database/entity/MonthlyBudgetEntity.kt` and
  `YearlyBudgetEntity.kt` (schema already in place)
- `app/src/main/java/cc/dlabs/pesamind/core/network/ApiModels.kt`
  (`BudgetTransactionOperation`/`UpdateMonthlyBudgetRequest`/`CreateMonthlyBudgetRequest`
  shapes driving Decision 3)
- `app/src/main/java/cc/dlabs/pesamind/core/database/migration/PrefsToRoomMigrator.kt`
  (existing `toEntity()` mappers + `BudgetManager.init()` call site, wire-compatibility to
  preserve)
- `app/src/main/java/cc/dlabs/pesamind/features/budgets/BudgetViewModel.kt`,
  `SetMonthlyBudgetViewModel.kt`, `YealyBudgetViewModel.kt` (rewiring targets)
- `app/src/main/java/cc/dlabs/pesamind/MainActivity.kt` (`PesaMindApp.onCreate` — repository
  `.init()` wiring)
