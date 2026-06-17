package cc.dlabs.pesamind.core.network

import android.util.Log
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    // ← Replace with your actual server IP
    const val BASE_URL = "https://api.dlabs.cc/api/v1/"

    private val client = OkHttpClient.Builder()
        // Add Token Authentication Interceptor (FIRST - modifies request)
        .addInterceptor { chain ->
            try {
                val token = runBlocking { TokenManager.getToken() }
                val request = chain.request().newBuilder().apply {
                    // Only add auth header if token is not null or empty
                    if (!token.isNullOrEmpty()) {
                        addHeader("Authorization", "Bearer $token")
                        Log.d(TAG, "✓ Token added to request for ${chain.request().url}")
                    } else {
                        Log.w(TAG, "✗ NO TOKEN - Request will likely fail with 401 for ${chain.request().url}")
                    }
                }.build()
                chain.proceed(request)
            } catch (e: Exception) {
                // If token retrieval fails, proceed without auth header
                Log.e(TAG, "ERROR retrieving token: ${e.message}", e)
                chain.proceed(chain.request())
            }
        }
        // Add token refresh interceptor to handle 401 responses
        .addInterceptor(TokenRefreshInterceptor())
        // Add HTTP Logging Interceptor (LAST - for debugging only)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        // Set reasonable timeouts
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}