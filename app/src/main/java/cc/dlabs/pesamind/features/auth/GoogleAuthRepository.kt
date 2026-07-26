package cc.dlabs.pesamind.features.auth

import android.util.Log
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.CheckUsernameRequest
import cc.dlabs.pesamind.core.network.models.CheckUsernameResponse
import cc.dlabs.pesamind.core.network.models.CompleteGoogleSignupRequest
import cc.dlabs.pesamind.core.network.models.CompleteGoogleSignupResponse
import cc.dlabs.pesamind.core.network.models.GoogleMobileSignInRequest
import cc.dlabs.pesamind.core.network.models.GoogleMobileSignInResponse
import cc.dlabs.pesamind.core.network.models.GooglePlatformSigninRequest
import cc.dlabs.pesamind.core.network.models.GooglePlatformSigninResponse

/**
 * Repository for Google OAuth operations.
 * Handles API communication and error handling.
 */
class GoogleAuthRepository {
    companion object {
        private const val TAG = "GoogleAuthRepo"
    }

    /**
     * Signs in with Google account details using the platform-specific OAuth endpoint.
     * This is the new recommended flow with platform validation.
     *
     * @param platform The platform identifier: "android", "web", or "ios"
     * @param email User's email from Google
     * @param googleId Google's unique identifier (sub claim)
     * @param displayName User's display name from Google (optional)
     * @param profilePhotoUrl User's profile photo URL from Google (optional)
     * @return Result containing sign-in response or error
     */
    suspend fun platformGoogleSignIn(
        platform: String,
        email: String,
        googleId: String,
        displayName: String?,
        profilePhotoUrl: String?,
    ): Result<GooglePlatformSigninResponse> {
        return try {
            Log.d(TAG, "Signing in with Google account details using platform-specific endpoint...")
            Log.d(TAG, "Platform: $platform, Email: $email")
            val response =
                ApiClient.api.platformGoogleSignIn(
                    GooglePlatformSigninRequest(
                        platform = platform,
                        email = email,
                        googleId = googleId,
                        googleDisplayName = displayName,
                        googleProfilePhoto = profilePhotoUrl,
                    ),
                )

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        Log.d(TAG, "Google platform sign-in successful. Is new user: ${body.isNewUser}")
                        Result.success(body)
                    } else {
                        Log.e(TAG, "Empty response body from platform sign-in endpoint")
                        Result.failure(Exception("Empty response from server"))
                    }
                }
                response.code() == 401 -> {
                    Log.e(TAG, "Google account is not authorized")
                    Result.failure(Exception("Google account not authorized. Please sign in again."))
                }
                response.code() == 400 -> {
                    Log.e(TAG, "Bad request: ${response.errorBody()?.string()}")
                    Result.failure(Exception("Invalid request to authentication server"))
                }
                else -> {
                    Log.e(TAG, "Google platform sign-in failed with code ${response.code()}")
                    Result.failure(Exception("Authentication failed. Please try again."))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Google platform sign-in", e)
            Result.failure(handleNetworkException(e))
        }
    }

    /**
     * Signs in with Google account details using Android-client-only flow.
     */
    suspend fun mobileGoogleSignIn(
        email: String,
        googleId: String,
        displayName: String?,
        profilePhotoUrl: String?,
    ): Result<GoogleMobileSignInResponse> {
        return try {
            Log.d(TAG, "Signing in with Google account details...")
            val response =
                ApiClient.api.mobileGoogleSignIn(
                    GoogleMobileSignInRequest(
                        email = email,
                        googleId = googleId,
                        googleDisplayName = displayName,
                        googleProfilePhoto = profilePhotoUrl,
                    ),
                )

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        Log.d(TAG, "Google mobile sign-in successful. Is new user: ${body.isNewUser}")
                        Result.success(body)
                    } else {
                        Log.e(TAG, "Empty response body from mobile sign-in endpoint")
                        Result.failure(Exception("Empty response from server"))
                    }
                }
                response.code() == 401 -> {
                    Log.e(TAG, "Google account is not authorized")
                    Result.failure(Exception("Google account not authorized. Please sign in again."))
                }
                response.code() == 400 -> {
                    Log.e(TAG, "Bad request: ${response.errorBody()?.string()}")
                    Result.failure(Exception("Invalid request to authentication server"))
                }
                else -> {
                    Log.e(TAG, "Google mobile sign-in failed with code ${response.code()}")
                    Result.failure(Exception("Authentication failed. Please try again."))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Google mobile sign-in", e)
            Result.failure(handleNetworkException(e))
        }
    }

    /**
     * Completes signup for a new Google OAuth user
     *
     * @param email User email from Google
     * @param googleId Google account ID
     * @param username Username chosen by user
     * @param displayName User's display name from Google
     * @param profilePhotoUrl User's profile photo URL from Google
     * @return Result containing the signup response or error
     */
    suspend fun completeGoogleSignup(
        email: String,
        googleId: String,
        username: String,
        displayName: String?,
        profilePhotoUrl: String?,
    ): Result<CompleteGoogleSignupResponse> {
        return try {
            Log.d(TAG, "Completing Google signup for username: $username")
            val response =
                ApiClient.api.completeGoogleSignup(
                    CompleteGoogleSignupRequest(
                        email = email,
                        googleId = googleId,
                        username = username,
                        googleDisplayName = displayName,
                        googleProfilePhoto = profilePhotoUrl,
                    ),
                )

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        Log.d(TAG, "Google signup completed successfully")
                        Result.success(body)
                    } else {
                        Log.e(TAG, "Empty response body from complete signup endpoint")
                        Result.failure(Exception("Empty response from server"))
                    }
                }
                response.code() == 409 -> {
                    Log.e(TAG, "Username already taken")
                    Result.failure(Exception("This username is already taken. Please choose another."))
                }
                response.code() == 400 -> {
                    Log.e(TAG, "Bad request during signup")
                    Result.failure(Exception("Invalid details provided. Please check your information."))
                }
                else -> {
                    Log.e(TAG, "Signup failed with code ${response.code()}")
                    Result.failure(Exception("Signup failed. Please try again."))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during signup", e)
            Result.failure(handleNetworkException(e))
        }
    }

    /**
     * Checks if a username is available
     *
     * @param username The username to check
     * @return Result containing availability status or error
     */
    suspend fun checkUsername(username: String): Result<CheckUsernameResponse> {
        return try {
            Log.d(TAG, "Checking username availability: $username")
            val response =
                ApiClient.api.checkUsername(
                    CheckUsernameRequest(username),
                )

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        Log.d(TAG, "Username check completed. Available: ${body.available}")
                        Result.success(body)
                    } else {
                        Log.e(TAG, "Empty response body from check username endpoint")
                        Result.failure(Exception("Could not check username availability"))
                    }
                }
                else -> {
                    Log.e(TAG, "Username check failed with code ${response.code()}")
                    Result.failure(Exception("Could not check username. Please try again."))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during username check", e)
            Result.failure(handleNetworkException(e))
        }
    }

    /**
     * Handles network-related exceptions and converts them to user-friendly messages
     */
    private fun handleNetworkException(e: Exception): Exception {
        return when {
            e.message?.contains("Unable to resolve host") == true ->
                Exception("No internet connection. Please check your network.")
            e.message?.contains("Connection timeout") == true ->
                Exception("Connection timed out. Please try again.")
            e.message?.contains("Connection refused") == true ->
                Exception("Could not reach the server. Please try again later.")
            else -> {
                Log.e(TAG, "Unhandled network exception: ${e.message}")
                Exception("Network error. Please try again.")
            }
        }
    }
}
