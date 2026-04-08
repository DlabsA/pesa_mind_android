package cc.dlabs.pesamind.core.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "http://YOUR_SERVER_IP:8080/"

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .addInterceptor { chain ->
                    val requestBuilder = chain.request().newBuilder()
                    TokenManager.getToken()
                        .takeIf { it.isNotBlank() }
                        ?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
                    chain.proceed(requestBuilder.build())
                }
                .build()
        )
        .build()
}

