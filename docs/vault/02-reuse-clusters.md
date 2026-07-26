# Reuse clusters — Pesa Mind Android

Generated 2026-07-19 by manual application of the `reuse-scout` agent methodology
(`.claude/agents/reuse-scout.md`) — same caveat as the architecture audit: project
agents only load at session start, so this pass was run directly; a future
`/reuse-scan` run should reproduce and refresh this. Backed by `graphify-out/graph.json`
(837 nodes / 751 edges — copy at `docs/vault/graphify/`) plus direct `grep` verification
of every call site listed below.

## Cluster 1 — Empty/loading-state composables duplicated per screen instead of shared via `core/ui/`

`core/ui/` currently contains exactly two files: `DetailScreenTopBar.kt` and
`UnifiedScreenHeader.kt` — **no shared `EmptyState`/`Skeleton`/`ErrorState` composable
exists there**, despite `.claude/CLAUDE.md` explicitly requiring shared
loading/empty/error composables to live in `core/ui/` and forbidding private
per-screen equivalents. 8 private definitions exist across 6 screen files instead:

| Composable | File | Definition | Call site |
|---|---|---|---|
| `TransactionsEmptyState()` | `features/budgets/YealyBudgetDetailScreen.kt` | :734 | :215 |
| `TransactionsEmptyState()` | `features/budgets/Setmonthlybudgetscreen.kt` | :741 | :222 |
| `EmptyState()` | `features/settings/notifications/TransactionListScreen.kt` | :764 | :183 |
| `ChannelEmptyState()` | `features/settings/channels/ChannelScreen.kt` | :631 | :275 |
| `AnomalyEmptyState()` | `features/analytics/AnalyticsScreen.kt` | :2554 | :2257 |
| `BudgetSkeletonView()` | `features/budgets/BudgetScreen.kt` | :292 | :152 |
| `BudgetStatsSkeleton()` | `features/budgets/BudgetScreen.kt` | :897 | :400 |
| `DashboardSkeletonView()` | `features/dashboard/DashboardScreen.kt` | :965 | :145 |

### Sub-cluster 1a — exact duplicate (not just near-duplicate)

`TransactionsEmptyState()` in `YealyBudgetDetailScreen.kt:734` and
`Setmonthlybudgetscreen.kt:741` are **byte-for-byte identical** (verified by reading both
definitions side by side: same `Column`/`Surface`/`Icon`/`Text` structure, same
`RoundedCornerShape(18.dp)`, same `Icons.Outlined.Receipt`, same `"No transactions yet"`
copy, same spacing values). This is the clearest case in the codebase — a single
`TransactionsEmptyState` (or a generic `EmptyState(icon, title)` per below) in
`core/ui/` would delete one of these two definitions outright and remove the risk of the
two drifting (they're already two screens for what's functionally the same "monthly
budget" concept — `Setmonthlybudgetscreen.kt` sets up a monthly budget,
`YealyBudgetDetailScreen.kt` is its yearly-budget-detail counterpart).

### Sub-cluster 1b — near-duplicates, same shape, different copy/icon

`EmptyState()`, `ChannelEmptyState()`, `AnomalyEmptyState()` all follow the same
structural pattern as 1a (icon in a tinted rounded `Surface`, title `Text`, optional
body/action) with different icons and copy per screen — strong candidates to collapse
into one parameterized `EmptyState(icon, title, subtitle?, action?)` in `core/ui/`, not
verified as byte-identical (didn't diff line-by-line) but same shape by design.

### Sub-cluster 1c — skeleton/loading composables

`BudgetSkeletonView()`, `BudgetStatsSkeleton()` (both in the same file —
`BudgetScreen.kt` has two separate private skeleton composables for two different
sections of the same screen) and `DashboardSkeletonView()` — not diffed for exact
duplication, but same category of violation: shimmer/skeleton loading state defined
locally instead of in `core/ui/`.

**Recommendation (not actioned — read-only pass):** add `EmptyState`/`Skeleton`
composables to `core/ui/`, migrate all 6 call sites, delete the 8 private definitions —
the `Setmonthlybudgetscreen.kt`/`YealyBudgetDetailScreen.kt` exact-duplicate pair alone
is a same-day, low-risk cleanup.

## Checked, not found duplicated

- **Currency/amount formatting**: no `formatCurrency`/`formatAmount` function names and
  no inline `"KES "`/`"Ksh "`/`"KSh "` literal-prefix formatting found anywhere under
  `app/src/main/java/cc/dlabs/pesamind` — either already centralized somewhere not
  matched by this search pattern, or genuinely not yet needed per-screen. Worth a
  follow-up with a wider search pattern if a shared currency formatter is expected to
  exist and isn't easily found.
- **Date formatting**: 2 files use `SimpleDateFormat`/`fun formatDate` — below the
  "at least 2 near-duplicate implementations with evidence" bar to call a cluster
  without reading both definitions; not investigated further in this pass, flagged as a
  narrower follow-up (`grep -rn "SimpleDateFormat(" app/src/main/java/.../features` to
  find both).
- **`core/storage/*` manager read/write patterns**: not compared pairwise in this pass
  (9 managers — `TokenManager`, `AccountManager`, `BudgetManager`, `ChannelManager`,
  `NotificationStorage`, `TransactionManager`, `ThemeManager`, `StreakSessionCache`,
  `SyncPolicy` — each is a DataStore-backed singleton per root `CLAUDE.md`'s own
  description, which by design means they share a *pattern*, not necessarily duplicated
  *code* — worth a dedicated `reuse-scout` pass focused only on this if a shared
  DataStore-manager base/helper is being considered).

## Pre-write check reminder

Per `.claude/CLAUDE.md`'s reuse-first rule: before adding a new `EmptyState`/`Skeleton`
variant anywhere, this cluster is exactly the kind of thing to check first — a 9th
private definition would make the problem worse, not better.
