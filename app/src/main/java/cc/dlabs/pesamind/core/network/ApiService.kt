interface ApiService {
    @POST("users/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

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

