# Standards, with rationale

Companion to `.claude/CLAUDE.md` (the terse, enforced contract). That file says *what*;
this file says *why*, and points at the concrete evidence from
`docs/vault/01-architecture-audit.md` / `docs/vault/02-reuse-clusters.md` where a rule
exists specifically because of something found in this codebase, not in the abstract.

## Reuse-first

**Rule:** query `graphify` before writing any new composable/util/manager method; reuse
or explicitly justify a deviation; keep the graph in sync after structural changes.

**Why:** `docs/vault/02-reuse-clusters.md` found 8 private `EmptyState`/`Skeleton`
composables across 6 screen files, with zero shared equivalents in `core/ui/` — including
one pair (`TransactionsEmptyState` in `Setmonthlybudgetscreen.kt` and
`YealyBudgetDetailScreen.kt`) that is byte-for-byte identical. That's what reuse-first is
trying to prevent: a check that takes seconds costs less than a second near-duplicate
implementation drifting from the first. The rule only works if the graph is actually kept
current — hence the `graphify update .` step after structural changes, not just "run it
once."

**Command syntax note:** the CLI has no bare `graphify .` invocation — that specific
mistake was made in three places during this session's own toolkit provisioning
(a hook script, a slash command, and this very file) before being caught by actually
running the tooling. If you're about to write a new script or doc referencing graphify
commands, verify against `graphify --help` rather than copying a previous reference.

## Architecture (MVVM, Clean-where-warranted)

**Rule:** Screen → ViewModel → data layer, no Composable touching `ApiClient`/a manager
directly; don't add a repository/use-case layer for a single feature in isolation; every
new ViewModel extends `UnifiedViewModel`.

**Why the ViewModel-bypass rule matters here specifically:** the audit found two live
violations — `SettingsScreen.kt` (6 direct manager calls: `ThemeManager`,
`AccountManager`, `TokenManager`) and `BudgetScreen.kt` (`AccountManager.getAccount()`).
Both bypass the ViewModel layer that's supposed to be the single place state changes get
coordinated and published via `UnifiedStateCoordinator`. A Composable that reads
`AccountManager` directly won't react to an `AccountUpdated` event published elsewhere —
it'll just be stale until the next full recomposition trigger.

**Why `UnifiedViewModel` over plain `ViewModel()`:** the audit found 10 of 12 ViewModels
still on plain `ViewModel()`, and three of those (`AuthViewModel`,
`SetMonthlyBudgetViewModel`, `YearlyBudgetViewModel`) are simultaneously among the
highest-fan-in nodes in the entire dependency graph. The pattern is inverted from what
you'd want: the screens most connected to the rest of the app (i.e. most likely to need
to react when something else changes) are the ones not wired into the event bus. This
isn't a style preference — it's a live functional gap. Migrating `AuthViewModel`,
`SetMonthlyBudgetViewModel`, and `YearlyBudgetViewModel` first would fix the highest-
impact instances of it.

**Why not a blanket repository layer:** `core/storage/*` managers already work as
lightweight repositories (DataStore-backed singletons) for the common case. Adding a
formal repository/use-case layer everywhere would mean two abstractions doing the same
job for most features, for no benefit until a domain actually needs multi-source merging
(network + cache + transform) or real cross-feature reuse — neither of which any single
current `core/storage/*` manager needs today.

## State management (StateFlow only, one UiState per screen)

**Why:** consistency lets any ViewModel be reasoned about the same way, and lets
`UnifiedViewModel` do its job (subscribing once to the bus, exposing one state stream).
No `LiveData` usage was found anywhere in this codebase — that part of the rule is
already fully honored; it's worth keeping enforced precisely because it's currently free
of exceptions to fix.

## UI (Material 3 only, shared empty/loading states in `core/ui/`)

**Why the shared-composable rule matters here specifically:** see Reuse-first above — 8
private `EmptyState`/`Skeleton` composables exist today with zero shared equivalents.
`core/ui/` currently holds only `DetailScreenTopBar.kt` and `UnifiedScreenHeader.kt` — it
was scaffolded for exactly this purpose but the empty/loading-state migration never
happened. Adding a generic `EmptyState(icon, title, subtitle?, action?)` there and
migrating the 6 call sites is lower-risk than most refactors in this codebase because the
byte-identical pair proves at least two of the eight were never meant to diverge in the
first place.

## Secrets (no literal fallback, ever; Keystore for local persistence)

**Why this is the single most important rule in this document:** the audit found a live
violation of the exact letter of this rule — `app/build.gradle.kts:47,49` hardcodes the
release-signing keystore password as a literal fallback
(`System.getenv("KEYSTORE_PASSWORD") ?: "K@sh404730"`), currently on `HEAD` and present
in git history. This is not a hypothetical the rule guards against — it already happened,
to the credential with the largest blast radius in the project (the key that signs both
release and debug builds). The `GOOGLE_ANDROID_CLIENT_ID` resolution pattern two lines
above it (Gradle property → env var → `.env`, no literal fallback) is the pattern to
copy; the keystore signing config is the pattern that broke it. See
`docs/vault/01-architecture-audit.md` finding 1 for detail and recommended remediation
(not actioned there — audit is read-only by design).

## Performance budgets

**Why "say so if you can't measure" is load-bearing, not boilerplate:** performance
claims without a profiler are indistinguishable from guesses, and a wrong "this meets
budget" is worse than an honest "unmeasured" because it removes the incentive to ever
actually measure. `compose-perf` (`.claude/agents/compose-perf.md`) is built around this
distinction specifically — it reports structural risk, explicitly labeled unmeasured,
rather than rounding a risk assessment up to a budget claim.

## Folder / naming conventions

**Why the exact `Setmonthlybudgetscreen.kt` example exists in the enforced contract:**
because it's a real file in this repo (`features/budgets/Setmonthlybudgetscreen.kt`) —
the rule wasn't written abstractly and later matched by coincidence; it's naming the
counter-example that already existed when the rule was written, and that file still
hasn't been renamed. Two more files repeat a related mistake with a typo instead of
casing (`YealyBudgetDetailScreen.kt`, `YealyBudgetViewModel.kt` — "Yealy" instead of
"Yearly"; the class names inside are spelled correctly, so it's filename-only drift).

**Why `core/utils/` gets called out specifically:** `core/utils/TransactionViewModel.kt`
is a real, current violation — a fully-formed screen-facing `ViewModel` (correctly
extending `UnifiedViewModel`, ironically) sitting in the one package the rule explicitly
prohibits it from. It's evidence the rule is necessary, not just tidy: without it, the
line between "cross-feature infrastructure" and "a feature that happens to be imported
from a few places" erodes one file at a time.
