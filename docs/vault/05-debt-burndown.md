# Debt burndown — pesa_mind_android + pesa-mind (backend)

Read-only triage. Sources: `docs/decisions/ADR-0004-offline-first.md` (this repo),
`docs/vault/01-architecture-audit.md` (this repo, dated 2026-07-19 — re-verified against
current source below, since two commits landed after it), direct inspection of
`~/Github/Personal/pesa-mind`. Nothing in this document has been fixed; it's ordering and
classification only, per this session's instruction.

## Part 0 — the two unknowns, resolved

### Is backend PR #13 (idempotent-create) actually live on `api.dlabs.cc`?

**Yes, confirmed live.** Not inferred from `git log` alone — checked three independent
ways:

1. `gh run list --workflow=deploy.yml --limit 5` (repo has `.github/workflows/deploy.yml`,
   which SSHes into the server, `git pull origin main`, runs `deploy.sh`, then curls
   `/health` as its own gate) shows both relevant runs **completed / success**:
   - PR #13 merge (`16a5ba9`) → run `30041959214`, success, 2026-07-23T20:24:34Z.
   - PR #14 merge (`87774af`, the unrelated streak-lock fix) → run `30174342136`, success,
     2026-07-25T20:49:22Z, i.e. a later, successful deploy of a commit descended from
     `16a5ba9` — so `16a5ba9`'s changes are necessarily in that running instance too.
2. `curl -i https://api.dlabs.cc/health` → `HTTP/2 200`, `{"status":"ok"}`, returned live
   during this session.
3. `git merge-base --is-ancestor 61802b2 main` on `origin/main` (post-`fetch`) → confirms
   the idempotency commit is an ancestor of what's deployed.

**To reproduce this yourself later** (in case the next deploy fails or rolls back):
```bash
cd ~/Github/Personal/pesa-mind
git fetch origin
gh run list --workflow=deploy.yml --limit 5        # deploy history + pass/fail
gh run view <run-id> --log | grep -i "health\|deploy"  # confirm the health-check step passed
curl -i https://api.dlabs.cc/health                  # confirm it's answering right now
git merge-base --is-ancestor <commit> main && echo "in main"  # confirm a specific fix shipped
```

**Is `fix/streak-redis-lock-failure` merged/deployed?** Yes — merged via PR #14
(`87774af`), same deploy run confirmed success above. Both backend fixes this ADR/session
have discussed are live.

### Was `TransactionViewModel` already relocated out of `core/utils/`?

**No — still at `app/src/main/java/cc/dlabs/pesamind/core/utils/TransactionViewModel.kt`.**
Checked directly (`find ... -iname "*TransactionViewModel*"`), one result, same path
`01-architecture-audit.md` and ADR-0004 both flagged. Not double-work — this is still open.

## Part 0.5 — audit items re-verified as *already fixed* (not in this backlog)

`01-architecture-audit.md` is dated 2026-07-19; three of its findings predate later commits
and are confirmed resolved by direct source inspection this session — excluding them so
they don't get re-actioned:
- **Finding 1 (CRITICAL, hardcoded keystore password)** — fixed. `app/build.gradle.kts` now
  has no literal fallback (`resolveSigningValue("KEYSTORE_PASSWORD", ...)`, comment
  confirms "No literal fallback... if unset, release signing is skipped"). Its own
  `REMEDIATION.md` (rotate password + purge history) is a separate, already-tracked,
  user-executed-only follow-up — not duplicated here.
- **Finding 2 (ktlint doesn't scope to `app/`)** — fixed. `app/build.gradle.kts:10` now
  applies `alias(libs.plugins.ktlint)`.
- **Finding 3 (10/12 ViewModels on plain `ViewModel()`)** — fixed. Grepped for
  `: ViewModel()` under `app/src/main/java` — the only match is `UnifiedViewModel.kt`
  itself (the base class). Every feature ViewModel now extends it.

## Session status (what's actually moved since this doc was first written)

- **A3 (uncommitted refresh-token WIP)** — resolved without action needed: a later session
  discovered it had already landed inside an unrelated commit (`3a1310a`) before this session
  started. No longer a precondition for anything.
- **A1 (`TokenManager` plaintext secrets)** — implemented (Tink + Android Keystore, DataStore
  kept as the store — see `docs/decisions/ADR-0005-token-storage-encryption.md`).
  `ktlintCheck`/`assembleDebug` both green. **Not yet committed** — awaiting approval.
- **B1/B3 (backend compile-breakers, `gorm.DeletedAt`)** — a handoff prompt was written for a
  fresh session in `~/Github/Personal/pesa-mind`; execution status from that session is not
  visible from here. Treat as "prompted, not confirmed done" until checked directly
  (`git log`/`gh pr list` in that repo).
- **Two new items found this session** — A9 and A10 below, from a direct review of
  Channels/Transactions/Budgets/Stats offline-first progress. Neither was in the original
  audit or ADR-0004; both are real, code-verified gaps, not speculation.

## Ordering hazards (explicit, load-bearing — read before sequencing anything below)

1. **Android Phase 3 dup-row cleanup (A6) must not run until backend PR #13 is confirmed
   deployed.** Reason: cleaning up already-duplicated rows is pointless if the mechanism
   that creates new duplicates (timed-out-retry racing an outbox POST with no
   upsert-on-conflict) is still live — cleanup would immediately be repolluted. **This
   hazard is now cleared** (see Part 0) — A6 is unblocked as of this session.
2. **Backend Go compile-breakers (B1) before any golangci-lint/CI gate (B2).** A lint gate
   over a tree that doesn't `go build ./...` at the root is either impossible to make green
   or has to be scoped around the breakage forever — fix the breakers first, cheaply, then
   set up the gate over a clean tree.
3. **Backend `gorm.DeletedAt` soft-delete (B3) before delta-pull (B4, roadmap) or the
   Android `Tombstone` table (A7).** A delta-pull response needs a way to say "this id was
   deleted since your last sync" — today's plain `*time.Time` + hard-deletes-in-practice
   gives it nothing to report deletions from. Wiring `Tombstone` to real use hits the exact
   same wall. Both stay blocked on B3 regardless of which one gets scheduled first.

## Backend (`~/Github/Personal/pesa-mind`) — sequenced

No open PRs (`gh pr list --state open` empty) besides what's already covered above.

### B1. Go build-breaking dead code — DEBT
**Severity:** low runtime impact (`cmd/api`, the real binary, builds clean) / **high
tooling blast radius** (breaks `go build ./...`, `go vet ./...` at the repo root for
anyone who doesn't already know to scope around it).
**Items:** `internal/domain/model/model.go` imports a nonexistent `savingsgoal` package
(zero importers — dead); `internal/interfaces/http/routes.go` doesn't compile and isn't
wired into `main.go` (dead); `cmd/seeder/` has `main` redeclared across two files.
**Dependencies:** none. **Blocks:** B2.
**Why first:** cheapest, lowest-risk item in the entire backlog (delete/fix dead files),
and it's a hard precondition for B2.

### B3. `gorm.DeletedAt` real soft-delete migration — DEBT
**Severity:** high. `BaseModel.DeletedAt` is a plain `*time.Time`, so GORM's automatic
soft-delete/scoping never activates; `category`/`transaction` `Delete()` are real hard
deletes today, and `transactions.channel_details_id` has `ON DELETE CASCADE` — deleting a
channel silently hard-deletes every transaction referencing it, via the DB, not app code.
**Blast radius:** every `BaseModel`-derived domain (`transaction`, `category`, `user`,
`automation`, `analytics`, `budget`); requires a schema migration, a decision on the FK
cascade, and adding `Unscoped()`/explicit `deleted_at IS NULL` filters wherever a
tombstone must stay invisible (two domains — `notification`, `automation` — already
hand-roll this; `transaction`/`category` don't filter at all, harmless today only because
a soft-deleted row can't currently exist).
**Dependencies:** none blocking it. **Blocks:** B4 (roadmap), A7.
**Bundle opportunistically:** `GormUserRepository.SoftDelete()`/`.Delete()`
(`user/gorm_repository.go:242-248`) is dead code with a false doc comment claiming
GORM auto-soft-deletes (it doesn't, per this same finding) — same root confusion, zero
current callers, cheap to fix or delete in the same pass rather than as a separate task.

### B6. Analytics cash-flow waterfall: opening/closing balance hardcoded to 0 — DEBT
**Severity:** medium — confirmed by reading the code
(`comprehensive_service.go:605-608`): `OpeningBalance: 0, ClosingBalance: 0` are literal,
not a fallback-on-error. Every user's cash-flow waterfall chart shows a wrong opening/
closing balance today, silently, not just on some edge case.
**Blast radius:** scoped to this one waterfall computation, not all analytics.
**Dependencies:** none. Needs the actual profile-balance field wired in
(`// TODO: Get actual balance from profile model` names the gap precisely).

### B2. No `golangci-lint`/CI-equivalent Go gate — DEBT
**Severity:** medium (parity gap — Android has `ktlintCheck`, backend has nothing;
currently `go build`/`go vet`/`gofmt -l`/`go test` are run ad hoc, per-branch, by whoever's
touching that code, not enforced).
**Dependencies:** **must follow B1** (see hazard 2). No other blockers.

### B4. Delta/cursor pull (`updated_since` query param) — FEATURE, routes to roadmap
Confirmed absent (`grep -rn "updated_since|cursor|server_time" internal/` → zero hits;
both GET routes take no query params at the registration level). Net-new capability, not
a fix for broken behavior — full-list-plus-diff (what Android already does) still works
correctly without it. **Dependency: blocked on B3** (see hazard 3) — noting the order now
so whoever picks this up later doesn't have to rediscover it.

### B5. Push/email notification provider integration — FEATURE, routes to roadmap
Both stubbed (`notification/service.go:28,34`, `notification_handler.go:72,100`),
never implemented. No dependencies; not sequenced against anything else here.

## Android (`pesa_mind_android`) — sequenced

No open PRs. One uncommitted working-tree diff exists right now (see A3).

### A3. Uncommitted refresh-token WIP — housekeeping, resolve first
`git diff` on `TokenRefreshInterceptor.kt`/`AuthManager.kt`/`TokenManager.kt` (adds
`TokenManager.clearRefreshToken()`, calls it on refresh-failure paths, strips some
emoji logging) is real, uncommitted work sitting in the tree right now.
**Not a backlog item — a precondition.** A1 below touches the same three files;
resolve this (commit it, with a real test using `runBlocking` per the standing
`runTest` landmine, or explicitly shelve it) before starting A1 so the two don't tangle
into one messy diff.

### A1. `TokenManager` stores JWT/PIN/pattern in plaintext DataStore — DEBT
**Severity:** critical/high — direct, named violation of this repo's own secrets rule
(`.claude/CLAUDE.md`: "any locally-persisted secret ... must be encrypted via Android
Keystore"). A rooted device, an `adb backup`, or a shared/lost device exposes the JWT,
PIN, and pattern in cleartext today.
**Blast radius:** every `TokenManager` consumer — `TokenRefreshInterceptor`,
`AuthManager`, `NavGraph`'s lock-state gating, `ApiClient`. Nontrivial migration: needs
`EncryptedSharedPreferences`/Keystore-wrapped storage plus a one-time migration path for
already-persisted plaintext values (same shape of problem `PrefsToRoomMigrator` already
solved once for a different store).
**Dependencies:** none blocking; **do after A3** (same files).
**Overlaps with A12's SettingsScreen half** — see A12.

### A6. Phase 3: historical duplicate-row cleanup — DEBT, now unblocked
Two known populations, both explicitly out of scope when the dedup fix shipped:
already-migrated channels stuck with `normalizedSenderKey = null` (never backfilled,
to avoid a bulk-`REPLACE` silently dropping a real duplicate mid-migration), and
already-persisted duplicate transaction rows from before the TID/`smsSourceKey` dedup
existed.
**Severity:** medium-high — real bad data already visible to users (double-counted
amounts in transaction lists/budgets), not a theoretical risk.
**Dependencies:** **was blocked on backend PR #13's deploy (hazard 1) — now cleared.**
Still needs a product decision before implementation: which row survives a merge, does a
`channelId` on an orphaned duplicate transaction get re-pointed, and the ADR's own
explicit constraint that this needs a user-facing or logged confirmation step, not a
silent bulk delete.

### A8. `SyncWorker`: two unique-work names can race the same outbox row — DEBT
**Severity:** medium — a real, documented, un-fixed concurrency gap (`pesamind_sync_periodic`
and `pesamind_sync_one_shot` aren't deduped against each other by WorkManager's `KEEP`
policy, and outbox claims aren't wrapped in a DB-level transaction). Same subsystem as A6;
sensible to schedule adjacently since whoever's back in `SyncWorker` for one should check
the other.
**Dependencies:** none.

### A12. Composables calling storage managers directly, bypassing the ViewModel — DEBT
Two files, materially different effort:
- **`BudgetScreen.kt:109`** — already has `BudgetViewModel` injected via
  `hiltViewModel()`; the one `AccountManager.getAccount()` call just needs to move into
  the existing ViewModel. Cheap, isolated.
- **`SettingsScreen.kt`** (lines 47, 54, 90-92, 246) — **has no ViewModel at all today**
  (confirmed: zero `ViewModel`/`viewModel()` references in the file). Fixing this means
  creating a new `SettingsViewModel` from scratch, not routing an existing one — a
  materially bigger lift than the audit entry implies. It's also the more actively-touched
  file (12 commits) and the one whose direct calls include `TokenManager.clearTokens()`/
  `clearLock()` — the exact same API surface A1's Keystore migration will change.
  **Recommend sequencing SettingsScreen's fix after A1 lands**, so its logout/token-clear
  call sites get touched once, not twice.
**Dependencies:** SettingsScreen half informally follows A1; BudgetScreen half has none.

### A5. Rename/relocate cleanup batch — DEBT, bundle together
- Relocate `TransactionViewModel` (+ `TransactionCreationResult`) out of `core/utils/`
  into a feature package — confirmed still open (Part 0 above). Medium blast radius:
  touches home, transactions-list, and SMS-pipeline call sites, but it's a mechanical
  move/rename, not a logic change.
- `features/budgets/Setmonthlybudgetscreen.kt` → `SetMonthlyBudgetScreen.kt`,
  `YealyBudgetDetailScreen.kt`/`YealyBudgetViewModel.kt` → `Yearly...` (class names inside
  are already correctly spelled — filename-only typo). Same mechanical-rename risk profile;
  natural to batch with the above in one pass since both are import-touching renames with
  no behavior change.
**Dependencies:** none blocking; safe now (no other in-flight branch currently touches
`TransactionViewModel`, per this session's `git status`/`git log` checks).

### Trivial cleanup batch — DEBT, lowest priority, bundle together
- `core/ads/` — empty directory, delete on sight per the repo's own dead-file rule
  (confirm with user first, per the destructive-action convention — it's a git-tracked
  removal even though the directory has no content).
- `NotificationStorage`'s pending-message sub-feature — confirmed still write-only
  (nothing reads it back); note this is narrower than deleting `NotificationStorage`
  itself, which is still actively called from the SMS success path.
- `ThemeManager`'s raw-`SharedPreferences`-instead-of-DataStore inconsistency — cosmetic,
  no correctness bug.
- detekt referenced in `.claude/CLAUDE.md` as an intended tool, never configured anywhere.
- 9 files with manual `Color(0x...)` literals instead of theme tokens.
All independent, all low severity, no dependencies on each other or on anything above.

### A9. Budgets: Room schema inert, DataStore cache silently broken — DEBT
**Severity:** medium-high — not security, but a real, currently-shipping correctness bug,
not just "not yet migrated." `MonthlyBudgetEntity`/`YearlyBudgetEntity` and their DAOs exist
in the Room schema (wired into `PesaMindDatabase`/`DatabaseModule` since Step 1) but are
**called from nowhere** outside their own file and the DB wiring — confirmed by grep,
zero real usage. `BudgetViewModel` still goes through `ApiClient.api` +
`BudgetManager` (the old DataStore-blob cache), and `BudgetManager.init()` is called
**only** from `PrefsToRoomMigrator` — never from `PesaMindApp.onCreate()`. Every
`BudgetManager` method guards on `isInitialized()`, permanently `false` in real app
runtime, so `isMonthlyBudgetsCacheStale()`/`getMonthlyBudgetByMonthYear()`/`saveYearlyBudgets()`
etc. are silent no-ops today — budgets have **zero working cache and zero offline
fallback**, despite the code reading like there should be one. Not a regression (budgets
were never offline-capable), but worse than "not started": the code creates a false
impression of caching that isn't happening.
**Blast radius:** large. `BudgetManager` is architecture-audit god-node #2 (22 edges, the
single highest-fan-in `core/storage/` singleton in the app) — consumed by `BudgetViewModel`,
`SetMonthlyBudgetViewModel`, `YearlyBudgetViewModel`, and their three screens. Fixing this
properly means building a `BudgetRepository` on the *already-existing* Room schema (no new
entities needed, unlike Slice A had to build from scratch) plus wiring it into `SyncWorker`
and all three ViewModels.
**Dependencies:** none blocking; this is ADR-0004's Slice B, reframed as an active bug
rather than a future feature. A cheap stopgap (just call `BudgetManager.init()` in
`PesaMindApp.onCreate()`, restoring the pre-ADR-0004 network+DataStore-cache behavior) was
explicitly rejected in ADR-0004 as throwaway work about to be deleted by Slice B — that
reasoning still holds, but worth re-weighing now that severity is confirmed higher (actively
broken, not just "old"), especially if Slice B has no near-term ETA.

### A10. Dashboard/Analytics/Budget stats: the offline→online refresh bridge doesn't work — DEBT
**Severity:** medium-high — user-visible, undermines the app's core value proposition
directly, and affects **already-shipped, working features** (Channels/Transactions), not
just the unmigrated Budget domain. `DashboardViewModel`/`AnalyticsViewModel`/`BudgetViewModel`
all read 100% from the network (`ApiService.getDashboard()`/`getAnalytics()`), zero Room
involvement. A bridge clearly meant to keep them current
(`TransactionViewModel.performCreateTransaction()` publishes `StateEvent.TransactionCreated`
specifically so — per its own comment — "Dashboard and Analytics refresh automatically") is
broken in three independent ways, confirmed by direct code reading:
1. **Offline:** `DashboardViewModel.refresh()` checks `networkMonitor.isConnectedNow`; if
   false, no fetch is attempted at all.
2. **Online:** the refresh fires immediately after the Room insert, before `SyncWorker` has
   actually pushed that transaction (it runs on its own cadence, not synchronously per
   create) — so the fetch returns stale server data that doesn't include what was "just
   added."
3. **After the real sync push lands:** `SyncWorker.kt` publishes **zero `StateEvent`s**
   (confirmed by grep) — nothing tells any stats screen to refetch. `StateEvent.SyncRequested`
   exists and `DashboardViewModel` listens for it, but nothing anywhere ever publishes it —
   dead, same pattern as the `Tombstone` table. Compounding it, `load()` on both
   `DashboardViewModel` and `AnalyticsViewModel` guards on `if (state != null) return`, so
   even reconnecting doesn't force a refetch once one has already loaded.
**Net effect:** a transaction captured offline can be correct in the transaction list forever
while the dashboard total never catches up in that app session — not delayed, potentially
never, until the app is killed and relaunched.
**Blast radius:** `DashboardViewModel`, `AnalyticsViewModel`, `SyncWorker` (needs to actually
publish something); `BudgetViewModel` once A9/Slice B gives it real local data to aggregate.
Smaller than A9's blast radius — no new Room schema needed, the data these screens need
already exists in Room for Channels/Transactions today.
**Dependencies:** independent of A9 for the Channel/Transaction half — fixable today. The
Budget half of this is blocked on A9 (nothing to aggregate locally until Budgets has real
Room data). Two real fix directions, a design decision not yet made — see "What to start
with" below.

### A7. `Tombstone` entity/table — DEBT, cross-repo gated
Schema exists, zero writers (confirmed via grep on `tombstoneDao()`). Not usable for real
until backend soft-delete (B3) and eventually delta-pull (B4) exist — see hazard 3.
**Recommendation to consider when this is picked up:** given B3/B4 are both
roadmap/backend-gated with no ETA, dropping the unused table now (cheap, safe, nothing
depends on it) may be more honest than leaving a schema element that can't do anything
useful for an indeterminate time — a call for whoever owns this next, not made here.

### Explicitly routed to roadmap, not this backlog (FEATURE)
- ADR-0004 **Slice B** (budgets/profile offline-first) — **superseded by A9's framing
  above**: this is no longer purely a future feature, it's the fix for a currently-broken
  cache. Keeping the roadmap/FEATURE label for the *profile* half (never was broken, just
  not yet local-first); the *budget* half is really A9 now — see A9 for the debt framing.
- ADR-0004 **Slice C**'s Paging 3 evaluation, and deleting
  `TransactionManager`/`BudgetManager`/`NotificationStorage` outright — **blocked on Slice
  B/A9 landing** (can't delete what ViewModels still call).
- `SyncStatusBadge` tap/retry affordance for `FAILED` rows.

## Recommended first 3 (original, from before this session's Channels/Transactions/Budgets/
Stats review — kept for the record, not re-litigated)

1. Backend B1 — delete/fix the Go compile-breakers.
2. Backend B3 — `gorm.DeletedAt` real soft-delete.
3. Android A1 — `TokenManager` → Keystore-backed encrypted storage.

A1 is now implemented (see "Session status" above); B1/B3 were handed off to a fresh
backend session. What follows is new: which of A9/A10 is more urgent, and where to start.

## Critical vs. biggest blast radius — these are two different items, not one

**Most critical to implement: A10 (the stats refresh bridge).** Not because the bug is more
severe in isolation than A9, but because of *who it already affects*: A9 (Budgets) only hits
users of a feature that has never worked offline — no regression, just an unfulfilled
promise. A10 hits **every user of Channels/Transactions**, features that ADR-0004 already
shipped and that this app's core pitch (SMS auto-capture working offline) depends on. Worse,
the broken piece looks like a working fix (`TransactionCreated` publishes, a comment says
"Dashboard and Analytics refresh automatically") — that's a false-confidence trap for whoever
next assumes this is handled and builds on top of it. Fix this first, or Budgets (A9) will
just inherit the same broken bridge once it has real data to feed it.

**Biggest blast radius: A9 (Budgets data layer).** `BudgetManager` is the single highest-fan-in
`core/storage/` singleton in the entire app (22 edges per the architecture audit — more than
`ChannelManager`, more than any other manager). A real fix touches three ViewModels, three
screens, a new `BudgetRepository`, and `SyncWorker` — comparable in size to the whole of
Slice A (which took three separate commits: A1 repositories, A2 outbox/sync, A3 SMS
rewire). A10, by contrast, touches two existing ViewModels and one existing worker, no new
Room schema — a much smaller, more contained change.

**Practical consequence of this pairing:** start with the smaller, more critical item (A10)
rather than the bigger one (A9) — not just because it's cheaper, but because A9's eventual
`BudgetRepository`/`BudgetViewModel` work should plug into an *already-fixed* stats bridge
instead of inheriting the same broken one and needing a second pass later.

## What to start with: A10, outlined (not implemented)

Two real fix directions exist; recommending one but this is a design call worth confirming
before writing code:

1. **Client-side Room aggregation** — Dashboard/Analytics compute their headline numbers
   from a `TransactionDao` aggregate query instead of trusting the server. Most correct
   "offline-first" answer, but a bigger lift: new DAO queries, and a real design question
   for anything Room *can't* compute (anomalies, streak) — those stay server-sourced,
   meaning the screen ends up with two data sources reconciled in one `UiState`, not one.
2. **Fix the existing event bridge instead of replacing it** — keep stats server-computed
   (no architecture change), but make the bridge that was already half-built actually work:
   - Add a real completion signal: `SyncWorker.doWork()` publishes a new
     `StateEvent.SyncCompleted` (or reuses `SyncRequested` — pick one, `SyncCompleted` name
     is more accurate to what actually happened) after a push+pull cycle finishes,
     success or partial.
   - `DashboardViewModel`/`AnalyticsViewModel` (and `BudgetViewModel` once A9 lands)
     subscribe to it and call `refresh()` unconditionally — no `isConnectedNow` guard on
     this path, since a completed sync implies connectivity.
   - Relax `DashboardViewModel`'s cold-start `NetworkMonitor.isConnected.collect { if
     (connected && dashboard == null) ... }` — that `dashboard == null` guard is what blocks
     a stale-then-reconnect refresh; needs to distinguish "never loaded" from "loaded but
     stale," not just null-check.
   - Keep `TransactionCreated`'s existing immediate `refresh()` call for responsiveness (a
     close-but-possibly-stale number beats nothing), with `SyncCompleted` as the correction
     that actually lands the right number afterward.
   - Verify: does `SyncWorker` have access to `UnifiedStateCoordinator.publishEvent` today?
     (It's a `@HiltWorker`, not a `ViewModel` — confirm the coordinator is reachable from
     there before assuming this is a one-line add.)

**Recommending direction 2** — smaller, doesn't change the stats architecture, and directly
closes the specific bug chain found this session (no completion event + blocking guards +
offline no-op). Direction 1 is a legitimate longer-term improvement but a bigger, separate
piece of work — not what "start with" should mean here.

Not yet actioned — this is the outline, not the diff. Say the word and I'll write it up
properly (or push back if you want direction 1 instead).
