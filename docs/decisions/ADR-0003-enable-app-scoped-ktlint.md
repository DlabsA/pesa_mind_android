# ADR-0003: Apply ktlint to `:app`, closing the lint-gate scope gap

**Status:** Accepted
**Date:** 2026-07-20

## Context

`docs/vault/01-architecture-audit.md` finding 2 (HIGH): `org.jlleitschuh.gradle.ktlint`
was applied only to the root Gradle project (`build.gradle.kts:9`), with no
`subprojects{}`/`allprojects{}` propagation and no plugin application inside
`app/build.gradle.kts`. Root `CLAUDE.md` documents `ktlintCheck` as the project's lint
gate without this caveat. Confirmed via `./gradlew ktlintCheck --dry-run`: the only tasks
that ran were `ktlintKotlinScriptCheck`/`runKtlintCheckOverKotlinScripts`, scoped to root
`.kts` build/settings scripts — none of the 86 Kotlin files under `app/src` had ever been
ktlint-checked by the command the project's own docs point to.

## Decision

Add `id("org.jlleitschuh.gradle.ktlint") version "12.1.0"` (matching the root project's
pinned version) directly to `app/build.gradle.kts`'s `plugins {}` block. This is a
single-module app with only `:app` included (per `settings.gradle.kts`), so applying the
plugin directly in that one subproject is simpler than introducing a `subprojects{}`
block or an `apply false` + propagation pattern for a project graph that will likely
never have a second module.

## Verification

- `./gradlew ktlintCheck --dry-run`: `:app:ktlintMainSourceSetCheck`,
  `:app:ktlintTestSourceSetCheck`, `:app:ktlintAndroidTestSourceSetCheck`, and sibling
  `:app:*` tasks now exist (previously absent entirely).
- `./gradlew ktlintCheck --continue`: real violations now surface in
  `app/src/test/java/cc/dlabs/pesamind/ExampleUnitTest.kt`,
  `app/src/androidTest/java/cc/dlabs/pesamind/ExampleInstrumentedTest.kt`, and
  `app/src/test/java/cc/dlabs/pesamind/features/auth/GoogleOAuthTest.kt` (missing
  trailing newlines, wildcard imports, unordered imports, missing trailing commas,
  multiline-expression formatting) — confirms the gate is live, not just wired.
- `./gradlew assembleDebug`: still succeeds — the plugin addition doesn't affect
  compilation or build output, only adds lint tasks.

## Parser crash found and fixed as part of this same change

`:app:ktlintMainSourceSetCheck` — the task covering `app/src/main` (86 files, the source
set that matters most) — initially did not complete with a violation report at all. It
**threw**:

```
Rule 'standard:argument-list-wrapping' throws exception in file 'DashboardScreen.kt' at position (248:60)
```

Root cause: `DashboardScreen.kt:247` and `:270` both had trailing whitespace inside a
lambda argument —

```kotlin
UnavailableFeatureOverlay(
    onNavigate = { 
        navController?.navigate(Routes.SetYearlyBudget.route)
    },
```

(`onNavigate = { ` — trailing space before the newline, at two call sites in the same
file). The `standard:argument-list-wrapping` rule in this ktlint version can't parse that
pattern without throwing, rather than reporting it as a normal violation — so **the
gate-enabling change itself would have shipped a gate that errors instead of running**,
per `android-reviewer`'s finding on the first version of this diff. Fixed the trailing
whitespace at both sites (whitespace-only edit, nothing else touched in the file) as part
of this same commit, scoped narrowly to just those two lines — two other unrelated
trailing-whitespace lines elsewhere in the file (a blank line and a `when` block) were
left alone since they don't hit this crash and are out of scope for a gate-enabling
change.

## Result: the gate now runs to completion and reports the real backlog

With the crash fixed, `:app:ktlintMainSourceSetCheck` completes and reports (does not
crash on) the actual, previously-invisible violation backlog:

- **84 of 86 files** under `app/src/main` have at least one violation.
- **~5,540 violation lines** in the report, dominated by
  `standard:trailing-comma-on-call-site` (1065), `standard:no-multi-spaces` (1064),
  `standard:argument-list-wrapping` (672 — the same rule that crashed on the two now-fixed
  sites, firing normally everywhere else), `standard:multiline-expression-wrapping` (667),
  `standard:function-signature` (282), `standard:indent` (234),
  `standard:function-naming` (184), `standard:trailing-comma-on-declaration-site` (164).

This is not a regression introduced by this change — it's the audit's finding 2 made
concrete: `ktlintCheck` was never actually linting this code, so this backlog has existed
silently the whole time. Fixing it is out of scope for this change (which is scoped to
"make the gate real"); it's a large, separate cleanup effort, likely `ktlintFormat`
-autocorrectable for the bulk of it (spacing/comma/indent rules), with manual review for
naming rules.

## Follow-up: closing the backlog and making the gate green (same change)

The ~5,540-line backlog above was not left as a standing failure. `./gradlew
ktlintFormat` auto-corrected the large majority of it (trailing commas, spacing,
indentation, wrapping). What remained after formatting split into three buckets,
handled as follows:

**Fixed by hand (non-semantic edits):**
- 37 `standard:discouraged-comment-location` violations — inline trailing comments on
  parameter/argument lines (e.g. `val platform: String, // "android", "web", or "ios"`)
  moved to their own line above the parameter. Comment content unchanged.
- 2 `standard:max-line-length` violations — `DashboardScreen.kt` (a ternary-style
  `if`/`else` wrapped into a block) and `ChannelViewModel.kt` (an `?:` chain split
  across lines).
- 1 `standard:no-consecutive-comments` — a stale, orphaned KDoc block in
  `MainActivity.kt` (documented a function that was no longer adjacent to it) was
  removed rather than reflowed, since keeping it would have left misleading
  documentation in place.
- 1 `standard:function-naming` — `TransactionViewModel.CreateTransaction` renamed to
  `createTransaction` (camelCase; it is a plain ViewModel method, not a `@Composable`).
  Only two call sites existed (`SMSMessageProcessor.kt`, `AddTransactionScreen.kt`),
  both updated in the same commit.
- 1 `standard:filename` — `SMSTypes.kt` renamed to `MessageSender.kt` to match its
  single top-level declaration (`object MessageSender`). Pure file rename; Kotlin
  resolves imports by symbol, not filename, so no other file needed a change.
- The `no-trailing-spaces`/`function-start-of-body-spacing` violations reported before
  formatting were no longer present after the auto-format pass.

**Deliberately disabled via `.editorconfig` (not fixed), each scoped and justified:**
- `ktlint_standard_no-wildcard-imports = disabled` (repo-wide, `[*.{kt,kts}]`) — of the
  93 wildcard-import violations found, 91 were `androidx.compose.*` /
  `androidx.navigation.compose.*` package imports (`foundation.layout.*`,
  `material3.*`, `runtime.*`, `animation.*`, `ui.*`, etc.) across ~25 Compose screen
  files. This is the standard, idiomatic Compose import style (these packages expose
  hundreds of small top-level functions each); expanding every file to explicit
  imports by hand, with no IDE symbol resolution available to verify completeness in
  this environment, was high-risk (silent missing-import breakage) for no readability
  gain. Verified against the existing code style per this ADR's own bar for disabling
  a rule: pervasive, pre-existing, and idiomatic — not sloppiness.
- `ktlint_standard_property-naming = disabled`, scoped to
  `app/src/main/java/cc/dlabs/pesamind/core/theme/*.kt` only — all 16 violations were
  `Spacing`/`Radius` design-token `const val`s (`Space4`, `Medium`, …) in `Color.kt`,
  intentionally PascalCase to match `Color`'s own token style, not
  SCREAMING_SNAKE_CASE. These are the exact tokens root `CLAUDE.md`'s UI section
  requires screens to reuse ("use Spacing/Radius/theme colors"); renaming them to
  satisfy the rule would ripple across every screen that reads a token, for a
  same-file-only naming rule with no cross-project ambiguity risk. Scoped to the
  `core/theme/` directory rather than disabled repo-wide, so the rule still applies
  everywhere else.
- Already present from the initial gate-enabling work: `ktlint_function_naming_ignore_when_annotated_with = Composable` —
  exempts `@Composable` PascalCase functions from `standard:function-naming` (this is
  what the crash-inducing files like `DashboardScreen.kt` needed; 184 of the original
  violations were this).

## Version catalog migration (same change)

Moved the ktlint plugin into `gradle/libs.versions.toml`
(`[versions] ktlint-plugin = "12.1.0"`, `[plugins] ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint-plugin" }`)
and replaced the raw `id("org.jlleitschuh.gradle.ktlint") version "12.1.0"` literal in
both `build.gradle.kts` (root) and `app/build.gradle.kts` with
`alias(libs.plugins.ktlint)`. Root `build.gradle.kts` applies the plugin directly (no
`apply false`) — verification showed this is intentional and load-bearing, not a
classpath-only declaration: it's what makes `ktlintKotlinScriptCheck` lint the root
`.kts` build/settings scripts. That behavior was preserved as-is; only the plugin
declaration mechanism changed. `hilt`, `google-services`, and `kotlin-kapt` remain raw
`id(...)` literals — deliberately out of scope for this change, flagged as a follow-up.

## Consequences

- `./gradlew ktlintCheck` is green on a clean tree as of this change (verified with
  `./gradlew clean ktlintCheck --continue`, no cached tasks). `./gradlew assembleDebug`
  still succeeds. This closes the red interval the audit found: the plugin is now both
  applied to `:app` *and* actually passing, in the same commit — there was never a
  point in git history where the gate was enabled but broken.
- Two rules are now disabled by `.editorconfig`, one repo-wide
  (`no-wildcard-imports`) and one scoped to `core/theme/`
  (`property-naming`) — see rationale above. Any future PR that wants to
  re-enable either should re-check whether the underlying convention (Compose
  wildcard imports, PascalCase design tokens) has actually changed, not just
  flip the flag.
- No `subprojects{}` block was introduced; if a second Gradle module is ever added to
  this repo, the plugin will need to be applied there too (or refactored into a shared
  convention plugin) — not done preemptively since only `:app` exists today.
