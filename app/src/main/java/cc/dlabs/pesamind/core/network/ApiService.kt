package cc.dlabs.pesamind.core.network
import cc.dlabs.pesamind.core.network.models.Account
import cc.dlabs.pesamind.core.network.models.AnalyticsResponse
import cc.dlabs.pesamind.core.network.models.AuthRegisterResponse
import cc.dlabs.pesamind.core.network.models.AuthResponse
import cc.dlabs.pesamind.core.network.models.Budget
import cc.dlabs.pesamind.core.network.models.ChangePasswordRequest
import cc.dlabs.pesamind.core.network.models.LoginRequest
import cc.dlabs.pesamind.core.network.models.RefreshRequest
import cc.dlabs.pesamind.core.network.models.RegisterRequest
import cc.dlabs.pesamind.core.network.models.Transaction
import cc.dlabs.pesamind.core.network.models.TransactionRequest
import cc.dlabs.pesamind.core.network.models.UpdateProfileRequest
import cc.dlabs.pesamind.core.network.models.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import cc.dlabs.pesamind.core.network.models.ApiMessageResponse
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.CreateChannelRequest
import cc.dlabs.pesamind.core.network.models.UpdateChannelRequest
import retrofit2.http.DELETE
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("users/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthRegisterResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<AuthResponse>

    // Account endpoint
    @GET("users/me")
    suspend fun getProfile(): Response<UserResponse>

    @PATCH("users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): Response<UserResponse>

    @POST("users/me/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>

//    @GET("accounts")
//    suspend fun getAccounts(): Response<List<Account>>
//
//    @POST("transactions")
//    suspend fun createTransaction(@Body body: TransactionRequest): Response<Transaction>
//
//    @GET("transactions")
//    suspend fun getTransactions(): Response<List<Transaction>>
//
//    @GET("analytics")
//    suspend fun getAnalytics(): Response<AnalyticsResponse>
//
//    @GET("budgets")
//    suspend fun getBudgets(): Response<List<Budget>>
//
//    @GET("health")
//    suspend fun health(): Response<Unit>

    @GET("categories")
    suspend fun getChannels(): Response<List<ChannelDetails>>

    @POST("categories")
    suspend fun createChannel(@Body body: CreateChannelRequest): Response<ChannelDetails>

    @GET("categories/channel-type")
    suspend fun getChannelsByType(
        @Query("channel_type") channelType: String
    ): Response<List<ChannelDetails>>

    @GET("categories/status")
    suspend fun getChannelsByStatus(
        @Query("status") status: Boolean
    ): Response<List<ChannelDetails>>

    @PUT("categories/{id}")
    suspend fun updateChannel(
        @Path("id") id: String,
        @Body body: UpdateChannelRequest
    ): Response<ApiMessageResponse>

    @DELETE("categories/{id}")
    suspend fun deleteChannel(
        @Path("id") id: String
    ): Response<ApiMessageResponse>
}

