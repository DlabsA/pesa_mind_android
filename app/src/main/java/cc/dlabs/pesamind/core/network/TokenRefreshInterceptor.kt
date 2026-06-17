package cc.dlabs.pesamind.core.network
import  cc.dlabs.pesamind.core.network.ApiClient.BASE_URL
import cc.dlabs.pesamind.core.network.models.RefreshRequest
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import android.util.Log

/**
 * Interceptor that handles 401 Unauthorized responses by:
 * 1. Attempting to refresh the authentication token (max 1 attempt per request)
 * 2. Retrying the original request with the new token
 * 3. Clearing tokens and failing if refresh fails (prevents infinite loops)
 */
class TokenRefreshInterceptor : Interceptor {
    
    private companion object {
        private const val TAG = "TokenRefreshInterceptor"
        private var isRefreshing = false
        private var refreshAttemptedForThisChain = ThreadLocal<Boolean>()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
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

        // Synchronized block to prevent multiple refresh attempts
        synchronized(this) {
            // Check again after acquiring lock (another thread might have already refreshed)
            if (isRefreshing) {
                Log.d(TAG, "Another thread is already refreshing, returning original request")
                return chain.proceed(originalRequest)
            }

            isRefreshing = true
        }

        return try {
            // Attempt to refresh the token
            Log.d(TAG, "Attempting to refresh token...")
            val refreshed = refreshToken()
            
            if (refreshed) {
                isRefreshing = false
                refreshAttemptedForThisChain.set(true)
                Log.d(TAG, "✓ Token refreshed successfully, retrying request")
                // Retry the original request with the new token (ONE MORE TIME ONLY)
                chain.proceed(originalRequest)
            } else {
                isRefreshing = false
                Log.e(TAG, "✗ Token refresh failed, returning 401")
                // Refresh failed, return 401 without retrying
                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token refresh: ${e.message}", e)
            e.printStackTrace()
            isRefreshing = false
            // Clear tokens on error to prevent infinite loops
            runBlocking { TokenManager.clearTokens() }
            response
        }
    }

    /**
     * Attempts to refresh the access token using the refresh token
     * @return true if refresh was successful, false otherwise
     */
    private fun refreshToken(): Boolean = runBlocking {
        return@runBlocking try {
            val refreshTokenValue = TokenManager.getRefreshToken()
            
            if (refreshTokenValue.isNullOrEmpty()) {
                // No refresh token available, cannot refresh
                Log.e(TAG, "No refresh token available")
                TokenManager.clearTokens()
                false
            } else {
                Log.d(TAG, "Sending refresh token request...")
                // Create a new API service without interceptors to avoid infinite loops
                val refreshService = createRefreshApiService()
                val refreshRequest = RefreshRequest(refreshTokenValue)
                
                val response = refreshService.refresh(refreshRequest)
                
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    
                    // Save new tokens
                    TokenManager.clearTokens()
                    TokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken)
                    Log.d(TAG, "✓ New tokens saved successfully")
                    true
                } else {
                    // Refresh failed, clear tokens
                    Log.e(TAG, "Refresh failed with code: ${response.code()}")
                    TokenManager.clearTokens()
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token: ${e.message}", e)
            e.printStackTrace()
            TokenManager.clearTokens()
            false
        }
    }

    /**
     * Creates a separate Retrofit API service for token refresh without the auth interceptor
     * to avoid infinite loops when refreshing tokens
     */
    private fun createRefreshApiService(): ApiService {
        val refreshClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            })
            .build()

        return retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(refreshClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}



