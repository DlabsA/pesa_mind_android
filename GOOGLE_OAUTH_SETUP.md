# Google OAuth Setup Guide

This guide documents the platform-specific Google OAuth setup:
- Android app uses the Android OAuth client
- Web app uses the Web OAuth client
- iOS app uses the iOS OAuth client

## 1) Client ID Matrix

Use a different OAuth client per platform in the same Google Cloud project.

| Platform | OAuth client type | Used by | Example usage |
| --- | --- | --- | --- |
| Android | Android | Android app + backend validation path for Android requests | package name + SHA-1 based |
| Web | Web application | Web frontend + backend validation path for web requests | JS/web sign-in and server exchange |
| iOS | iOS | iOS app + backend validation path for iOS requests | bundle-id based |

## 2) Google Cloud Console Setup

1. Open <https://console.cloud.google.com>.
2. Select the OAuth project used by all clients.
3. Enable Google Identity / Sign-In related APIs.
4. Configure OAuth consent screen (Testing/Production, test users, branding).

Create credentials:

### Android OAuth client
- Application type: `Android`
- Package name: `cc.dlabs.pesamind`
- SHA-1: `B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03`

### Web OAuth client
- Application type: `Web application`
- Add allowed origins/redirect URIs for your web app.

### iOS OAuth client
- Application type: `iOS`
- Add iOS bundle id for the iOS app.

## 3) Backend Configuration

Store all three client IDs in backend environment/config:

```dotenv
GOOGLE_OAUTH_ANDROID_CLIENT_ID=your-android-client-id.apps.googleusercontent.com
GOOGLE_OAUTH_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
GOOGLE_OAUTH_IOS_CLIENT_ID=your-ios-client-id.apps.googleusercontent.com
```

Backend rule:
- For Android requests, validate against `GOOGLE_OAUTH_ANDROID_CLIENT_ID`
- For Web requests, validate against `GOOGLE_OAUTH_WEB_CLIENT_ID`
- For iOS requests, validate against `GOOGLE_OAUTH_IOS_CLIENT_ID`

## 4) Android App Configuration

This Android project reads `.env` during Gradle build for Android client ID.

Example `.env`:

```dotenv
GOOGLE_ANDROID_CLIENT_ID="your-android-client-id.apps.googleusercontent.com"
```

Then build:

```zsh
cd "/Users/conradkash/Github/dlabs/pesa_mind_android"
./gradlew clean :app:assembleDebug
```

## 5) Backend Contract (Platform-Aware)

Recommended request shape for a unified endpoint:

```json
{
  "platform": "android",
  "email": "user@gmail.com",
  "google_id": "110169214549386730370",
  "display_name": "User Name",
  "profile_photo": "https://..."
}
```

If backend receives an ID token in some clients, it should choose expected audience by `platform`:
- `android` -> Android OAuth client
- `web` -> Web OAuth client
- `ios` -> iOS OAuth client

## 6) Common Error: Status Code 10

`ApiException: 10` usually means Google configuration mismatch.

Check in order:
1. Correct platform OAuth client is used.
2. Android package and SHA-1 exactly match Google Cloud.
3. OAuth consent screen has required test users.
4. App uses credentials from the correct Google project.
5. Wait a few minutes after console edits for propagation.

## 7) Quick Verification Commands

Check signing SHA-1 used by the built APK:

```zsh
"$HOME/Library/Android/sdk/build-tools/36.1.0/apksigner" verify --print-certs \
"/Users/conradkash/Github/dlabs/pesa_mind_android/app/build/outputs/apk/debug/app-debug.apk"
```

Check generated Android client in BuildConfig:

```zsh
cat \
"/Users/conradkash/Github/dlabs/pesa_mind_android/app/build/generated/source/buildConfig/debug/cc/dlabs/pesamind/BuildConfig.java"
```

## 8) Notes

- Android, Web, and iOS clients must live in the same intended Google project.
- Do not reuse one client ID across all platforms.
- Keep secrets and client IDs in environment/config, not hardcoded in source.
