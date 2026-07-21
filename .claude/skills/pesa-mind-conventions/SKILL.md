---
name: pesa-mind-conventions
description: Use when writing or reviewing Kotlin/Compose code in this repo (Pesa Mind Android) and you need the concrete lint/format commands, folder/naming conventions, or the reuse-first check-before-writing workflow. Load this before creating any new file under app/src/main, or before running a lint/format pass.
version: 0.1.0
---

# Pesa Mind conventions

This skill captures how this specific repo enforces the standards in `.claude/CLAUDE.md` (the target contract) — commands and mechanics, not rationale (rationale lives in `docs/vault/00-standards.md`).

## Lint / format

Ktlint only — **detekt is referenced in `.claude/CLAUDE.md` as a target but is not actually configured in this repo** (no `detekt` plugin in `build.gradle.kts`, no `detekt.yml`). Treat any mention of detekt config as aspirational, not current state, until it's actually added (tracked as a follow-up in `docs/vault/01-architecture-audit.md`).

```bash
./gradlew ktlintCheck    # lint — see gap below, whole module, no standalone CLI so can't scope to one file
./gradlew ktlintFormat   # auto-format
```

**Known gap:** `org.jlleitschuh.gradle.ktlint` (`build.gradle.kts:9`) is applied only to the **root** Gradle project. There's no `subprojects{}`/`allprojects{}` propagation and `app/build.gradle.kts` doesn't apply the plugin itself — so `ktlintCheck` today only lints root `.kts` build/settings scripts, **not** any file under `app/src`. In other words, the lint gate documented in root `CLAUDE.md` is not currently linting application code at all. This is a real, high-impact finding, not a style nit — see `docs/vault/01-architecture-audit.md`. Don't assume a clean `ktlintCheck` run means app code is clean.

No `.editorconfig` and no custom `ktlint {}` block exist in `build.gradle.kts` — ktlint runs with its built-in defaults. If you add custom rules, they belong in a `ktlint {}` block in the root `build.gradle.kts`, matching the existing single-module Gradle layout.

## Reuse-first, mechanically

Before writing any new composable, util function, or manager method:
1. `graphify explain "<name or concept>"` or `graphify query "<question>"` via Bash — both are real CLI subcommands. If `graphify-out/graph.json` doesn't exist yet, there is **no bare `graphify .` CLI command** (it errors: `unknown command '.'`) — a first-time full build requires the LLM-orchestrated `/graphify` skill (invoke via the Skill tool, path defaults to `.`), not a raw shell command. For a fast, code-only refresh of an existing graph, `graphify update <path>` is a real headless CLI subcommand ("no LLM needed").
2. If an equivalent exists, extend/reuse it. If you deviate, say so explicitly and name the node you didn't reuse — don't silently duplicate.
3. For anything beyond a quick check, prefer the `reuse-scout` agent (`.claude/agents/reuse-scout.md`) — it's built for exactly this and can enumerate call sites, not just confirm existence.

## Folder / naming, as currently laid out

Current top-level feature dirs: `analytics`, `auth`, `budgets`, `common`, `dashboard`, `home`, `settings`, `transactions` (all under `features/`). Current `core/` subpackages: `ads`, `coordinator`, `di`, `navigation`, `network`, `storage`, `theme`, `ui`, `utils`.

- Screens: `<Feature>Screen.kt`, PascalCase.
- ViewModels: `<Feature>ViewModel.kt`, one per screen/flow, extends `UnifiedViewModel` for new code (`core/coordinator/UnifiedViewModel.kt`) — plain `ViewModel()` is legacy debt, not a pattern to copy.
- Managers/singletons: `<Domain>Manager.kt` in `core/storage/`.
- `core/utils/` is cross-feature infrastructure only — no feature-specific ViewModels, screens, or types there. (`core/utils/` and `core/ads/` currently exist and haven't been audited for this yet — check `docs/vault/01-architecture-audit.md` if it exists before assuming they're clean.)
- One feature = one directory under `features/`; don't create an empty feature directory as a placeholder.
- Delete dead files/empty directories on sight when found — confirm with the user first (git-tracked removal is a destructive action).

## Shared UI

Check `core/ui/` before writing any `*Skeleton()`/`*EmptyState()`/`*ErrorState()` composable in a screen file — these belong in `core/ui/` and should be reused, not redefined per-screen.
