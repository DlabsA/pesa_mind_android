package cc.dlabs.pesamind.core.network
import android.util.Log
import cc.dlabs.pesamind.core.network.ApiClient.BASE_URL
import cc.dlabs.pesamind.core.network.models.RefreshRequest
import cc.dlabs.pesamind.core.storage.AuthManager
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.sync.SyncScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that handles 401 Unauthorized responses by:
 * 1. Attempting to refresh the authentication token (ONE attempt per request chain)
 * 2. Rebuilding and retrying the original request with the new token
 * 3. Clearing tokens and logging out if refresh fails (prevents infinite loops)
 */
class TokenRefreshInterceptor : Interceptor {
    private companion object {
        private const val TAG = "TokenRefreshInterceptor"

        // Mutex ensures only one thread refreshes at a time and others wait for completion
        private val refreshMutex = Mutex()
        private var refreshAttemptedForThisChain = ThreadLocal<Boolean>()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestUrl = originalRequest.url.toString()

        // Ensure ThreadLocal is cleaned up after this chain completes
        try {
            // Skip token refresh for auth endpoints - they should return their own error messages
            if (isAuthEndpoint(requestUrl)) {
                Log.d(TAG, "Auth endpoint detected, skipping refresh logic: $requestUrl")
                return chain.proceed(originalRequest)
            }

            // Check if we've already attempted a refresh for this request chain
            val hasAttemptedRefresh = refreshAttemptedForThisChain.get() ?: false
            if (hasAttemptedRefresh) {
                // Already tried refresh, don't try again to prevent infinite loops
                Log.w(TAG, "Already attempted refresh for this chain, skipping to prevent infinite loop")
                return chain.proceed(originalRequest)
            }

            val response = chain.proceed(originalRequest)

            // If not a 401, return the response as-is
            if (response.code != 401) {
                return response
            }

            Log.w(TAG, "Got 401 response for ${originalRequest.url}")
            response.close()

            // Use Mutex to coordinate token refresh across threads
            // All threads wait for the first one to complete refresh
            return runBlocking {
                refreshMutex.lock()
                try {
                    // Attempt to refresh the token (ONE TIME ONLY PER CHAIN)
                    Log.d(TAG, "Attempting to refresh token...")
                    val refreshed = refreshToken()

                    if (refreshed) {
                        refreshAttemptedForThisChain.set(true)
                        Log.d(TAG, "✓ Token refreshed successfully, retrying request")

                        // Rebuild the request with the new token
                        val newToken = TokenManager.getToken()
                        val retryRequest =
                            originalRequest.newBuilder()
                                .removeHeader("Authorization")
                                .apply {
                                    if (!newToken.isNullOrEmpty()) {
                                        addHeader("Authorization", "Bearer $newToken")
                                    }
                                }
                                .build()

                        // Retry with the new token (ONE MORE TIME ONLY)
                        chain.proceed(retryRequest)
                    } else {
                        // Refresh failed - logout and clear everything
                        Log.e(TAG, "Token refresh failed, logging out")
                        handleLogout()
                        response
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during token refresh: ${e.message}", e)
                    e.printStackTrace()
                    // Clear tokens on error and logout to prevent infinite loops
                    handleLogout()
                    response
                } finally {
                    refreshMutex.unlock()
                }
            }
        } finally {
            // Clean up ThreadLocal to prevent thread leaks and allow retries on same thread
            refreshAttemptedForThisChain.remove()
        }
    }

    /**
     * Handle logout: clear tokens and notify the app
     */
    private fun handleLogout() {
        runBlocking {
            TokenManager.clearTokens()
            // Call AuthManager to trigger logout event/navigation
            AuthManager.logout()
        }
    }

    /**
     * Attempts to refresh the access token using the refresh token
     * @return true if refresh was successful, false otherwise
     */
    private suspend fun refreshToken(): Boolean =
        try {
            val refreshTokenValue = TokenManager.getRefreshToken()

            if (refreshTokenValue.isNullOrEmpty()) {
                // No refresh token available, cannot refresh
                Log.e(TAG, "No refresh token available - cannot refresh")
                TokenManager.clearRefreshToken()
                false
            } else {
                Log.d(TAG, "Sending refresh token request...")
                // Create a new API service without interceptors to avoid infinite loops
                val refreshService = createRefreshApiService()
                val refreshRequest = RefreshRequest(refreshTokenValue)

                val response = refreshService.refresh(refreshRequest)

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!

                    // ATOMIC: Save new tokens directly without intermediate clearTokens() call
                    // This prevents a race condition window where other threads see null tokens
                    TokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken)
                    Log.d(TAG, "✓ New tokens saved successfully")
                    // A fresh token is worth a pull — the outbox/periodic pull may have been
                    // silently failing on the now-expired token for a while.
                    SyncScheduler.triggerSyncNow()
                    true
                } else {
                    // Refresh failed - clear the invalid refresh token
                    Log.e(TAG, "Refresh failed with code: ${response.code()} - clearing invalid refresh token")
                    TokenManager.clearRefreshToken()
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token: ${e.message} - clearing invalid refresh token", e)
            e.printStackTrace()
            TokenManager.clearRefreshToken()
            false
        }

    /**
     * Creates a separate Retrofit API service for token refresh without the auth interceptor
     * to avoid infinite loops when refreshing tokens
     */
    private fun createRefreshApiService(): ApiService {
        val refreshClient =
            okhttp3.OkHttpClient.Builder()
                .addInterceptor(
                    okhttp3.logging.HttpLoggingInterceptor().apply {
                        level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
                    },
                )
                .build()

        return retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(refreshClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /**
     * Check if a URL is an auth endpoint that should not trigger token refresh.
     * Auth endpoints return their own error messages and should not be intercepted.
     */
    private fun isAuthEndpoint(url: String): Boolean =
        url.contains("/auth/login") ||
            url.contains("/auth/register") ||
            url.contains("/auth/refresh") ||
            url.contains("/auth/verify")
}
