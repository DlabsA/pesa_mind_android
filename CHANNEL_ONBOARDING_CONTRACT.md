# Channel Onboarding — API Contract

Reference for the post-signup, batch channel-onboarding flow. Backend: `pesa-mind`
(`~/Github/Personal/pesa-mind`, `origin/main`). Android client implementation:
`features/onboarding/` in this repo. Written for the iOS phase to implement against the same
contract.

## Summary of the feature

Registration no longer auto-creates any channels. Instead, the client collects channel choices
across a post-signup onboarding flow (skippable per screen, one-shot) and creates them all in a
single batched call. Free tier allows up to 4 channels total. A user-scoped
`channels_onboarded` flag (server: `User.ChannelsOnboardedAt`, nullable timestamp; client:
`TokenManager.isChannelsOnboarded()`, local DataStore boolean) tracks completion — **never**
derived from channel count, since finishing with 0 channels is valid.

## Endpoints

### `POST /api/v1/categories/batch` (new)

Auth required (JWT). Creates every item in the request in one all-or-nothing transaction and
marks the user onboarded.

**Request:**
```json
{
  "channels": [
    {
      "id": "optional-client-uuid",
      "name": "Cash",
      "channel_type": "Cash",
      "channel_desc": "",
      "description": "",
      "status": true,
      "account_number": null,
      "opening_balance": 50000
    }
  ]
}
```
`channels` may be an empty array — finishing onboarding with 0 channels is valid and still marks
the user onboarded.

**Response — `200 OK`** for both a first-time create and an idempotent replay:
```json
{
  "already_onboarded": false,
  "channels": [ /* ChannelDetailsResponse[], see below */ ]
}
```

**Idempotency:**
- Batch-level: if the user is already onboarded (`ChannelsOnboardedAt` set) when the request
  arrives, the server does **zero writes** and returns the user's existing channels with
  `already_onboarded: true`. Safe to retry the exact same request any number of times.
- Item-level: each item's optional `id` is a client-generated UUID used the same way as the
  single-create endpoint's `id` — `ON CONFLICT (id) DO NOTHING`, reusing the existing row if
  that id already exists rather than erroring or duplicating.

**Errors** (see error-code table below): `403 CHANNEL_LIMIT_EXCEEDED`,
`400 INVALID_CHANNEL_TYPE`, `422 CHANNEL_DESC_REQUIRED`, `422 ACCOUNT_NUMBER_BLANK`,
`422 INVALID_OPENING_BALANCE`. Any single item's validation failure rolls back the **entire**
batch (no partial creates), and `channels_onboarded_at` stays unset.

### `POST /api/v1/categories` (existing, extended)

Single-channel create — kept for adding channels after onboarding (still subject to the ≤4
limit). Now accepts the same two new optional fields as the batch endpoint:
`account_number` (string, optional) and `opening_balance` (number, optional, defaults to 0).
`channel_desc` is no longer required for `Cash` (still required for `MobileMoney`/`Bank`).

### `POST /users/register`, `POST /auth/login`, `POST /auth/refresh`, Google OAuth endpoints

No behavior change beyond one new response field — see below. Register still returns no auth
tokens (client must call `/login` separately). No auto-created channels on register or on new
OAuth user creation.

## New/changed fields

### `ChannelDetails` (channel model, both request and response)

| Field | Type | Notes |
|---|---|---|
| `account_number` | `string \| null`, optional | Shared field: a phone number for `MobileMoney`/Airtel channels, a bank account number for `Bank` channels. **Optional for every channel type** — no per-type required/forbidden enforcement, no format validation beyond "not blank if present." |
| `opening_balance` | `number \| null`, optional (request only) | Sets the channel's initial `available_balance` (normally 0). Response still reports this as `available_balance`, not a separate field. |

### `channels_onboarded` (new, on every auth-response `profile` object)

Present in `UserResponse.profile` (Register), `LoginResponse.profile` (Login/Refresh), and every
OAuth response's `profile` (`OAuthLoginResponse`, `CompleteGoogleSignupResponse`, etc.) —
`bool`, `true` once the user has completed (or explicitly skipped) onboarding.

```json
{ "profile": { "...": "...", "channels_onboarded": true } }
```

## Free-tier limit

`category.FreeTierChannelLimit = 4` (plain constant, Go: `internal/domain/category/service.go`).
Enforced as `existing_count + batch_size > 4` → `403 CHANNEL_LIMIT_EXCEEDED`, both in the batch
endpoint and in the single-create endpoint (so single-create can't be used to bypass the batch
limit after onboarding). Not per-type slots — a paid tier just needs a different limit.

## Per-`ChannelType` validation

| | Cash | MobileMoney | Bank |
|---|---|---|---|
| `channel_type` valid enum (`Cash`/`MobileMoney`/`Bank`) | required | required | required |
| `channel_desc` non-empty | not required | required | required |
| `account_number` | optional | optional | optional |
| `opening_balance` | optional, `>= 0` | optional, `>= 0` | optional, `>= 0` |

`channel_desc` is free text server-side — not matched against a fixed provider/bank list (that
list only exists client-side, e.g. `ChannelDescBank`/`ChannelDescMobileMoney` in this repo's
`features/settings/channels/ChannelTypes.kt`).

## Error codes

All error responses are `{"error": "<message>", "code": "<CODE>"}` — `code` is present only for
the typed errors below; other failures (auth, not-found, generic 500s) omit it and keep the
existing `{"error": "..."}` shape.

| HTTP | `code` | Meaning |
|---|---|---|
| 403 | `CHANNEL_LIMIT_EXCEEDED` | Would exceed the free-tier channel limit |
| 400 | `INVALID_CHANNEL_TYPE` | `channel_type` not one of `Cash`/`MobileMoney`/`Bank` |
| 422 | `CHANNEL_DESC_REQUIRED` | `channel_desc` missing for MobileMoney/Bank |
| 422 | `ACCOUNT_NUMBER_BLANK` | `account_number` present but blank/whitespace-only |
| 422 | `INVALID_OPENING_BALANCE` | `opening_balance` present and negative |

## Client-side gating (Android reference; iOS should mirror the same rules)

- Local flag: `TokenManager.isChannelsOnboarded()` / `setChannelsOnboarded(Boolean)` — plain,
  non-secret DataStore boolean, mirrors the existing `LockState` pattern.
- **Cold start** (`NavGraph`): if logged in and no PIN/pattern lock is pending, route to
  onboarding when the local flag is unset, else Dashboard.
- **Post-login/signup** (`AuthViewModel.resolvePostAuthDestination()`): same rule, evaluated
  right after tokens are saved — covers email login, existing-user Google sign-in, and new-user
  Google sign-up (all three previously computed this independently; now one shared function).
- **Server-true-wins sync**: on every successful login/Google sign-in, if
  `profile.channels_onboarded == true`, set the local flag to `true`. Never clears an
  already-`true` local flag back to `false` — covers (a) finishing onboarding while offline,
  before the flag-sync call has landed, and (b) a reinstall logging into an already-onboarded
  account.
- **Offline-first finish**: the onboarding flow does local, sequential
  `ChannelRepository.createChannel(...)` calls (each already offline-safe via the existing
  outbox), sets the local flag immediately after, then fires a best-effort
  `POST /categories/batch` purely to flip the server-side flag — retried implicitly by the
  server-true-wins sync above on the next login if it fails while offline. Safe to resend with
  the same items (or an empty list) since items are idempotent via client-id.

## Files (backend, `pesa-mind`, for reference)

- `internal/domain/category/model.go`, `validation.go`, `errors.go`, `service.go`
- `internal/domain/onboarding/model.go`, `service.go`
- `internal/domain/user/model.go` (`ChannelsOnboardedAt`)
- `internal/interfaces/http/dto/category_dto.go`, `onboarding_dto.go`, `user_dto.go`, `mappers.go`
- `internal/interfaces/http/handlers/category_handler.go`, `onboarding_handler.go`, `error_helpers.go`
- `cmd/api/main.go` (route registration), `internal/infrastructure/setup/dependencies.go` (DI)
