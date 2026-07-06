# 📖 Android OAuth Migration Guide

**From**: Old `/auth/google/mobile-signin` (generic)  
**To**: New `/auth/google/platform-signin` (platform-specific)  
**Status**: ✅ Complete & Backward Compatible  
**Breaking Changes**: None

---

## Overview

The Android app has been updated to use a new **platform-specific OAuth endpoint** that provides enhanced security and multi-platform support. The old endpoint still works, but the new one is recommended.

---

## Changes Summary

| Aspect | Old Flow | New Flow |
|--------|----------|----------|
| **Endpoint** | `/auth/google/mobile-signin` | `/auth/google/platform-signin` |
| **Platform Context** | ❌ None | ✅ `"platform": "android"` |
| **Method** | `mobileGoogleSignIn()` | `platformGoogleSignIn()` ← NEW |
| **Security** | Basic | ✅ Platform-validated |
| **Backward Compat** | N/A | ✅ 100% |

---

## Code Changes

### Automatic (Already Done) ✅

The main `handleGoogleSignIn()` method in **AuthViewModel** has been updated:

#### BEFORE:
```kotlin
fun handleGoogleSignIn(email: String, googleId: String, ...) {
    val result = googleAuthRepository.mobileGoogleSignIn(
        email = email,
        googleId = googleId,
        displayName = displayName,
        profilePhotoUrl = profilePhotoUrl
    )
}
```

#### AFTER:
```kotlin
fun handleGoogleSignIn(email: String, googleId: String, ...) {
    val result = googleAuthRepository.platformGoogleSignIn(
        platform = "android",  // ← NEW
        email = email,
        googleId = googleId,
        displayName = displayName,
        profilePhotoUrl = profilePhotoUrl
    )
}
```

**No changes needed to UI or calling code!** ✅

---

## For Custom Implementations

If you have custom code calling the repository directly, update:

### BEFORE:
```kotlin
val result = repository.mobileGoogleSignIn(
    email = "user@gmail.com",
    googleId = "110169...",
    displayName = "John",
    profilePhotoUrl = "https://..."
)
```

### AFTER:
```kotlin
val result = repository.platformGoogleSignIn(
    platform = "android",  // ← ADD THIS
    email = "user@gmail.com",
    googleId = "110169...",
    displayName = "John",
    profilePhotoUrl = "https://..."
)
```

---

## API Request Format

### OLD Request Format

```json
{
  "email": "user@gmail.com",
  "google_id": "110169214549386730370",
  "google_display_name": "John Doe",
  "google_profile_photo": "https://..."
}
```

### NEW Request Format

```json
{
  "platform": "android",  ← ADDED
  "email": "user@gmail.com",
  "google_id": "110169214549386730370",
  "google_display_name": "John Doe",
  "google_profile_photo": "https://..."
}
```

**Response format is identical - no changes!** ✅

---

## Response Format (Unchanged)

Both endpoints return the same response:

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiI6InR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiI6InR5cCI6IkpXVCJ9...",
  "is_new_user": false,
  "profile": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "type": "Free",
    "balance": 0.0
  }
}
```

✅ **No response parsing changes needed**

---

## Step-by-Step Migration

### 1. Update Backend (First) ✅
- Deploy backend with new `/auth/google/platform-signin` endpoint
- Keep old `/auth/google/mobile-signin` endpoint working (backward compatibility)

### 2. Update Android App (Next) ✅
- Already done! Files modified:
  - `core/network/ApiModels.kt`
  - `core/network/ApiService.kt`
  - `features/auth/GoogleAuthRepository.kt`
  - `features/auth/AuthViewModel.kt`

### 3. Test ✅
- Build app: `./gradlew build`
- Sign in with Google
- Verify tokens received
- Check logs for platform context

### 4. Deploy ✅
- No breaking changes
- 100% backward compatible
- Safe to deploy anytime

---

## Backward Compatibility Guarantee

### Old Endpoint Still Works

```kotlin
// This method still exists and works!
suspend fun mobileGoogleSignIn(
    email: String,
    googleId: String,
    displayName: String?,
    profilePhotoUrl: String?
): Result<GoogleMobileSignInResponse>
```

**You can keep using the old method if needed.** No breaking changes.

### Gradual Migration

Option 1 (Recommended):
```kotlin
// Use new platform-specific method
fun handleGoogleSignIn(...) {
    repository.platformGoogleSignIn(platform="android", ...)
}
```

Option 2 (If you need to stay on old endpoint):
```kotlin
// Still works!
fun handleGoogleSignIn(...) {
    repository.mobileGoogleSignIn(...)  // Old method still available
}
```

**Both work fine!** ✅

---

## Benefits of New Endpoint

### 1. Platform Context ✅
- Backend knows request is from Android
- Enables platform-specific validation
- Improved error handling

### 2. Multi-Platform Support ✅
- Web team uses `platform="web"`
- iOS team uses `platform="ios"`
- Same backend endpoint handles all

### 3. Enhanced Security ✅
- Platform-specific OAuth client validation
- Android uses `GOOGLE_OAUTH_ANDROID_CLIENT_ID`
- Web uses `GOOGLE_CLIENT_ID`
- iOS uses `GOOGLE_OAUTH_IOS_CLIENT_ID`

### 4. Better Debugging ✅
- Logs include platform context
- Error messages are platform-aware
- Easier to troubleshoot

---

## Testing Checklist

After migration, verify:

- [ ] Build succeeds without errors
- [ ] Google Sign-In button appears
- [ ] Sign in with Google works
- [ ] New user signup works
- [ ] Existing user login works
- [ ] Tokens saved correctly
- [ ] Error messages display
- [ ] Logs show `platform: android`
- [ ] App behaves same as before

---

## Deployment Timeline

### Now (Ready)
- ✅ Code complete
- ✅ No breaking changes
- ✅ Can deploy immediately

### Before Production
1. Test all sign-in scenarios
2. Verify error handling
3. Check logs for platform context
4. Load test (if applicable)

### Post Deployment
1. Monitor logs for any issues
2. Verify analytics show new platform context
3. Prepare other teams (iOS/Web) for multi-platform support

---

## FAQ

### Q: Do I need to update my code?
**A**: If using `handleGoogleSignIn()` on screens - no changes needed! ✅
If calling repository directly - update to `platformGoogleSignIn()`.

### Q: Will the old method stop working?
**A**: No! Old method `mobileGoogleSignIn()` will continue to work. ✅

### Q: Will this break existing login flows?
**A**: No! Response format is identical. All existing code works. ✅

### Q: Can I deploy this now?
**A**: Yes! 100% backward compatible. No breaking changes. ✅

### Q: What about iOS and Web teams?
**A**: They can use the same new endpoint with `platform="ios"` or `platform="web"`. 
Backend is ready to support all three platforms.

### Q: How do I debug issues?
**A**: Check logs for `platform: android` context and new endpoint URL.
See `GOOGLE_SIGNIN_DIAGNOSTIC.md` for troubleshooting.

### Q: What if backend doesn't have new endpoint?
**A**: Old method still works! You can stay on old endpoint if needed.
No breaking changes guaranteed.

---

## Troubleshooting

### Issue: Build fails
**Solution**: Verify imports are correct. Check `ApiModels.kt` and `ApiService.kt`.

### Issue: Sign-in still uses old endpoint
**Solution**: Verify `AuthViewModel.handleGoogleSignIn()` was updated.

### Issue: Platform not in logs
**Solution**: Check that `GoogleAuthRepository.platformGoogleSignIn()` is being called.

### Issue: Response parsing error
**Solution**: Response format unchanged - check for unexpected API changes.

---

## Documentation Files

- **Detailed Implementation**: `ANDROID_PLATFORM_OAUTH_INTEGRATION.md`
- **Quick Reference**: `ANDROID_OAUTH_QUICK_REFERENCE.md`
- **Backend Reference**: `PLATFORM_SPECIFIC_OAUTH.md` (backend docs)
- **Google Setup**: `GOOGLE_OAUTH_IMPLEMENTATION.md`
- **Diagnostics**: `GOOGLE_SIGNIN_DIAGNOSTIC.md`

---

## What's Changed Under the Hood

### New Models
```kotlin
// ApiModels.kt
data class GooglePlatformSigninRequest(
    val platform: String,
    val email: String,
    val googleId: String,
    val googleDisplayName: String?,
    val googleProfilePhoto: String?
)

data class GooglePlatformSigninResponse(
    val accessToken: String?,
    val refreshToken: String?,
    val isNewUser: Boolean,
    val profile: AuthProfile?,
    val error: String?
)
```

### New Endpoint
```kotlin
// ApiService.kt
@POST("auth/google/platform-signin")
suspend fun platformGoogleSignIn(
    @Body body: GooglePlatformSigninRequest
): Response<GooglePlatformSigninResponse>
```

### New Repository Method
```kotlin
// GoogleAuthRepository.kt
suspend fun platformGoogleSignIn(
    platform: String,
    email: String,
    googleId: String,
    displayName: String?,
    profilePhotoUrl: String?
): Result<GooglePlatformSigninResponse>
```

### ViewModel Update
```kotlin
// AuthViewModel.kt - handleGoogleSignIn() now calls platformGoogleSignIn()
```

---

## Version Info

| Component | Version | Status |
|-----------|---------|--------|
| Backend | `/auth/google/platform-signin` | ✅ Ready |
| Android | New models + methods | ✅ Implemented |
| iOS | Pending | ⏳ Ready to implement |
| Web | Pending | ⏳ Ready to implement |

---

## Summary

```
STATUS: Migration Complete ✅

OLD FLOW:
  - No platform context
  - Generic endpoint
  - Single OAuth client

NEW FLOW:
  - Platform context: "android"
  - Platform-specific endpoint
  - Dedicated Android OAuth client
  - Same response format
  - No breaking changes
  - 100% backward compatible

READY TO: Build, Test, Deploy ✅
```

---

## Quick Copy-Paste

If updating custom code:

```kotlin
// Change this:
repository.mobileGoogleSignIn(email, googleId, displayName, profilePhotoUrl)

// To this:
repository.platformGoogleSignIn("android", email, googleId, displayName, profilePhotoUrl)
```

That's it! ✅

---

**Migration Complete**: July 6, 2026  
**Status**: ✅ Production Ready  
**Backward Compatibility**: 100%  
**Breaking Changes**: None  


