package cc.dlabs.pesamind.core.network
import cc.dlabs.pesamind.core.network.analytics.AnalyticsSummaryResponse
import cc.dlabs.pesamind.core.network.analytics.BudgetUtilizationResponse
import cc.dlabs.pesamind.core.network.analytics.BudgetVsActualResponse
import cc.dlabs.pesamind.core.network.analytics.CashFlowWaterfallResponse
import cc.dlabs.pesamind.core.network.analytics.DashboardResponse
import cc.dlabs.pesamind.core.network.analytics.FinancialHealthResponse
import cc.dlabs.pesamind.core.network.analytics.MonthlyTrendsResponse
import cc.dlabs.pesamind.core.network.analytics.SpendingVelocityResponse
import cc.dlabs.pesamind.core.network.models.AnalyticResponse
import cc.dlabs.pesamind.core.network.models.AnomalySection
import cc.dlabs.pesamind.core.network.models.ApiMessageResponse
import cc.dlabs.pesamind.core.network.models.AuthRegisterResponse
import cc.dlabs.pesamind.core.network.models.AuthResponse
import cc.dlabs.pesamind.core.network.models.BatchCreateChannelsRequest
import cc.dlabs.pesamind.core.network.models.BatchCreateChannelsResponse
import cc.dlabs.pesamind.core.network.models.ChangePasswordRequest
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.CheckUsernameRequest
import cc.dlabs.pesamind.core.network.models.CheckUsernameResponse
import cc.dlabs.pesamind.core.network.models.CheckoutRequest
import cc.dlabs.pesamind.core.network.models.CheckoutResponse
import cc.dlabs.pesamind.core.network.models.CompleteGoogleSignupRequest
import cc.dlabs.pesamind.core.network.models.CompleteGoogleSignupResponse
import cc.dlabs.pesamind.core.network.models.CreateChannelRequest
import cc.dlabs.pesamind.core.network.models.CreateDebtCreditRequest
import cc.dlabs.pesamind.core.network.models.CreateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.CreateSavingGoalRequest
import cc.dlabs.pesamind.core.network.models.CreateYearlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.DebtCreditResponse
import cc.dlabs.pesamind.core.network.models.ExpenseForecastSection
import cc.dlabs.pesamind.core.network.models.GoogleMobileSignInRequest
import cc.dlabs.pesamind.core.network.models.GoogleMobileSignInResponse
import cc.dlabs.pesamind.core.network.models.GooglePlatformSigninRequest
import cc.dlabs.pesamind.core.network.models.GooglePlatformSigninResponse
import cc.dlabs.pesamind.core.network.models.InvoiceResponse
import cc.dlabs.pesamind.core.network.models.LinkTransactionRequest
import cc.dlabs.pesamind.core.network.models.LoginRequest
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.PlanResponse
import cc.dlabs.pesamind.core.network.models.ProcessedMessageRequest
import cc.dlabs.pesamind.core.network.models.ProcessedMessageResponse
import cc.dlabs.pesamind.core.network.models.RefreshRequest
import cc.dlabs.pesamind.core.network.models.RegisterRequest
import cc.dlabs.pesamind.core.network.models.SavingGoalResponse
import cc.dlabs.pesamind.core.network.models.SubscriptionResponse
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.network.models.TransactionRequest
import cc.dlabs.pesamind.core.network.models.UpdateChannelRequest
import cc.dlabs.pesamind.core.network.models.UpdateDebtCreditRequest
import cc.dlabs.pesamind.core.network.models.UpdateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.UpdateProfileRequest
import cc.dlabs.pesamind.core.network.models.UpdateSavingGoalRequest
import cc.dlabs.pesamind.core.network.models.UpdateYearlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.UserResponse
import cc.dlabs.pesamind.core.network.models.VerifyPlayPurchaseRequest
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth/login")
    suspend fun login(
        @Body body: LoginRequest,
    ): Response<AuthResponse>

    @POST("users/register")
    suspend fun register(
        @Body body: RegisterRequest,
    ): Response<AuthRegisterResponse>

    @POST("auth/refresh")
    suspend fun refresh(
        @Body body: RefreshRequest,
    ): Response<AuthResponse>

    // ── Google OAuth Endpoints ────────────────────────────────────────────────

    @POST("auth/google/platform-signin")
    suspend fun platformGoogleSignIn(
        @Body body: GooglePlatformSigninRequest,
    ): Response<GooglePlatformSigninResponse>

    @POST("auth/google/mobile-signin")
    suspend fun mobileGoogleSignIn(
        @Body body: GoogleMobileSignInRequest,
    ): Response<GoogleMobileSignInResponse>

    @POST("auth/google/complete-signup")
    suspend fun completeGoogleSignup(
        @Body body: CompleteGoogleSignupRequest,
    ): Response<CompleteGoogleSignupResponse>

    @POST("auth/google/check-username")
    suspend fun checkUsername(
        @Body body: CheckUsernameRequest,
    ): Response<CheckUsernameResponse>

    // Account endpoint
    @GET("users/me")
    suspend fun getProfile(): Response<UserResponse>

    @PATCH("users/me")
    suspend fun updateProfile(
        @Body body: UpdateProfileRequest,
    ): Response<UserResponse>

    @POST("users/me/change-password")
    suspend fun changePassword(
        @Body body: ChangePasswordRequest,
    ): Response<Unit>

    @GET("categories")
    suspend fun getChannels(): Response<List<ChannelDetails>>

    @POST("categories")
    suspend fun createChannel(
        @Body body: CreateChannelRequest,
    ): Response<ChannelDetails>

    @POST("categories/batch")
    suspend fun batchCreateChannels(
        @Body body: BatchCreateChannelsRequest,
    ): Response<BatchCreateChannelsResponse>

    @GET("categories/channel-type")
    suspend fun getChannelsByType(
        @Query("channel_type") channelType: String,
    ): Response<List<ChannelDetails>>

    @GET("categories/status")
    suspend fun getChannelsByStatus(
        @Query("status") status: Boolean,
    ): Response<List<ChannelDetails>>

    @PATCH("categories/{id}")
    suspend fun updateChannel(
        @Path("id") id: String,
        @Body body: UpdateChannelRequest,
    ): Response<ApiMessageResponse>

    @DELETE("categories/{id}")
    suspend fun deleteChannel(
        @Path("id") id: String,
    ): Response<ApiMessageResponse>

    @GET("transactions")
    suspend fun getTransactions(): Response<List<TransactionDetails>>

    @GET("transactions/by-channel")
    suspend fun getTransactionsByChannel(
        @Query("channelID") channelId: String,
    ): Response<List<TransactionDetails>>

    @GET("transactions/by-date-range")
    suspend fun getTransactionsByDateRange(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
    ): Response<List<TransactionDetails>>

    @POST("transactions")
    suspend fun createTransaction(
        @Body body: TransactionRequest,
    ): Response<TransactionDetails>

    @POST("processed-messages")
    suspend fun createProcessedMessage(
        @Body body: ProcessedMessageRequest,
    ): Response<ProcessedMessageResponse>

    // ── Lent & Borrowed / Saving Goals ───────────────────────────────────────

    @GET("debt-credits")
    suspend fun getDebtCredits(
        @Query("status") status: String = "all",
    ): Response<List<DebtCreditResponse>>

    @POST("debt-credits")
    suspend fun createDebtCredit(
        @Body body: CreateDebtCreditRequest,
    ): Response<DebtCreditResponse>

    @PATCH("debt-credits/{id}")
    suspend fun updateDebtCredit(
        @Path("id") id: String,
        @Body body: UpdateDebtCreditRequest,
    ): Response<DebtCreditResponse>

    @DELETE("debt-credits/{id}")
    suspend fun deleteDebtCredit(
        @Path("id") id: String,
    ): Response<ApiMessageResponse>

    @POST("debt-credits/{id}/link-transaction")
    suspend fun linkDebtCreditTransaction(
        @Path("id") id: String,
        @Body body: LinkTransactionRequest,
    ): Response<DebtCreditResponse>

    @POST("debt-credits/{id}/unlink-transaction")
    suspend fun unlinkDebtCreditTransaction(
        @Path("id") id: String,
        @Body body: LinkTransactionRequest,
    ): Response<DebtCreditResponse>

    @GET("saving-goals")
    suspend fun getSavingGoals(
        @Query("status") status: String = "all",
    ): Response<List<SavingGoalResponse>>

    @POST("saving-goals")
    suspend fun createSavingGoal(
        @Body body: CreateSavingGoalRequest,
    ): Response<SavingGoalResponse>

    @PATCH("saving-goals/{id}")
    suspend fun updateSavingGoal(
        @Path("id") id: String,
        @Body body: UpdateSavingGoalRequest,
    ): Response<SavingGoalResponse>

    @DELETE("saving-goals/{id}")
    suspend fun deleteSavingGoal(
        @Path("id") id: String,
    ): Response<ApiMessageResponse>

    @POST("saving-goals/{id}/link-transaction")
    suspend fun linkSavingGoalTransaction(
        @Path("id") id: String,
        @Body body: LinkTransactionRequest,
    ): Response<SavingGoalResponse>

    @POST("saving-goals/{id}/unlink-transaction")
    suspend fun unlinkSavingGoalTransaction(
        @Path("id") id: String,
        @Body body: LinkTransactionRequest,
    ): Response<SavingGoalResponse>

    // Budget endpoints
    @GET("budgets/monthly")
    suspend fun getMonthlyBudgets(): Response<List<MonthlyBudgetResponse>>

    @POST("budgets/monthly")
    suspend fun createMonthlyBudget(
        @Body body: CreateMonthlyBudgetRequest,
    ): Response<MonthlyBudgetResponse>

    @GET("budgets/monthly/by-month-year")
    suspend fun getMonthlyBudgetByMonthYear(
        @Query("month") month: Int,
        @Query("year") year: Long,
    ): Response<MonthlyBudgetResponse>

    @GET("budgets/monthly/by-yearly/{id}")
    suspend fun getMonthlyBudgetsByYearlyId(
        @Path("id") yearlyBudgetId: String,
    ): Response<List<MonthlyBudgetResponse>>

    @GET("budgets/monthly/{id}")
    suspend fun getMonthlyBudgetById(
        @Path("id") id: String,
    ): Response<MonthlyBudgetResponse>

    @PATCH("budgets/monthly/{id}")
    suspend fun updateMonthlyBudget(
        @Path("id") id: String,
        @Body body: UpdateMonthlyBudgetRequest,
    ): Response<MonthlyBudgetResponse>

    @DELETE("budgets/monthly/{id}")
    suspend fun deleteMonthlyBudget(
        @Path("id") id: String,
    ): Response<ApiMessageResponse>

    @GET("budgets/yearly")
    suspend fun getYearlyBudgets(): Response<List<YearlyBudgetResponse>>

    @GET("budgets/yearly/by-year")
    suspend fun getYearlyBudgetsByYear(
        @Query("year") year: Long,
    ): Response<YearlyBudgetResponse>

    @POST("budgets/yearly")
    suspend fun createYearlyBudget(
        @Body body: CreateYearlyBudgetRequest,
    ): Response<YearlyBudgetResponse>

    @GET("budgets/yearly/{id}")
    suspend fun getYearlyBudgetById(
        @Path("id") id: String,
    ): Response<YearlyBudgetResponse>

    @PATCH("budgets/yearly/{id}")
    suspend fun updateYearlyBudget(
        @Path("id") id: String,
        @Body body: UpdateYearlyBudgetRequest,
    ): Response<YearlyBudgetResponse>

    @DELETE("budgets/yearly/{id}")
    suspend fun deleteYearlyBudget(
        @Path("id") id: String,
    ): Response<ApiMessageResponse>

    @GET("analytics/summary")
    suspend fun getAnalyticsSummary(): Response<AnalyticsSummaryResponse>

    @GET("analytics/budget-utilization/by-month")
    suspend fun getBudgetUtilization(
        @Query("month") month: Int,
        @Query("year") year: Int,
    ): Response<BudgetUtilizationResponse>

    @GET("analytics/spending-velocity")
    suspend fun getSpendingVelocity(): Response<SpendingVelocityResponse>

    @GET("analytics/monthly-trends")
    suspend fun getMonthlyTrends(): Response<MonthlyTrendsResponse>

    @GET("analytics/expense-forecast")
    suspend fun getExpenseForecast(): Response<ExpenseForecastSection>

    @GET("analytics/cash-flow-waterfall")
    suspend fun getCashFlowWaterfall(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): Response<CashFlowWaterfallResponse>

    @GET("analytics/budget-vs-actual")
    suspend fun getBudgetVsActual(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): Response<BudgetVsActualResponse>

    @GET("analytics/anomalies")
    suspend fun getAnomalies(): Response<AnomalySection>

    @GET("analytics/financial-health")
    suspend fun getFinancialHealth(): Response<FinancialHealthResponse>

    @GET("data/dashboard")
    suspend fun getDashboard(
        @Query("month") month: Int,
        @Query("year") year: Int,
    ): Response<DashboardResponse>

    @GET("data/analytics")
    suspend fun getAnalytics(
        @Query("period") period: String = "month",
    ): Response<AnalyticResponse>

    // ── Payments ──────────────────────────────────────────────────────────────

    @GET("payments/plans")
    suspend fun getPlans(): Response<List<PlanResponse>>

    @POST("payments/checkout")
    suspend fun checkout(
        @Body body: CheckoutRequest,
    ): Response<CheckoutResponse>

    /**
     * Polling target. The backend reconciles a stale invoice against the payment
     * provider before answering, which is how a payment settles without webhooks.
     */
    @GET("payments/invoices/{id}")
    suspend fun getInvoice(
        @Path("id") id: String,
    ): Response<InvoiceResponse>

    @GET("payments/invoices")
    suspend fun getInvoices(): Response<List<InvoiceResponse>>

    @GET("payments/subscription")
    suspend fun getSubscription(): Response<SubscriptionResponse>

    /**
     * Settles a Google Play Billing purchase already completed via
     * [cc.dlabs.pesamind.core.billing.PlayBillingManager]. Unlike [checkout], the
     * purchase has already happened by the time this is called — this is purely
     * server-side verification against the Play Developer API before entitlement
     * is granted.
     */
    @POST("payments/play/verify")
    suspend fun verifyPlayPurchase(
        @Body body: VerifyPlayPurchaseRequest,
    ): Response<InvoiceResponse>
}
