# Google Sign-In Status Code 10 Troubleshooting Guide

## Issue Summary
You're receiving `ApiException: status code=10` when attempting Google Sign-In on Android. This indicates a configuration mismatch between your app and Google Cloud Console.

## Root Cause: Missing google-services.json
**FIX IMPLEMENTED**: The `google-services.json` file was missing. Without this file, Android's Play Services library cannot determine which OAuth client to use, resulting in error code 10.

### What Changed
1. ✅ Created `app/google-services.json` with your Android OAuth client configuration
2. ✅ Added Google Services plugin (`com.google.gms.google-services`) to Gradle build
3. ✅ Verified app package name: `cc.dlabs.pesamind`
4. ✅ Verified SHA-1 fingerprint: `B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03`

## Verification Checklist

### Step 1: Verify Google Cloud Console Configuration
Your Android OAuth client MUST have:
- **Package Name**: `cc.dlabs.pesamind`
- **SHA-1 Fingerprint**: `B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03`
- **OAuth Client ID**: `884168293120-ntru34a3u3cg6b9o7du4mi6jada02f80.apps.googleusercontent.com`

**How to verify**:
1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Select project: **pesa-mind** (Project ID: `pesa-mind-884168293120`)
3. Navigate to **APIs & Services** → **Credentials**
4. Find the Android OAuth 2.0 credential for package `cc.dlabs.pesamind`
5. Click to expand and verify the SHA-1 fingerprint matches exactly

### Step 2: Verify OAuth Consent Screen
Your OAuth consent screen MUST be configured:
1. Go to **APIs & Services** → **OAuth Consent Screen**
2. Verify status is **Published** (not Draft)
3. Ensure your test account email is added to the test users list
4. Verify the following scopes are requested:
   - `profile` (email, display name, profile picture)
   - `email`

### Step 3: Verify Google Sign-In API is Enabled
1. Go to **APIs & Services** → **Library**
2. Search for **Google Sign-In API**
3. Ensure it's **ENABLED**

### Step 4: Verify google-services.json
File location: `app/google-services.json`
```json
{
  "project_info": {
    "project_id": "pesa-mind-884168293120",
    "project_number": "884168293120"
  },
  "client": [
    {
      "client_info": {
        "android_client_info": {
          "package_name": "cc.dlabs.pesamind",
          "certificate_hash": [
            "b3aaba5ebab97a8231283838d783f6a093e046fc03"
          ]
        }
      },
      "oauth_client": [
        {
          "client_id": "884168293120-ntru34a3u3cg6b9o7du4mi6jada02f80.apps.googleusercontent.com",
          "client_type": 1
        }
      ]
    }
  ],
  "configuration_version": "1"
}
```

## Build and Deploy

### Rebuild the app
```bash
./gradlew clean :app:assembleDebug
```

The build should:
- ✅ Process `app/google-services.json` file
- ✅ Generate `GoogleServices.json` resource for the app
- ✅ Include OAuth client ID in app resources
- ✅ Complete successfully

### Generate Release APK (for testing)
```bash
./gradlew :app:assembleRelease
```

APK location: `app/release/app-release.aab` or `app/build/outputs/apk/debug/app-debug.apk`

## Testing Google Sign-In

### Quick Test
1. Rebuild app: `./gradlew clean :app:assembleDebug`
2. Install on test device: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Open app and tap "Sign in with Google"
4. Select test account (the one in your OAuth consent screen test users)
5. Should sign in successfully without error 10

### Expected Flow
```
1. Tap Google Sign-In button
2. Google Sign-In dialog opens
3. Select test account or enter credentials
4. Redirect to app (no errors)
5. Extract email, google_id, display_name, profile_photo
6. Send to backend POST /auth/google/mobile-signin
7. Backend validates and returns auth tokens
```

### Log Inspection
When sign-in succeeds, you should see in logcat:
```
D/GoogleSignInManager: Initializing Google Sign-In client (platform-specific OAuth flow for Android)
D/GoogleSignInManager: Processing Google Sign-In result...
D/GoogleSignInManager: Extracted account: email=your.test@gmail.com, hasGoogleId=true
```

## If Status Code 10 Still Persists

### Common Causes
1. **SHA-1 mismatch**: The SHA-1 in Google Cloud Console doesn't match `B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03`
2. **Package name mismatch**: Make sure it's exactly `cc.dlabs.pesamind` (case-sensitive)
3. **OAuth credential type wrong**: Must be "Android" type, not "Web" or other types
4. **Android not in allowed platforms**: Some OAuth clients are restricted to Web only
5. **google-services.json still missing**: Verify file exists at `app/google-services.json`

### Debug Steps
1. Verify keystore SHA-1:
   ```bash
   keytool -list -v -keystore ~/.android/my-release-key.keystore -storepass K@sh404730
   ```

2. Verify generated resources include OAuth client ID:
   ```bash
   find app/build -name "R.java" -o -name "GoogleServices.json" | head -5
   ```

3. Check app logs for exact error:
   ```bash
   adb logcat | grep GoogleSignInManager
   ```

## Architecture Notes

### Google-Services.json Purpose
- Tells Android Play Services which OAuth credentials to use
- Contains package name, SHA-1 fingerprint, OAuth client ID
- Generated from Firebase/Google Cloud Console configuration
- Processed by `com.google.gms.google-services` Gradle plugin

### Android-Client-Only Flow
Your app uses the **Android-client-only OAuth flow**:
1. App uses `GOOGLE_ANDROID_CLIENT_ID` (Android OAuth client)
2. Google Sign-In validates with Android client credentials (package name + SHA-1)
3. App extracts account identity fields (no ID token)
4. Sends account details to backend `/auth/google/mobile-signin`
5. Backend validates account and returns app tokens

**NOT** using:
- ❌ ID token verification
- ❌ Web OAuth client credentials
- ❌ Backend `/verify-token` endpoint

## Configuration Files

### Files Modified/Created
- ✅ `app/google-services.json` - **CREATED** with Android OAuth client config
- ✅ `build.gradle.kts` - Added Google Services plugin dependency
- ✅ `app/build.gradle.kts` - Added Google Services plugin application
- ✅ `.env` - Already has `GOOGLE_ANDROID_CLIENT_ID`

### Environment Variables (Backend)
Backend needs to have in `.env`:
```
GOOGLE_OAUTH_ANDROID_CLIENT_ID=884168293120-ntru34a3u3cg6b9o7du4mi6jada02f80.apps.googleusercontent.com
```

This allows backend to validate that the OAuth client ID came from your registered Android app.

## Next Steps

1. **Verify Google Cloud Console** - Follow Steps 1-3 in Verification Checklist
2. **Rebuild and Deploy** - Run `./gradlew clean :app:assembleDebug`
3. **Test Google Sign-In** - Tap button and verify no error 10
4. **Check Backend** - Ensure `/auth/google/mobile-signin` endpoint is implemented and running
5. **End-to-End Test** - Sign in → extract account data → send to backend → get tokens

## Support

If you still encounter issues:
1. Verify all config in Google Cloud Console
2. Check `adb logcat | grep -i "google\|oauth"` for detailed errors
3. Ensure `app/google-services.json` file exists and is valid JSON
4. Verify `.env` and backend `.env` have correct OAuth client IDs
5. Check that backend `/auth/google/mobile-signin` endpoint is running

