# Google Sign-In Configuration Diagnostic

## Current Configuration

### App Details
- **Package Name:** `cc.dlabs.pesamind`
- **App Version Code:** 15
- **SHA-1 Fingerprint:** `B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03`
- **SHA-256 Fingerprint:** `D2:80:73:CB:A8:3D:72:4D:40:5D:55:BE:C4:22:2F:0B:A6:04:DE:81:A8:94:EA:31:B3:D3:B4:89:47:B9:07:63`

### Google Cloud Configuration
- **Android OAuth Client ID:** `884168293120-cngr633jrrkuq5hcuv0cqv19latmfb9j.apps.googleusercontent.com`
- **Web OAuth Client ID:** must be configured separately as `GOOGLE_WEB_CLIENT_ID`
- **Project ID:** `884168293120`

## Error: ApiException with status code 10

**Status Code 10** means: **Configuration mismatch between your app and Google Cloud Console**

### Checklist to Fix:

1. **Verify Google Cloud Console Credentials:**
   - [ ] Go to https://console.cloud.google.com
   - [ ] Select the correct project: `884168293120`
   - [ ] Navigate to "APIs & Services" → "Credentials"
   - [ ] Find the **Android OAuth client** with Client ID: `884168293120-cngr633jrrkuq5hcuv0cqv19latmfb9j.apps.googleusercontent.com`
   - [ ] Click "Edit" (pencil icon)
   - [ ] In the Android OAuth client, ensure:
     - Package name: `cc.dlabs.pesamind` (should match exactly)
     - Certificate SHA-1: `B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03`
   - [ ] Find the **Web OAuth client** for the same project
   - [ ] Copy its client ID and set it as `GOOGLE_WEB_CLIENT_ID`

2. **Verify OAuth Consent Screen:**
   - [ ] Go to "APIs & Services" → "OAuth consent screen"
   - [ ] Make sure the OAuth consent screen is configured
   - [ ] Your app (`cc.dlabs.pesamind`) should be listed as a Test Application
   - [ ] Status should be "In Production" or "Testing" (not "None")

3. **Verify Google Sign-In API:**
   - [ ] Go to "APIs & Services" → "Enabled APIs & services"
   - [ ] Search for "Google Identity Services API" (or "Google+ API")
   - [ ] Make sure it's **enabled**

4. **If Credential Doesn't Exist:**
   - [ ] Create a new Android credential:
     - Click "+ Create Credentials" → "OAuth 2.0 Client ID"
     - Application type: "Android"
     - Package name: `cc.dlabs.pesamind`
     - Certificate SHA-1: `B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03`
     - Click "Create"

## Debugging Steps:

### 1. Verify the Certificate SHA-1:
```bash
keytool -list -v -keystore ~/.android/my-release-key.keystore -storepass K@sh404730 | grep SHA1
```

Expected output:
```
SHA1: B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03
```

### 2. Check BuildConfig:
The app must be configured with:
- `GOOGLE_WEB_CLIENT_ID`: your **Web OAuth client ID** from Google Cloud Console
- `GOOGLE_ANDROID_CLIENT_ID`: your Android OAuth client ID (used for diagnostics/package+SHA verification)

> Important: `requestIdToken()` must use the **Web OAuth client ID**. Passing the Android client ID here can produce status code `10`.

### 3. Verify Android Manifest:
- Package name declared: `cc.dlabs.pesamind` ✓
- Internet permission declared ✓

## Resolution Steps:

1. **Most Common Issue:** Android OAuth SHA-1 fingerprint not registered
   - Solution: Add `B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03` to your credential in Google Cloud Console

2. **Another Common Issue:** Wrong client ID passed to `requestIdToken()`
   - `GOOGLE_WEB_CLIENT_ID` must be the credential of type **Web application**
   - `GOOGLE_ANDROID_CLIENT_ID` belongs to the **Android** credential tied to package name + SHA-1
   - Ensure the package name exactly matches: `cc.dlabs.pesamind`

3. **If still failing:** OAuth Consent Screen
   - Ensure the OAuth consent screen is configured
   - Add your app as a test application

## After Making Changes:

1. Save changes in Google Cloud Console
2. Clean and rebuild your app:
   ```bash
   export GOOGLE_WEB_CLIENT_ID="YOUR_WEB_OAUTH_CLIENT_ID"
   ./gradlew clean
   ./gradlew assembleDebug
   ```
3. Uninstall and reinstall the app
4. Try Google Sign-In again

## Important Notes:

- The SHA-1 fingerprint is generated from your signing keystore (`~/.android/my-release-key.keystore`)
- Using debug keystores will have different SHA-1 fingerprints
- Each keystore/certificate combination requires its own OAuth credential
- Changes to Google Cloud Console can take a few minutes to propagate

## Resources:

- [Google Sign-In Setup Guide](https://developers.google.com/identity/sign-in/android/start-integrating)
- [Google Cloud Console](https://console.cloud.google.com)
- [Common Google Sign-In Errors](https://developers.google.com/identity/sign-in/android/troubleshooting)

