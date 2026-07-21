# Architecture audit — Pesa Mind Android

Generated 2026-07-19 by manual application of the `android-auditor` agent methodology
(`.claude/agents/android-auditor.md`) — the agent definition exists but project agents
only load at session start, so this pass was run directly rather than dispatched; a
future `/audit` run should reproduce and refresh this. Backed by `graphify-out/graph.json`
(837 nodes / 751 edges, rebuilt this session — copy at `docs/vault/graphify/`) plus direct
`grep`/`git log` verification of every claim below. Ranked by **impact × how often the
path is touched** (touch-frequency = commit count on the file, via `git log --oneline`).

## Entry points

- `MainActivity.kt` (`app/src/main/java/cc/dlabs/pesamind/MainActivity.kt`) — hosts
  `PesaMindApp` (the `@HiltAndroidApp` class is defined inside this file, not its own
  file, per root `CLAUDE.md`), sets the Compose content root, calls
  `.showMandatorySettingsDialog()`.
- `core/navigation/NavGraph.kt` — single `NavHost`, all routes registered here. Start
  destination picked at runtime from `TokenManager.getLockState()`/`isLoggedIn()`.
- SMS pipeline entry points (manifest-registered): `BootReceiver` →
  `MessageMonitoringService` (foreground, `START_STICKY`) → `SmsReceiver`
  (`SMS_RECEIVED`) → `SMSMessageProcessor`. `SMSMessageProcessor` is graph god-node #10
  (10 edges) — high blast radius for any change here.

## Layer map (from the graph's community detection)

- Community 20: `MainActivity` + `PesaMindApp` — app entry.
- Community 4/17/19/etc.: each screen's `UiState`/`ViewModel`/phase-sealed-class cluster
  as its own tight community — confirms the one-`UiState`-per-screen convention is
  mostly followed structurally, even where the ViewModel base class isn't (see below).
- `core/network` (`ApiModels.kt`, `ApiService`) dominates Community 0 (75 nodes) and is
  the single most-connected node in the whole graph.

## Most-connected files ("god nodes", per `graphify-out/GRAPH_REPORT.md`)

| Rank | Node | Edges | Note |
|---|---|---|---|
| 1 | `ApiService` | 43 | Expected — single Retrofit interface, every ViewModel/manager depends on it. Not itself a violation. |
| 2 | `BudgetManager` | 22 | `core/storage/` singleton; high fan-in from budgets feature. |
| 3 | `AuthViewModel` | 15 | Plain `ViewModel()`, not `UnifiedViewModel` — see violation below. |
| 4 | `SetMonthlyBudgetViewModel` | 15 | Plain `ViewModel()`. |
| 5 | `YearlyBudgetViewModel` | 15 | Plain `ViewModel()`; file itself is misspelled `YealyBudgetViewModel.kt`. |
| 6 | `ChannelManager` | 12 | `core/storage/` singleton. |
| 7 | `BudgetViewModel` | 12 | Plain `ViewModel()`. |
| 9 | `NotificationStorage` | 10 | `core/storage/` singleton. |
| 10 | `SMSMessageProcessor` | 10 | Core ingestion pipeline — see entry points. |

Four of the top-7 most-connected nodes are ViewModels still on plain `ViewModel()` — the
highest-fan-in code in the app is also the least migrated to the target base class.

## Standards violations, ranked by impact × touch-frequency

### 1. CRITICAL — hardcoded release-signing keystore password committed to `app/build.gradle.kts`

`app/build.gradle.kts:47` and `:49`:
```kotlin
storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "K@sh404730"
keyPassword = System.getenv("KEY_PASSWORD") ?: "K@sh404730"
```
Direct, explicit violation of `.claude/CLAUDE.md`'s Secrets section: *"No secret,
password, or key ever has a literal fallback value in a committed file
(`build.gradle.kts` or otherwise)."* This is the literal example given in that rule.

**This is a live credential exposure, not a style issue.** The password
`K@sh404730` is in the current working tree and confirmed present in git history
(introduced in an earlier commit touching `app/build.gradle.kts`, still there on every
commit since — `app/build.gradle.kts` has 6 commits total). It's the password for the
same keystore (`~/.android/my-release-key.keystore`) that signs **both release and debug
builds** per root `CLAUDE.md`, with `isMinifyEnabled = false`. Anyone with read access to
this repository (or its history, even after a future fix — git history doesn't forget)
has the signing key password.

**Recommendation (not actioned — read-only audit):** rotate the keystore password,
remove the literal fallback (fail the build if `KEYSTORE_PASSWORD`/`KEY_PASSWORD` aren't
set, matching the existing pattern for `GOOGLE_ANDROID_CLIENT_ID`), and treat the current
keystore as potentially compromised if this repo has ever been pushed anywhere
non-private. Flagged to the user directly in-session, not just here.

### 2. HIGH — ktlint lints zero application Kotlin files

`build.gradle.kts:9` applies `org.jlleitschuh.gradle.ktlint` only to the root Gradle
project. No `subprojects{}`/`allprojects{}` block propagates it, and
`app/build.gradle.kts` doesn't apply the plugin itself. Confirmed by running
`./gradlew ktlintCheck --dry-run`: the only tasks that execute are
`ktlintKotlinScriptCheck`/`runKtlintCheckOverKotlinScripts` (root `.kts` build/settings
scripts) — there is no `app`-scoped ktlint task at all. Root `CLAUDE.md` documents
`ktlintCheck` as "lint (ktlint via org.jlleitschuh.gradle.ktlint)" without this caveat.
Every one of the 86 Kotlin source files under `app/src` has never been ktlint-checked by
the command the project's own docs point to. High impact (the lint gate is a no-op for
the code that matters) × very high touch-frequency (every PR touches app code).

### 3. HIGH — 10 of 12 ViewModels still use plain `ViewModel()`, not `UnifiedViewModel`

```
app/src/main/java/cc/dlabs/pesamind/features/settings/security/SetPatternViewModel.kt
app/src/main/java/cc/dlabs/pesamind/features/settings/security/SetPinViewModel.kt
app/src/main/java/cc/dlabs/pesamind/features/settings/account/AccountViewModel.kt
app/src/main/java/cc/dlabs/pesamind/features/settings/security/SecurityViewModel.kt
app/src/main/java/cc/dlabs/pesamind/features/settings/account/ChangePasswordViewModel.kt
app/src/main/java/cc/dlabs/pesamind/features/settings/channels/ChannelViewModel.kt
app/src/main/java/cc/dlabs/pesamind/features/auth/AuthViewModel.kt
app/src/main/java/cc/dlabs/pesamind/features/auth/UnlockViewModel.kt
app/src/main/java/cc/dlabs/pesamind/features/budgets/SetMonthlyBudgetViewModel.kt
app/src/main/java/cc/dlabs/pesamind/features/budgets/YealyBudgetViewModel.kt
```
Only `DashboardViewModel` and `AnalyticsViewModel` extend `UnifiedViewModel`. Root
`CLAUDE.md` already frames plain `ViewModel()` as "legacy debt being paid down, not a
pattern to copy" — but three of the plain-`ViewModel()` files (`AuthViewModel`,
`SetMonthlyBudgetViewModel`, `YearlyBudgetViewModel`) are simultaneously top-7
most-connected nodes in the whole graph, meaning the highest-blast-radius screens are
exactly the ones not receiving cross-screen `StateEvent`s (e.g. a budget change made
elsewhere won't reactively refresh `SetMonthlyBudgetViewModel`/`YearlyBudgetViewModel`
unless they separately re-fetch). `AuthViewModel` also has 3 commits, actively touched.

### 4. MEDIUM-HIGH — Composables calling storage managers directly, bypassing the ViewModel

`.claude/CLAUDE.md`: *"No Composable may call `ApiClient`/`ApiService` or a storage
manager directly — always through a ViewModel."*

- `features/settings/SettingsScreen.kt` (12 commits — actively touched): lines 46, 53,
  88–90, 241 call `ThemeManager.isDarkModeEnabled()`, `AccountManager.getAccount()`,
  `TokenManager.clearTokens()`, `TokenManager.clearLock()`, `AccountManager.clearAccount()`,
  `ThemeManager.setDarkModeEnabled()` directly from the Composable body / lambda.
- `features/budgets/BudgetScreen.kt:109` (2 commits): `AccountManager.getAccount()`
  called directly.

No violations found for `ApiClient`/`ApiService` called directly from a `*Screen.kt` file
— that specific pattern is clean; the leak is manager access, not network access.

### 5. MEDIUM — folder/naming convention violations

- `features/budgets/Setmonthlybudgetscreen.kt` — **this is the literal counter-example
  named in `.claude/CLAUDE.md`'s own naming-convention rule** ("PascalCase, no exceptions
  (e.g. not `Setmonthlybudgetscreen.kt`)"). The rule was written pointing directly at an
  existing file that still hasn't been renamed.
- `features/budgets/YealyBudgetDetailScreen.kt` and
  `features/budgets/YealyBudgetViewModel.kt` — both misspelled "Yealy" instead of
  "Yearly" in the filename (the class names inside are correctly `YearlyBudgetViewModel`
  per the graph, so this is a filename-only typo, but it still breaks the
  `<Feature>Screen.kt`/`<Feature>ViewModel.kt` convention as literally as the point above).

### 6. LOW-MEDIUM — `core/utils/` holds a feature-specific ViewModel and dead empty package

`.claude/CLAUDE.md`: *"Do not add feature-specific ViewModels, screens, or types under
`core/utils/` — `core/` is cross-feature infrastructure only."*

- `core/utils/TransactionViewModel.kt` defines `class TransactionViewModel :
  UnifiedViewModel()` with its own `TransactionState`/`StateFlow` — a screen-facing
  ViewModel, not a utility, sitting directly in the package the rule calls out by name.
  (Ironically it *does* correctly extend `UnifiedViewModel` — it's misplaced, not
  outdated.)
- `core/ads/` exists as an empty directory (no files). Per `.claude/CLAUDE.md`'s
  dead-file rule, empty directories should be deleted on sight (confirm with user first —
  not done here, read-only pass).

### 7. Manual color literals

9 files under `features/**/*Screen.kt` contain `Color(0x...)` literals instead of
`Spacing`/`Radius`/theme colors. Not individually enumerated here — a `/audit` follow-up
scoped just to this rule (`grep -rn "Color(0x" app/src/main/java/.../features
--include="*Screen.kt"`) would produce the file:line list; deprioritized below the
above findings by impact.

### Not violated (checked, clean)

- No `LiveData` usage anywhere in the codebase — `StateFlow`-only is fully honored.
- No Composable found calling `ApiClient`/`ApiService` directly.
- `GOOGLE_ANDROID_CLIENT_ID` resolution in `app/build.gradle.kts:25-27` correctly follows
  the documented Gradle property → env var → `.env` → (see caveat above: this one has no
  literal fallback, unlike the keystore passwords) — the pattern itself is sound; the
  keystore signing config just didn't follow it.

## Follow-ups tracked, not fixed (per user decision, this pass)

- detekt is referenced as a target in `.claude/CLAUDE.md` ("ktlint/detekt config") but
  is not configured anywhere in this repo — no plugin, no `detekt.yml`.
- ktlint's Gradle scope gap (finding 2) needs `app/build.gradle.kts` to apply the
  `org.jlleitschuh.gradle.ktlint` plugin, or a root `subprojects{}` block — an
  application-config change, intentionally not made in this audit pass.
