# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Pesa Mind — a single-module Android app (`cc.dlabs.pesamind`) written in Kotlin/Jetpack Compose. Its core feature is
reading incoming SMS (M-Pesa/bank transaction alerts), parsing them, and auto-creating transactions, combined with
budgeting/analytics screens on top.

## Commands

Onboarding: install the repo's git hooks once after cloning (a pre-commit hook blocks direct commits to `main`):
```bash
./.githooks/install-hooks.sh
```

Build / lint / test (from repo root, uses the Gradle wrapper):
```bash
./gradlew assembleDebug             # build debug APK
./gradlew ktlintCheck               # lint (ktlint via org.jlleitschuh.gradle.ktlint)
./gradlew ktlintFormat              # auto-format Kotlin sources
./gradlew test                      # run JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "cc.dlabs.pesamind.features.auth.GoogleOAuthTest"   # single test class
./gradlew testDebugUnitTest --tests "cc.dlabs.pesamind.features.auth.GoogleOAuthTest.someMethod"  # single test method
./gradlew connectedAndroidTest      # instrumented tests (app/src/androidTest), needs a device/emulator
```

There is no CI config in this repo — `ktlintCheck`/`test` are the closest thing to a verification gate before pushing.

## Build configuration notes

- `GOOGLE_ANDROID_CLIENT_ID` is resolved in `app/build.gradle.kts` in this order: Gradle property → env var → `.env`
  file at repo root (`KEY=value` format, not committed) → hardcoded fallback. Same pattern to follow if adding more
  build-time secrets.
- Release and debug builds both sign with the same keystore at `~/.android/my-release-key.keystore` (path/passwords
  hardcoded in `app/build.gradle.kts`); `isMinifyEnabled` is off.
- `BASE_URL` for the API is hardcoded in `ApiClient.kt` (`https://api.dlabs.cc/api/v1/`), not build-variant driven.

## Architecture

### Package layout
- `core/` — cross-feature infrastructure:
  - `di/` — Hilt modules (`NetworkModule` binds `ApiService`/`NetworkMonitor`). Most singletons below are **not**
    Hilt-injected; they're plain Kotlin `object`s reached via static access.
  - `network/` — `ApiClient` (Retrofit/OkHttp singleton with token-auth + `TokenRefreshInterceptor` + logging
    interceptors, in that order), `ApiService` (Retrofit interface), `ApiModels`, `network/analytics/*` response DTOs.
  - `storage/` — DataStore-backed singleton "manager" objects, one per domain: `TokenManager` (JWT + PIN/pattern lock
    state), `AccountManager`, `BudgetManager`, `ChannelManager`, `NotificationStorage`, `TransactionManager`,
    `ThemeManager`, `StreakSessionCache`, `SyncPolicy`. These are the source of truth for local persistence — most
    screens read/write through them directly rather than through a repository layer.
  - `coordinator/` — `UnifiedStateCoordinator` + `UnifiedViewModel` (see below).
  - `navigation/` — `NavGraph.kt` (single `NavHost`, all routes registered here) + `Routes.kt` (route string
    constants). Start destination is picked at runtime from `TokenManager.getLockState()`/`isLoggedIn()`.
  - `theme/`, `ui/` — Compose theme and shared composables (`UnifiedScreenHeader`, `DetailScreenTopBar`, etc.).
- `features/<feature>/` — one package per feature area: `auth`, `dashboard`, `home`, `budgets`, `transactions`,
  `analytics`, `common`, and `settings/{account,channels,notifications,security}`. Each typically holds a
  Compose screen + a `ViewModel` + feature-local types/components in flat files (no further sub-layering).

### Cross-ViewModel state sync
The app uses a custom pub/sub layer instead of a shared repository/cache for keeping ViewModels in sync:
- `StateEvent` (`core/coordinator/StateEvent.kt`) — sealed class of app-wide events (`TransactionCreated`,
  `ChannelUpdated`, `UserLoggedOut`, etc.).
- `UnifiedStateCoordinator` — singleton `MutableSharedFlow` event bus (no replay, buffered).
- `UnifiedViewModel` — base class ViewModels extend to auto-subscribe to the bus and get a `publishEvent()` helper;
  override `onStateEvent()` to react to events published by other ViewModels (e.g. Dashboard/Analytics refreshing
  after Transaction/Channel changes). Not every ViewModel has been migrated to this base class yet — check whether a
  given ViewModel extends `UnifiedViewModel` or plain `ViewModel()` before assuming events will reach it. Full
  usage guide: `UNIFIED_VIEWMODEL_GUIDE.md`.

### SMS transaction ingestion pipeline
Background SMS monitoring is a core, permission-heavy subsystem living in `features/settings/notifications/`:
`BootReceiver` (starts monitoring on device boot) → `MessageMonitoringService` (persistent foreground service,
`START_STICKY`) → `SmsReceiver` (`SMS_RECEIVED` broadcast) → `SMSMessageProcessor` (parses SMS body into a
transaction). All of these plus the manifest permissions (`RECEIVE_SMS`, `READ_SMS`, `FOREGROUND_SERVICE*`,
`RECEIVE_BOOT_COMPLETED`) need to stay consistent when touched — see `BACKGROUND_SMS_MONITORING.md` and
`BACKGROUND_MONITORING_QUICK_REFERENCE.md` for the full flow and manifest wiring.

### Auth
- Two parallel auth paths: username/password (`AuthViewModel`) and Google Sign-In (`GoogleSignInManager` +
  `GoogleAuthRepository`), both funneling into `TokenManager` for JWT storage. See
  `ANDROID_PLATFORM_OAUTH_INTEGRATION.md` / `GOOGLE_OAUTH_IMPLEMENTATION.md` for the OAuth-specific flow.
- App-lock is separate from login: `TokenManager.LockState` (NONE/PIN/PATTERN) drives which screen
  (`PinUnlockScreen`/`PatternUnlockScreen`/dashboard) `NavGraph` opens to on launch, independent of whether the user
  has a valid JWT.

### DI
Hilt (`@HiltAndroidApp` app class `PesaMindApp` is defined inside `MainActivity.kt`, not its own file). Only
network-layer types are currently provided via Hilt modules; storage managers and `ApiClient` itself are accessed as
singleton objects, not injected — follow the existing pattern in a given file rather than introducing DI
inconsistently.

## Git workflow
- `main` is protected by a local pre-commit hook (`.githooks/pre-commit`) that refuses commits made directly on
  `main` — always work on a feature branch (`git switch -c feature/...`). The hook only takes effect after running
  `./.githooks/install-hooks.sh` (also runs automatically on common Gradle tasks).
