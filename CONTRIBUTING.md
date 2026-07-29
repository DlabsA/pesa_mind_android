# Contributing

## Onboarding (required)

Git hooks install automatically on common Gradle tasks. To install them immediately
after cloning:

```bash
./.githooks/install-hooks.sh
```

This enforces one thing: **the `pre-commit` hook blocks commits directly on `main`.**
Always work on a feature branch:

```bash
git switch -c feature/my-change
```

If hooks stop working, re-run the installer above.

## Before you open a PR

Run both, every time — there's no CI in this repo to catch it for you:

```bash
./gradlew ktlintCheck
./gradlew test
```

For anything touching Compose UI, also run `./gradlew assembleDebug` and click through
the change on a device/emulator — passing tests verify correctness, not that the
screen actually looks or behaves right.

## Architecture rules (enforced, not just style)

The full contract lives in [`.claude/CLAUDE.md`](.claude/CLAUDE.md) — read it before
touching anything nontrivial. The load-bearing rules, briefly:

- **MVVM only.** A Composable never calls `ApiClient`/`ApiService` or a `core/storage/`
  manager directly — always through a ViewModel.
- **No repository layer for a single feature in isolation.** Add one only when a
  domain has real cross-feature reuse or has to merge multiple sources (network +
  cache + transform). If you add one, say in the PR description why this domain
  crossed that line — see `ChannelRepository`/`TransactionRepository`'s doc comments
  for the precedent.
- **Every new ViewModel extends `UnifiedViewModel`**, not plain `ViewModel()` — that's
  what gets it cross-screen `StateEvent`s. Plain `ViewModel()` on an existing file is
  known debt being paid down, not a pattern to copy into new code.
- **`StateFlow` only** — no `LiveData`, no loose `mutableStateOf` for anything that's
  server/business state. One `UiState` (data class or sealed hierarchy) per screen.
- **Material 3 + theme tokens only** — no manual `Color(0x...)` or literal dimensions
  in a screen file; use `Spacing`/`Radius`/theme colors.
- **Reuse-first.** Check `core/ui/` before writing a new loading/empty/error
  composable, and check for an existing manager/repository before writing a new one.
  If this repo has `graphify` set up, run `graphify explain "<concept>"` first — see
  `.claude/CLAUDE.md`'s Reuse-first section for the exact workflow.

## Testing — one landmine, read this before writing a ViewModel/manager test

**Never use `kotlinx.coroutines.test.runTest`** to construct a ViewModel or manager
whose `init` block (or the method under test) can fall through to a real `ApiClient`
call, unless that call is mocked. `runTest` auto-detects the `TestDispatcher` installed
on `Dispatchers.Main` and drains it as part of finishing — including any coroutine
queued on `viewModelScope` by an `init { load() }` block — which means an unmocked
network call inside it fires a **real HTTP request against the production API**
(`https://api.dlabs.cc`), not a test double. This has actually happened in this repo
before (`ADR-0004`, the hotfix from 2026-07-22).

Use plain `kotlinx.coroutines.runBlocking` instead — it runs its own event loop and
never touches the `TestDispatcher`'s scheduler, so a queued `viewModelScope` coroutine
is left permanently pending (never executed) rather than drained. This applies to
every ViewModel/manager in this codebase today, since none of them have an injectable
`ApiService` seam yet.

Test locations: `app/src/test/` (JVM unit tests — for anything touching Room, use
Robolectric + an in-memory database, mirroring the existing `*RepositoryTest.kt`
files, not a mocked DAO) and `app/src/androidTest/` (instrumented, needs a
device/emulator).

## Secrets

Never commit a real value for `GOOGLE_ANDROID_CLIENT_ID`, a keystore password, or any
other secret — not even as a "temporary" literal fallback in `build.gradle.kts`. Fail
the build if the env var/property/file isn't set; don't fall back to a string literal.
See the [README](README.md#secrets) for the resolution order this repo already
follows for both of its current secrets.

## Documenting a real design decision

If a change is architecturally significant — new cross-cutting infrastructure, a
deviation from `.claude/CLAUDE.md`'s stated pattern, a security-relevant storage
change — write an ADR under `docs/decisions/`. Follow the existing format (Status/Date
header, Context, Decision, Verification/Consequences). Explain *why*, not just *what*
— the diff already shows what changed; the reasoning is what a future reader (human or
otherwise) actually needs from the ADR.

## Commit messages and deviations

Explain why, not just what. If you deviate from an existing pattern, an ADR's stated
plan, or a reviewer's suggestion, say so explicitly in the commit/PR and name what you
chose not to do instead, rather than silently diverging — this repo's own design docs
follow that discipline throughout `docs/decisions/`, and PRs should too.
