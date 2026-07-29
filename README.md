# Pesa Mind — Android

Pesa Mind reads incoming SMS (M-Pesa/bank transaction alerts), parses them, and
auto-creates transactions — combined with budgeting and analytics screens on top.
Single-module Kotlin/Jetpack Compose app (`cc.dlabs.pesamind`).

## Architecture, in brief

- **MVVM**: Compose screen → ViewModel → data layer. No screen calls the network or a
  storage manager directly.
- **Offline-first**: Room is the source of truth for channels, transactions, and
  budgets. Every write goes local-first (Room + a durable outbox), synced to the
  backend by a background `SyncWorker`. See
  [`docs/decisions/ADR-0004-offline-first.md`](docs/decisions/ADR-0004-offline-first.md).
- **State**: `StateFlow` only, one `UiState` per screen, cross-screen updates via a
  small pub/sub event bus (`UnifiedViewModel`/`StateEvent`).
- **DI**: Hilt for the network layer; `core/storage/` singletons are accessed directly
  as plain objects, not injected — an intentional, existing pattern to follow, not
  partial migration debt.

Full contract for where the codebase is *going* (enforced every session):
[`.claude/CLAUDE.md`](.claude/CLAUDE.md). Current-state architecture map:
[`CLAUDE.md`](CLAUDE.md). Design history and rationale: [`docs/decisions/`](docs/decisions/).

## Prerequisites

- Android Studio (or the CLI toolchain: JDK 17+, Android SDK, `compileSdk` 36)
- A release keystore, only if you need to produce a signed release build — see
  "Secrets" below; not required for debug builds

## Getting started

```bash
git clone <repo-url>
cd pesa_mind_android
./.githooks/install-hooks.sh   # blocks direct commits to main; also runs on common Gradle tasks
```

### Secrets

Two things are resolved at build time, in this order — Gradle property → env var →
a gitignored local file:

- **`GOOGLE_ANDROID_CLIENT_ID`** (Google Sign-In): `-PGOOGLE_ANDROID_CLIENT_ID=...`,
  an env var, or a `.env` file at the repo root (`GOOGLE_ANDROID_CLIENT_ID=...`).
- **Release signing** (`KEYSTORE_PASSWORD`/`KEY_PASSWORD`/`KEY_ALIAS`/`KEYSTORE_FILE`):
  same resolution order, or a gitignored `keystore.properties` at the repo root
  (`storePassword`/`keyPassword`/`keyAlias`/`storeFile`). Release signing is skipped
  entirely if the password secrets aren't set — you don't need a real keystore to
  build or run debug.

Never commit a real value for either. See `.claude/CLAUDE.md`'s Secrets section for
why this matters here specifically — this repo has had a real credential leak before
(`docs/vault/REMEDIATION.md`).

## Build, lint, test

```bash
./gradlew assembleDebug             # build debug APK
./gradlew ktlintCheck               # lint (ktlint), app-scoped — not just root build scripts
./gradlew ktlintFormat              # auto-format
./gradlew test                      # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest      # instrumented tests (app/src/androidTest), needs a device/emulator
```

There is no CI in this repo — `ktlintCheck`/`test` are the closest thing to a
pre-push gate. Run both before opening a PR; see [`CONTRIBUTING.md`](CONTRIBUTING.md)
for the rest of the workflow.

## Project structure

- `core/` — cross-feature infrastructure: network client, `core/storage/` managers,
  Room database + offline sync (outbox + `SyncWorker`), navigation, theme, shared UI
  components.
- `features/<feature>/` — one package per feature area (`auth`, `dashboard`, `budgets`,
  `transactions`, `analytics`, `settings/{account,channels,notifications,security}`),
  each a Compose screen + ViewModel + feature-local types.

See the root [`CLAUDE.md`](CLAUDE.md) for the full package-by-package map, and
`docs/vault/01-architecture-audit.md` for a snapshot of known structural debt.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md).
