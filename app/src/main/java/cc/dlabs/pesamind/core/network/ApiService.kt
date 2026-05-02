package cc.dlabs.pesamind.core.network
import cc.dlabs.pesamind.core.network.models.AuthRegisterResponse
import cc.dlabs.pesamind.core.network.models.AuthResponse
import cc.dlabs.pesamind.core.network.models.ChangePasswordRequest
import cc.dlabs.pesamind.core.network.models.LoginRequest
import cc.dlabs.pesamind.core.network.models.RefreshRequest
import cc.dlabs.pesamind.core.network.models.RegisterRequest
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
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.network.models.TransactionRequest
import cc.dlabs.pesamind.core.network.models.UpdateChannelRequest
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query
import cc.dlabs.pesamind.core.network.models.CreateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.CreateYearlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.UpdateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.UpdateYearlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse


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

    @PATCH("categories/{id}")
    suspend fun updateChannel(
        @Path("id") id: String,
        @Body body: UpdateChannelRequest
    ): Response<ApiMessageResponse>

    @DELETE("categories/{id}")
    suspend fun deleteChannel(
        @Path("id") id: String
    ): Response<ApiMessageResponse>

    @GET("transactions")
    suspend fun getTransactions(): Response<List<TransactionDetails>>

    @POST("transactions")
    suspend fun createTransaction(@Body body: TransactionRequest): Response<TransactionDetails>

    // Budget endpoints
    @GET("budgets/monthly")
    suspend fun getMonthlyBudgets(): Response<List<MonthlyBudgetResponse>>

    @POST("budgets/monthly")
    suspend fun createMonthlyBudget(@Body body: CreateMonthlyBudgetRequest): Response<MonthlyBudgetResponse>

    @GET("budgets/monthly/by-month-year")
    suspend fun getMonthlyBudgetByMonthYear(
        @Query("month") month: Int,
        @Query("year") year: Long
    ): Response<MonthlyBudgetResponse>

    @GET("budgets/monthly/by-yearly/{id}")
    suspend fun getMonthlyBudgetsByYearlyId(
        @Path("id") yearlyBudgetId: String
    ): Response<List<MonthlyBudgetResponse>>

    @GET("budgets/monthly/{id}")
    suspend fun getMonthlyBudgetById(@Path("id") id: String): Response<MonthlyBudgetResponse>

    @PATCH("budgets/monthly/{id}")
    suspend fun updateMonthlyBudget(
        @Path("id") id: String,
        @Body body: UpdateMonthlyBudgetRequest
    ): Response<MonthlyBudgetResponse>

    @DELETE("budgets/monthly/{id}")
    suspend fun deleteMonthlyBudget(@Path("id") id: String): Response<ApiMessageResponse>

    @GET("budgets/yearly")
    suspend fun getYearlyBudgets(): Response<List<YearlyBudgetResponse>>

    @POST("budgets/yearly")
    suspend fun createYearlyBudget(@Body body: CreateYearlyBudgetRequest): Response<YearlyBudgetResponse>

    @GET("budgets/yearly/{id}")
    suspend fun getYearlyBudgetById(@Path("id") id: String): Response<YearlyBudgetResponse>

    @PATCH("budgets/yearly/{id}")
    suspend fun updateYearlyBudget(
        @Path("id") id: String,
        @Body body: UpdateYearlyBudgetRequest
    ): Response<YearlyBudgetResponse>

    @DELETE("budgets/yearly/{id}")
    suspend fun deleteYearlyBudget(@Path("id") id: String): Response<ApiMessageResponse>
}
