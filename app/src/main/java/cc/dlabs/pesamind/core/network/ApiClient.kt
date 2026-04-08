package cc.dlabs.pesamind.core.network

import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // ← Replace with your actual server IP
    private const val BASE_URL = "http://173.212.219.227:8080/api/v1/"

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val token = runBlocking { TokenManager.getToken() }
            val request = chain.request().newBuilder().apply {
                if (token != null) addHeader("Authorization", "Bearer $token")
            }.build()
            chain.proceed(request)
        }
        .build()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}