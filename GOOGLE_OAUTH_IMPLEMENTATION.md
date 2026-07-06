# Google OAuth Implementation

This document defines the backend/client implementation model where each platform uses its own Google OAuth client:
- Android -> Android OAuth client
- Web -> Web OAuth client
- iOS -> iOS OAuth client

## Architecture Summary

### Client responsibilities
- Android app signs in with Google account selection flow.
- Web app signs in with Google web flow.
- iOS app signs in with Google iOS flow.
- Each client sends platform context to backend.

### Backend responsibilities
- Accept Google auth payload from each platform.
- Select expected OAuth audience/client by `platform`.
- Validate payload against that platform's client configuration.
- Return app tokens/profile or `is_new_user=true` onboarding flow.

## Platform Client Mapping

| Platform | Backend expected client env | Notes |
| --- | --- | --- |
| `android` | `GOOGLE_OAUTH_ANDROID_CLIENT_ID` | Must match package + SHA-1 setup |
| `web` | `GOOGLE_OAUTH_WEB_CLIENT_ID` | Must match web origin/redirect setup |
| `ios` | `GOOGLE_OAUTH_IOS_CLIENT_ID` | Must match iOS bundle id setup |

## Recommended Backend Endpoints

### 1) Platform sign-in
`POST /api/v1/auth/google/platform-signin`

Request example:

```json
{
  "platform": "android",
  "email": "user@gmail.com",
  "google_id": "110169214549386730370",
  "google_display_name": "User Name",
  "google_profile_photo": "https://..."
}
```

Response (existing user):

```json
{
  "access_token": "jwt_access",
  "refresh_token": "jwt_refresh",
  "is_new_user": false,
  "profile": {
    "id": "user_123",
    "username": "john_doe",
    "type": "user",
    "balance": 5000.0
  }
}
```

Response (new user):

```json
{
  "access_token": null,
  "refresh_token": null,
  "is_new_user": true,
  "profile": null
}
```

### 2) Complete signup
`POST /api/v1/auth/google/complete-signup`

Request example:

```json
{
  "email": "user@gmail.com",
  "google_id": "110169214549386730370",
  "username": "john_doe",
  "google_display_name": "John Doe",
  "google_profile_photo": "https://..."
}
```

### 3) Username check
`POST /api/v1/auth/google/check-username`

Request example:

```json
{
  "username": "john_doe"
}
```

## Android Project Notes (Current)

The Android app currently:
- uses Google sign-in account selection
- sends Google account identity fields to backend mobile/google sign-in flow
- does not rely on `requestIdToken(...)`

Related files:
- `app/src/main/java/cc/dlabs/pesamind/features/auth/GoogleSignInManager.kt`
- `app/src/main/java/cc/dlabs/pesamind/features/auth/AuthViewModel.kt`
- `app/src/main/java/cc/dlabs/pesamind/features/auth/GoogleAuthRepository.kt`
- `app/src/main/java/cc/dlabs/pesamind/core/network/ApiService.kt`
- `app/src/main/java/cc/dlabs/pesamind/core/network/ApiModels.kt`

## Backend Validation Logic (Pseudo)

```text
if platform == "android":
  expected_client = GOOGLE_OAUTH_ANDROID_CLIENT_ID
elif platform == "web":
  expected_client = GOOGLE_OAUTH_WEB_CLIENT_ID
elif platform == "ios":
  expected_client = GOOGLE_OAUTH_IOS_CLIENT_ID
else:
  reject("unsupported platform")

validate_google_payload_against(expected_client)
issue_or_lookup_user_tokens()
```

## Error 10 Troubleshooting

If Android still sees `ApiException: 10`:
1. Confirm Android OAuth credential has package `cc.dlabs.pesamind`.
2. Confirm SHA-1 is `B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03`.
3. Confirm credential is in the intended Google project.
4. Confirm OAuth consent screen includes test account (if in Testing mode).
5. Rebuild/reinstall and retry after propagation delay.

## Security Notes

- Keep OAuth client IDs in config/env, not hardcoded constants.
- Keep backend-side verification strict by platform.
- Log platform and credential selection path for debugging (without exposing secrets).
- Add rate limits and replay protections on auth endpoints.

## Minimal Backend Env Example

```dotenv
GOOGLE_OAUTH_ANDROID_CLIENT_ID=android-client-id.apps.googleusercontent.com
GOOGLE_OAUTH_WEB_CLIENT_ID=web-client-id.apps.googleusercontent.com
GOOGLE_OAUTH_IOS_CLIENT_ID=ios-client-id.apps.googleusercontent.com
```
