package cc.dlabs.pesamind.features.auth

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Manages Google Sign-In operations including initialization and token retrieval.
 *
 * PLATFORM-SPECIFIC OAUTH FLOW:
 * This manager works with the platform-specific OAuth backend endpoint:
 *   POST /api/v1/auth/google/platform-signin
 *
 * The backend validates that the request comes from the Android platform and uses
 * the GOOGLE_OAUTH_ANDROID_CLIENT_ID for OAuth client validation.
 *
 * IMPORTANT: You must:
 * 1. Configure Google OAuth in Google Cloud Console
 * 2. Add your SHA-1 fingerprint to the OAuth 2.0 Android credentials
 * 3. Ensure Google Sign-In API is enabled for your project
 * 4. Backend must have GOOGLE_OAUTH_ANDROID_CLIENT_ID configured in .env
 */
class GoogleSignInManager(
    context: Context,
) {
    companion object {
        private const val TAG = "GoogleSignInManager"
    }

    private val googleSignInClient: GoogleSignInClient? =
        try {
            val options =
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestProfile()
                    .build()

            Log.d(TAG, "Initializing Google Sign-In client (platform-specific OAuth flow for Android)")
            GoogleSignIn.getClient(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Google Sign-In client", e)
            null
        }

    val isConfigured: Boolean = googleSignInClient != null

    /**
     * Returns the cached Google account if available
     */
    fun getCachedAccount(context: Context) = GoogleSignIn.getLastSignedInAccount(context)

    /**
     * Initiates sign-in flow by returning the sign-in intent
     * Returns null if Google Sign-In is not configured
     */
    fun getSignInIntent() = googleSignInClient?.signInIntent

    /**
     * Clears the cached Google account first, then returns a fresh sign-in intent.
     * This helps force account selection after app logout.
     */
    fun getSignInIntentAfterSignOut(onIntentReady: (android.content.Intent?) -> Unit) {
        val client = googleSignInClient
        if (client == null) {
            Log.e(TAG, "Google Sign-In client is not configured")
            onIntentReady(null)
            return
        }

        client.signOut()
            .addOnCompleteListener {
                Log.d(TAG, "Cleared cached Google session before launching sign-in")
                onIntentReady(client.signInIntent)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Google signOut failed, continuing with sign-in intent", error)
                onIntentReady(client.signInIntent)
            }
    }

    /**
     * Extracts ID token from the Google Sign-In result
     *
     * @param activityResultCode The result code from onActivityResult
     * @param data The Intent data from onActivityResult
     * @return GoogleSignInResult containing ID token or error message
     */
    fun handleSignInResult(
        _activityResultCode: Int,
        data: Any?,
    ): GoogleSignInResult {
        return if (!isConfigured) {
            GoogleSignInResult.Error("Google Sign-In is not configured. Please check Google Play Services setup.")
        } else if (data == null) {
            Log.e(TAG, "Google Sign-In returned null data")
            GoogleSignInResult.Error("Google Sign-In was canceled. Please try again.")
        } else {
            try {
                // This will handle the result from startActivityForResult
                if (data !is android.content.Intent) {
                    Log.e(TAG, "Invalid activity result data type: ${data.javaClass.simpleName}")
                    return GoogleSignInResult.Error("Invalid activity result data type")
                }

                Log.d(TAG, "Processing Google Sign-In result. Intent extras: ${data.extras?.keySet()}")

                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                Log.d(TAG, "Task completed successfully")
                val account = task.getResult(ApiException::class.java)

                val email = account.email
                val googleId = account.id
                Log.d(TAG, "Extracted account: email=$email, hasGoogleId=${googleId != null}")

                if (!email.isNullOrBlank() && !googleId.isNullOrBlank()) {
                    GoogleSignInResult.Success(
                        email = email,
                        displayName = account.displayName ?: "",
                        profilePhotoUrl = account.photoUrl?.toString(),
                        googleId = googleId,
                    )
                } else {
                    Log.e(TAG, "Missing required Google account data. email=$email, googleId=$googleId")
                    GoogleSignInResult.Error("Google account details are incomplete. Please try another account.")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In ApiException: status code=${e.statusCode}, message=${e.message}", e)
                GoogleSignInResult.Error(getErrorMessage(e.statusCode))
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during Google Sign-In: ${e.javaClass.simpleName}", e)
                GoogleSignInResult.Error("Sign-in failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Signs out the user from Google and clears the cached account
     */
    fun signOut() {
        googleSignInClient?.signOut()
        Log.d(TAG, "User signed out from Google")
    }

    /**
     * Converts Google Sign-In error status codes to user-friendly messages
     */
    private fun getErrorMessage(statusCode: Int): String =
        when (statusCode) {
            10 -> {
                Log.e(
                    TAG,
                    "Configuration Error (10):\n" +
                        "This error indicates a mismatch between your app configuration and Google Cloud Console.\n" +
                        "To fix:\n" +
                        "1. Verify your package name: cc.dlabs.pesamind\n" +
                        "2. Verify your SHA-1 fingerprint is registered: B3:AA:BA:5E:BA:B9:7A:82:31:28:38:D7:83:F6:A0:93:E0:46:FC:03\n" +
                        "3. Verify your Android OAuth client in Google Cloud Console matches that package+SHA-1\n" +
                        "4. Check that OAuth consent screen is configured and your test account is allowed\n" +
                        "See GOOGLE_SIGNIN_DIAGNOSTIC.md for detailed troubleshooting steps",
                )
                "Configuration error: Android OAuth package/SHA-1 mismatch in Google Cloud Console"
            }
            12501 -> "Sign-in was canceled"
            12500 -> "Network error. Check your connection"
            else -> "Sign-in failed (error code: $statusCode). Please try again."
        }
}

/**
 * Sealed class representing the result of Google Sign-In
 */
sealed class GoogleSignInResult {
    data class Success(
        val email: String,
        val displayName: String,
        val profilePhotoUrl: String?,
        val googleId: String,
    ) : GoogleSignInResult()

    data class Error(val message: String) : GoogleSignInResult()
}
