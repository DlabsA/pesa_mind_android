# Navigation Fix for Google Sign-In on Register Page

## Issue
When an existing user signed in via Google on the **Register page**, they were NOT being routed to the next page despite successful authentication.

**Logs showed**:
```
Google Sign-In successful: kakurucon1234@gmail.com ✅
Backend returns: is_new_user: false ✅
AuthVM: "Existing user, saving tokens" ✅
But... NO navigation occurred ❌
```

## Root Cause
The `RegisterScreen` component was missing a navigation handler for the `AuthUiState.LoginSuccess` state.

### What Happens:
1. User taps "Sign up with Google" button on RegisterScreen
2. Google Sign-In succeeds
3. App calls `vm.handleGoogleSignIn()` in AuthViewModel
4. Backend responds with `is_new_user: false` (existing user)
5. AuthViewModel sets state to: `AuthUiState.LoginSuccess(destination)`
6. **RegisterScreen's `LaunchedEffect` was NOT listening for this state** ❌
7. No navigation triggered
8. User stuck on RegisterScreen

### Why LoginScreen Worked:
LoginScreen already had the correct handler at lines 119-124:
```kotlin
is AuthUiState.LoginSuccess -> {
    val destination = (authState as AuthUiState.LoginSuccess).destination
    navController.navigate(destination) {
        popUpTo(Routes.Login.route) { inclusive = true }
    }
}
```

But RegisterScreen did NOT have this handler.

## Solution

### Code Change: RegisterScreen.kt (lines 115-135)

**Before** (Missing LoginSuccess handler):
```kotlin
LaunchedEffect(authState) {
    when (authState) {
        is AuthUiState.RegisterSuccess -> { ... }
        is AuthUiState.NeedUsername -> { ... }
        is AuthUiState.GoogleSignupSuccess -> { ... }
        else -> {}  // ← LoginSuccess falls through here!
    }
}
```

**After** (Added LoginSuccess handler):
```kotlin
LaunchedEffect(authState) {
    when (authState) {
        is AuthUiState.RegisterSuccess -> { ... }
        is AuthUiState.NeedUsername -> { ... }
        is AuthUiState.GoogleSignupSuccess -> { ... }
        is AuthUiState.LoginSuccess -> {
            // ← NEW: Handle existing user sign-in
            val destination = (authState as AuthUiState.LoginSuccess).destination
            navController.navigate(destination) {
                popUpTo(Routes.Register.route) { inclusive = true }
            }
        }
        else -> {}
    }
}
```

## Expected Behavior After Fix

### Scenario 1: Existing User Signs In via Google on RegisterScreen
```
1. RegisterScreen: Tap "Sign up with Google"
2. Google Sign-In succeeds
3. Backend returns: is_new_user: false
4. AuthVM sets: LoginSuccess(destination = "lock_setup" | "pin_unlock" | "pattern_unlock")
5. RegisterScreen observes LoginSuccess
6. ✅ User navigated to lock setup / PIN unlock / Pattern unlock
```

### Scenario 2: New User Signs In via Google on RegisterScreen
```
1. RegisterScreen: Tap "Sign up with Google"
2. Google Sign-In succeeds
3. Backend returns: is_new_user: true
4. AuthVM sets: NeedUsername(email, googleId, displayName, profilePhotoUrl)
5. RegisterScreen observes NeedUsername
6. ✅ Username selection dialog appears
7. User enters username
8. AuthVM sets: GoogleSignupSuccess
9. ✅ User navigated to Dashboard
```

### Scenario 3: Existing User Signs In via Google on LoginScreen (Already Working)
```
1. LoginScreen: Tap "Sign in with Google"
2. Google Sign-In succeeds
3. Backend returns: is_new_user: false
4. AuthVM sets: LoginSuccess(destination)
5. LoginScreen observes LoginSuccess ✅ (already had handler)
6. ✅ User navigated appropriately
```

## Build Status
✅ **Build Successful**
```
BUILD SUCCESSFUL in 18s
45 actionable tasks: 14 executed, 31 up-to-date
```

## Testing

### Install Updated APK
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Test Steps
1. **Open app and go to Register page**
2. **Tap "Sign up with Google"**
3. **Sign in with an EXISTING Google account** (one already registered in app)
   - User should be navigated to lock setup / PIN unlock / Pattern unlock
4. **Verify navigation occurs** (user should no longer be stuck on RegisterScreen)

### Expected Logs
```
RegisterScreen: Google Sign-In successful: kakurucon1234@gmail.com
AuthVM: Existing user, saving tokens
RegisterScreen: Existing user signed in via Google, navigating to lock_setup
```

## Files Modified
- **`app/src/main/java/cc/dlabs/pesamind/features/auth/RegisterScreen.kt`**
  - Lines 115-135: Added `LoginSuccess` state handler to `LaunchedEffect`

## Summary of Navigation States

| State | Triggered | Screen Handling | Action |
|-------|-----------|-----------------|--------|
| `RegisterSuccess` | Manual registration | RegisterScreen ✅ | Navigate to Login |
| `LoginSuccess` | Existing user signs in | LoginScreen ✅<br>RegisterScreen ✅ (NEW) | Navigate to destination |
| `NeedUsername` | New user Google sign-in | Both screens ✅ | Show username dialog |
| `GoogleSignupSuccess` | New user completes signup | Both screens ✅ | Navigate to Dashboard |

## Architecture

The fix aligns RegisterScreen's navigation logic with LoginScreen's, creating consistent behavior:

```
User Action: Tap "Sign in/up with Google"
    ↓
Google Sign-In succeeds → handleGoogleSignIn()
    ↓
Backend validates account
    ↓
┌─ Is new user?
│  YES → NeedUsername state → Dialog appears → completeGoogleSignup() → GoogleSignupSuccess → Dashboard ✅
│  NO → LoginSuccess state → Navigate to lock/pin/pattern setup ✅
└─
```

## Deployment Notes

1. This is a UI-only navigation fix
2. No backend changes required
3. No API changes
4. No database changes
5. No dependency changes

The backend was already correctly returning `is_new_user: false` for existing users. The app just wasn't handling the resulting state.

## Related Issues Fixed

- ✅ Google Sign-In status code 10 fixed (google-services.json added)
- ✅ Navigation for existing users on RegisterScreen fixed (this change)
- ✅ Navigation for new users on RegisterScreen working (already implemented)
- ✅ Navigation on LoginScreen working (already implemented)

