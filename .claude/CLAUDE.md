# Target Standards (contract, not docs)

This file is enforced every session. It states where the codebase is *going*, not
where it is. For current-state architecture, see the root `CLAUDE.md`.

## Reuse-first — non-negotiable

Before writing any new composable, util function, or manager method:
1. Run `graphify explain "<name or concept>"` (or `graphify query "<question>"`) via Bash,
   invoking it directly by name — do not wait for it to appear in the surfaced skill list.
2. If no `graphify-out/graph.json` exists yet, invoke the `/graphify` skill (Skill tool,
   no path argument defaults to `.`) to do the first full build — there is no bare
   `graphify .` shell command; running it directly errors `unknown command '.'`.
3. If an equivalent already exists anywhere in the graph, extend or reuse it. Do not create
   a parallel implementation. If you deviate (genuinely different requirements), say so
   explicitly and name the existing node you chose not to reuse.
4. After any move/rename/new module, run `graphify update .` (a real headless CLI
   subcommand — code-only re-extraction, no LLM needed) to keep the graph in sync. If it
   reports no existing manifest, invoke the `/graphify` skill instead (full build, needs
   LLM-orchestrated semantic extraction, can't run headless).

## Architecture

- MVVM: Screen (Compose) → ViewModel → data layer. No Composable may call `ApiClient`/
  `ApiService` or a storage manager directly — always through a ViewModel.
- Do not introduce a repository/UseCase layer for a single feature in isolation. Only add
  Clean Architecture layering (repository + use cases) when a domain has real cross-feature
  reuse or multi-source merging (network + cache + transform) — until then, ViewModel →
  `ApiClient`/manager singleton directly is correct, per the existing pattern in
  `core/storage/*`. If you add a repository, document why that feature crossed the
  complexity threshold and others didn't.
- Every new ViewModel extends `UnifiedViewModel` (`core/coordinator/UnifiedViewModel.kt`),
  not plain `ViewModel()`, so it receives cross-screen state events. Plain `ViewModel()` is
  legacy debt being paid down, not a pattern to copy.
- Feature code lives under `features/<feature>/`. Do not add feature-specific ViewModels,
  screens, or types under `core/utils/` — `core/` is cross-feature infrastructure only.

## State management

- `StateFlow` only. No `LiveData`, no ad-hoc `mutableStateOf` for anything that represents
  server/business state (transient UI-only state like a dropdown's open/closed flag is fine
  as local Compose state).
- One `UiState` data class or sealed hierarchy per screen, exposed as a single
  `StateFlow<UiState>`. No parallel loose `StateFlow`/`mutableStateOf` fields alongside it.

## UI

- Material 3 components and theming only (`core/theme/`). No manual color/dimension
  literals in a screen file — use `Spacing`/`Radius`/theme colors.
- Shared loading/empty/error composables (skeletons, shimmer, empty states) belong in
  `core/ui/`. Never define a private `*Skeleton()`/`*EmptyState()`/`*ErrorState()` composable
  inside a screen file — check `core/ui/` first, add to it if missing, then reuse.

## Secrets

- No secret, password, or key ever has a literal fallback value in a committed file
  (`build.gradle.kts` or otherwise). Env var / local `.env` / Gradle property only — if none
  is set, fail the build, don't fall back to a string literal.
- Any locally-persisted secret (JWT, refresh token, PIN, pattern) must be encrypted via
  Android Keystore (`EncryptedSharedPreferences` or a Keystore-wrapped key), not plain
  DataStore/SharedPreferences. `androidx.security.crypto` is already a dependency — use it,
  don't add a second one.

## Performance budgets

- Cold launch to first interactive frame: < 2s.
- Frame budget: 16.67ms (60 FPS). No unbatched network/disk I/O on the main thread inside
  a Composable or `LaunchedEffect` that runs on every recomposition.
- Typical screen memory footprint: < 100MB. Large lists use `LazyColumn`/`LazyRow` with keys,
  never `Column` + `forEach` for anything unbounded (transaction lists, channel lists).
- If you can't measure a budget (no profiler/device in this session), say so explicitly
  instead of claiming it's met.

## Testing

- **Never use `kotlinx.coroutines.test.runTest` to construct a ViewModel/manager whose
  `init` path (or any method under test) can fall through to a real `ApiClient` call,
  unless that call is mocked/faked.** `runTest` auto-detects a `Dispatchers.Main` backed
  by a `TestDispatcher` (set via `Dispatchers.setMain(...)`) and drains its scheduler as
  part of finishing — which runs any coroutine queued on `viewModelScope` (e.g. an
  `init { load() }` block) to completion, firing a real HTTP request against the
  production API from a "unit" test. Confirmed the hard way in
  `TransactionViewModelValidationTest.kt` (`ADR-0004` hotfix, 2026-07-22): an earlier
  version of that file used `runTest` and reliably hit `https://api.dlabs.cc/api/v1/`
  from every test run.
  - Use plain `kotlinx.coroutines.runBlocking` instead — it has its own event loop and
    never touches a separately-constructed `TestDispatcher`'s scheduler, so a queued
    `viewModelScope` coroutine is left permanently pending (never executed) rather than
    drained.
  - This applies to every ViewModel/manager in this codebase today, since none of them
    have an injectable `ApiService` seam yet (`ApiClient.api` is an eagerly-built `val`
    on a plain `object`, not mockable without a production-code change) — treat this as
    a standing landmine for any new JVM test, not a one-off fix.

## Folder / naming conventions

- Screens: `<Feature>Screen.kt`, PascalCase, no exceptions (e.g. not `Setmonthlybudgetscreen.kt`).
- ViewModels: `<Feature>ViewModel.kt`, one per screen/flow.
- Managers/singletons: `<Domain>Manager.kt` in `core/storage/`.
- One feature = one directory under `features/`; don't leave an empty feature directory
  as a placeholder — create it only when the first file lands in it.
- Delete dead files and empty directories on sight when you find them (confirm with the
  user first per the destructive-action rule, since it's a git-tracked removal) — don't let
  them linger as a second, unreferenced copy of a live screen.
