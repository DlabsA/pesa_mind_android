package cc.dlabs.pesamind.core.network
import  cc.dlabs.pesamind.core.network.ApiClient.BASE_URL
import cc.dlabs.pesamind.core.network.models.RefreshRequest
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that handles 401 Unauthorized responses by:
 * 1. Attempting to refresh the authentication token
 * 2. Retrying the original request with the new token
 * 3. Clearing tokens and failing if refresh fails
 */
class TokenRefreshInterceptor : Interceptor {
    
    companion object {
        private var isRefreshing = false
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        // If not a 401, return the response as-is
        if (response.code != 401) {
            return response
        }

        response.close()

        // Synchronized block to prevent multiple refresh attempts
        synchronized(this) {
            // Check again after acquiring lock (another thread might have already refreshed)
            if (isRefreshing) {
                return chain.proceed(originalRequest)
            }

            isRefreshing = true
        }

        return try {
            // Attempt to refresh the token
            val refreshed = refreshToken()
            
            if (refreshed) {
                isRefreshing = false
                // Retry the original request with the new token
                chain.proceed(originalRequest)
            } else {
                isRefreshing = false
                // Refresh failed, return 401
                response
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRefreshing = false
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
                TokenManager.clearTokens()
                false
            } else {
                // Create a new API service without interceptors to avoid infinite loops
                val refreshService = createRefreshApiService()
                val refreshRequest = RefreshRequest(refreshTokenValue)
                
                val response = refreshService.refresh(refreshRequest)
                
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    
                    // Save new tokens
                    TokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken)
                    true
                } else {
                    // Refresh failed, clear tokens
                    TokenManager.clearTokens()
                    false
                }
            }
        } catch (e: Exception) {
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



