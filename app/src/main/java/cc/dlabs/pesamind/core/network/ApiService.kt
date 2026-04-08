package cc.dlabs.pesamind.core.network
import cc.dlabs.pesamind.core.network.models.Account
import cc.dlabs.pesamind.core.network.models.AnalyticsResponse
import cc.dlabs.pesamind.core.network.models.AuthResponse
import cc.dlabs.pesamind.core.network.models.Budget
import cc.dlabs.pesamind.core.network.models.LoginRequest
import cc.dlabs.pesamind.core.network.models.RefreshRequest
import cc.dlabs.pesamind.core.network.models.RegisterRequest
import cc.dlabs.pesamind.core.network.models.Transaction
import cc.dlabs.pesamind.core.network.models.TransactionRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("users/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<AuthResponse>

    @GET("accounts")
    suspend fun getAccounts(): Response<List<Account>>

    @POST("transactions")
    suspend fun createTransaction(@Body body: TransactionRequest): Response<Transaction>

    @GET("transactions")
    suspend fun getTransactions(): Response<List<Transaction>>

    @GET("analytics")
    suspend fun getAnalytics(): Response<AnalyticsResponse>

    @GET("budgets")
    suspend fun getBudgets(): Response<List<Budget>>

    @GET("health")
    suspend fun health(): Response<Unit>
}
