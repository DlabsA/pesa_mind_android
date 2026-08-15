package cc.dlabs.pesamind.core.network

import android.util.Log
import cc.dlabs.pesamind.BuildConfig
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"

    // Resolved at build time (see app/build.gradle.kts). Release is pinned to
    // production; debug can be pointed at a local backend with
    // `-PAPI_BASE_URL=http://<lan-ip>:8099/api/v1/`, which is how features that
    // aren't deployed yet — subscription checkout, for one — get tested on a device.
    val BASE_URL: String = BuildConfig.API_BASE_URL

    private val client =
        OkHttpClient.Builder()
            // Add Token Authentication Interceptor (FIRST - modifies request)
            .addInterceptor { chain ->
                try {
                    val token = runBlocking { TokenManager.getToken() }
                    val request =
                        chain.request().newBuilder().apply {
                            // Only add auth header if token is not null or empty
                            if (!token.isNullOrEmpty()) {
                                addHeader("Authorization", "Bearer $token")
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
            // Add HTTP Logging Interceptor (LAST - for debugging only).
            //
            // Debug builds only. This used to log BODY unconditionally, which put
            // every JWT and refresh token into logcat on release builds — and would
            // put full card numbers there the moment the subscription checkout
            // screen shipped, since card details transit this client on their way
            // to the backend. Release builds now log nothing at all.
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor(RedactingLogger()).apply {
                            level = HttpLoggingInterceptor.Level.BODY
                            // Belt and braces: even in debug, never print these.
                            redactHeader("Authorization")
                            redactHeader("Cookie")
                        },
                    )
                }
            }
            // Set reasonable timeouts
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    val api: ApiService =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
}
