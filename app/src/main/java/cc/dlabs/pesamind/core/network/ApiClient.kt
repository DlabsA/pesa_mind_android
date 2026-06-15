package cc.dlabs.pesamind.core.network

import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // ← Replace with your actual server IP
    const val BASE_URL = "http://173.212.219.227:8080/api/v1/"

    private val client = OkHttpClient.Builder()
        // Add Token Authentication Interceptor (FIRST - modifies request)
        .addInterceptor { chain ->
            val token = runBlocking { TokenManager.getToken() }
            val request = chain.request().newBuilder().apply {
                if (token != null) addHeader("Authorization", "Bearer $token")
            }.build()
            chain.proceed(request)
        }
        // Add token refresh interceptor to handle 401 responses
        .addInterceptor(TokenRefreshInterceptor())
        // Add HTTP Logging Interceptor (LAST - for debugging only, use HEADERS level)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS // Changed from BODY to avoid closed stream issues
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