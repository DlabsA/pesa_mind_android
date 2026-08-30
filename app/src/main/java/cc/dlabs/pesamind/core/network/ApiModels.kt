package cc.dlabs.pesamind.core.network.models

import cc.dlabs.pesamind.core.database.SyncStatus
import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    val username: String = "",
    val email: String = "",
    val password: String = "",
)

data class LoginRequest(
    val email: String = "",
    val password: String = "",
)

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String = "",
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    val error: String? = null,
    val profile: AuthProfile? = null,
)

data class AuthProfile(
    val id: String? = null,
    @SerializedName("user_id")
    val userId: String? = null,
    val username: String? = null,
    @SerializedName(value = "avatar_url", alternate = ["avatarUrl", "AvatarURL"])
    val avatarUrl: String? = null,
    val type: String? = null,
    val balance: Double? = null,
    @SerializedName("channels_onboarded")
    val channelsOnboarded: Boolean = false,
    // Non-null only while Type == "Premium" AND the account is on its 30-day
    // trial (never set for a paid Premium/Enterprise account) — backend always
    // sends the already-effective Type, so this is purely for a countdown UI.
    @SerializedName("trial_expires_at")
    val trialExpiresAt: String? = null,
)

data class AuthRegisterResponse(
    val id: String? = null,
    val email: String? = null,
    val error: String? = null,
)

// ── Google OAuth Models ────────────────────────────────────────────────────────

/**
 * Request to verify a Google ID token and check if user exists
 */
data class VerifyGoogleTokenRequest(
    @SerializedName("id_token")
    val idToken: String,
)

/**
 * Response from /api/v1/auth/google/verify-token
 * - If is_new_user=true, user must complete signup
 * - If is_new_user=false, user is already registered, tokens are returned
 */
data class VerifyGoogleTokenResponse(
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("is_new_user")
    val isNewUser: Boolean = false,
    val profile: AuthProfile? = null,
    val error: String? = null,
)

/**
 * Request to complete signup after user selects username
 */
data class CompleteGoogleSignupRequest(
    val email: String,
    @SerializedName("google_id")
    val googleId: String,
    val username: String,
    @SerializedName("google_display_name")
    val googleDisplayName: String? = null,
    @SerializedName("google_profile_photo")
    val googleProfilePhoto: String? = null,
)

/**
 * Response from /api/v1/auth/google/complete-signup
 */
data class CompleteGoogleSignupResponse(
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("is_new_user")
    val isNewUser: Boolean = false,
    val profile: AuthProfile? = null,
    val error: String? = null,
)

/**
 * Request to check if username is available
 */
data class CheckUsernameRequest(
    val username: String,
)

/**
 * Response from /api/v1/auth/google/check-username
 */
data class CheckUsernameResponse(
    val available: Boolean,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Request for Android-client-only Google sign-in.
 * This flow sends account identity fields instead of Google ID tokens.
 */
data class GoogleMobileSignInRequest(
    val email: String,
    @SerializedName("google_id")
    val googleId: String,
    @SerializedName("google_display_name")
    val googleDisplayName: String? = null,
    @SerializedName("google_profile_photo")
    val googleProfilePhoto: String? = null,
)

/**
 * Response from /api/v1/auth/google/mobile-signin
 */
data class GoogleMobileSignInResponse(
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("is_new_user")
    val isNewUser: Boolean = false,
    val profile: AuthProfile? = null,
    val error: String? = null,
)

/**
 * Request for platform-specific Google sign-in.
 * The platform field indicates which OAuth client should be used:
 * - android: GOOGLE_OAUTH_ANDROID_CLIENT_ID
 * - web: GOOGLE_CLIENT_ID
 * - ios: GOOGLE_OAUTH_IOS_CLIENT_ID
 */
data class GooglePlatformSigninRequest(
    // "android", "web", or "ios"
    val platform: String,
    val email: String,
    @SerializedName("google_id")
    val googleId: String,
    @SerializedName("google_display_name")
    val googleDisplayName: String? = null,
    @SerializedName("google_profile_photo")
    val googleProfilePhoto: String? = null,
)

/**
 * Response from /api/v1/auth/google/platform-signin
 * Same structure as mobile-signin but now with platform-aware validation
 */
data class GooglePlatformSigninResponse(
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("is_new_user")
    val isNewUser: Boolean = false,
    val profile: AuthProfile? = null,
    val error: String? = null,
)

// ── Account Models ────────────────────────────────────────────────────────────

data class Account(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val type: String = "",
    val balance: Double = 0.0,
    val trialExpiresAt: String? = null,
    /**
     * End of a *paid* period, distinct from [trialExpiresAt]. Cached so the
     * subscription card and the Settings row can show a renewal date offline;
     * written only by `SubscriptionResumer` from `GET payments/subscription`.
     */
    val premiumExpiresAt: String? = null,
)

data class TransactionRequest(
    // Client-generated UUID sent as the create idempotency key (ADR-0004 Slice A2) — the
    // server's actual handling of this field is unverified; omitted (Gson drops nulls by
    // default) for any caller that doesn't supply one. See SyncWorker.
    val id: String? = null,
    val amount: Double = 0.0,
    val type: String = "",
    val note: String = "",
    @SerializedName("channel_details_id")
    val channelId: String = "",
    // Optional, mutually exclusive — links this transaction to a debt/credit or saving goal
    // at creation time (the "choose a purpose" flow). Both are *server* ids, resolved from the
    // local FK's own serverId at push time — see OutboxPusher.pushTransactionEntry.
    @SerializedName("debt_credit_id")
    val debtCreditServerId: String? = null,
    @SerializedName("saving_goal_id")
    val savingGoalServerId: String? = null,
)

data class TransactionDetails(
    val id: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    val note: String = "",
    @SerializedName("channel_details_name")
    val channelDetailsName: String = "",
    val username: String = "",
    // Local-only field (ADR-0004 Slice A3 sync-status UI): mirrors ChannelDetails.smsNotificationEnabled's
    // @Transient pattern — never sent to or read from the server, populated from TransactionEntity.syncStatus.
    @Transient
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    // Local-only field, same @Transient pattern as [syncStatus] above — populated from
    // TransactionEntity.createdAt, used for client-side date-range filtering (TransactionListScreen).
    @Transient
    val createdAt: Long = 0L,
    // The server's real transaction date (Go's time.Time.String() format, e.g.
    // "2026-07-12 19:51:33.525482 +0000 UTC") — NOT the same thing as [createdAt] above, which
    // is local-only. TransactionRepository.reconcileFromServer parses this into
    // TransactionEntity.createdAt on a fresh insert; without it, every re-synced transaction
    // (e.g. after a logout/Room-wipe) silently got stamped with "now" instead of its actual
    // date, corrupting any local date-range filter (see that function's doc comment).
    @SerializedName("created_at")
    val serverCreatedAt: String? = null,
    // Server ids (not local) — resolved to a local DebtCreditEntity/SavingGoalEntity id in
    // TransactionRepository.reconcileFromServer, mirroring how [resolvedChannelId] is passed
    // in alongside this DTO rather than stored on it directly.
    @SerializedName("debt_credit_id")
    val debtCreditServerId: String? = null,
    @SerializedName("saving_goal_id")
    val savingGoalServerId: String? = null,
)

data class ProcessedMessageRequest(
    @SerializedName("sender_id")
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = 0,
    @SerializedName("sim_info")
    val simInfo: Int = 0,
    @SerializedName("receiving_sim_number")
    val receivingSimNumber: String = "",
)

data class ProcessedMessageResponse(
    val id: String = "",
    @SerializedName("sender_id")
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = 0,
    @SerializedName("sim_info")
    val simInfo: Int = 0,
    @SerializedName("receiving_sim_number")
    val receivingSimNumber: String = "",
    @SerializedName("created_at")
    val createdAt: String = "",
    @SerializedName("was_duplicate")
    val wasDuplicate: Boolean = false,
)

data class AnalyticsResponse(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0,
)

data class Budget(
    val id: String = "",
    val category: String = "",
    val limit: Double = 0.0,
    val spent: Double = 0.0,
)

data class UpdateProfileRequest(
    val username: String? = null,
    val email: String? = null,
)

data class ChangePasswordRequest(
    val current_password: String,
    val new_password: String,
    val confirm_password: String,
)

/**
 * `GET/PATCH users/me`.
 *
 * The server nests everything but id/email under `profile` (Go's
 * `dto.UserResponse` wrapping `ProfileData`). This class used to be flat, with
 * `username` mapped from a `name` key the server never sends — so on the one path
 * that reads it (`AccountViewModel.loadProfile` with no cached account) username,
 * type and balance all deserialised to empty, and the trial expiry was invisible.
 * [AuthProfile] already models `ProfileData` exactly, so it is reused here rather
 * than restated.
 */
data class UserResponse(
    val id: String = "",
    val email: String = "",
    val profile: AuthProfile? = null,
)

data class ChannelDetails(
    val id: String = "",
    @SerializedName("user_id")
    val userId: String = "",
    val name: String = "",
    @SerializedName("channel_type")
    val channelType: String = "",
    val description: String = "",
    @SerializedName("status")
    val status: Boolean = true,
    @SerializedName("channel_desc")
    val channelDesc: String = "",
    // Server-computed running balance (derived from transactions) — read-only from the
    // client's perspective, so it flows in on every sync/reconcile but is never part of the
    // dirty/outbox write path the way name/status/etc. are.
    @SerializedName("available_balance")
    val availableBalance: Double = 0.0,
    // Optional, shared across channel types: a phone number for MobileMoney/Airtel
    // channels, a bank account number for Bank channels.
    @SerializedName("account_number")
    val accountNumber: String? = null,
    // Local-only field: SMS notification flag (not sent to backend)
    @Transient
    val smsNotificationEnabled: Boolean = true,
    // Local-only field (ADR-0004 Slice A3 sync-status UI): not sent to or read from the server,
    // populated from ChannelEntity.syncStatus.
    @Transient
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

data class CreateChannelRequest(
    // Client-generated UUID sent as the create idempotency key (ADR-0004 Slice A2) — see
    // TransactionRequest.id for the same caveat. Omitted for the existing SMS auto-create
    // call site, which doesn't supply one.
    val id: String? = null,
    val name: String,
    @SerializedName("channel_type")
    val channelType: String,
    val description: String,
    @SerializedName("channel_desc")
    val channelDesc: String,
    val status: Boolean,
    // Optional for every channel type — a phone number for MobileMoney/Airtel, a
    // bank account number for Bank.
    @SerializedName("account_number")
    val accountNumber: String? = null,
    @SerializedName("opening_balance")
    val openingBalance: Double? = null,
)

data class UpdateChannelRequest(
    val name: String,
    val description: String,
    @SerializedName("channel_desc")
    val channelDesc: String,
    val status: Boolean,
)

data class BatchCreateChannelItem(
    val id: String? = null,
    val name: String,
    @SerializedName("channel_type")
    val channelType: String,
    val description: String = "",
    @SerializedName("channel_desc")
    val channelDesc: String = "",
    val status: Boolean = true,
    @SerializedName("account_number")
    val accountNumber: String? = null,
    @SerializedName("opening_balance")
    val openingBalance: Double? = null,
)

data class BatchCreateChannelsRequest(
    val channels: List<BatchCreateChannelItem>,
)

data class BatchCreateChannelsResponse(
    @SerializedName("already_onboarded")
    val alreadyOnboarded: Boolean = false,
    val channels: List<ChannelDetails> = emptyList(),
)

data class ApiMessageResponse(
    val message: String? = null,
    val error: String? = null,
)

// SMS Message Model
data class SMSMessage(
    val id: String = "",
    @SerializedName("sender_id")
    val senderId: String = "",
    @SerializedName("sender_name")
    val senderName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("is_read")
    val isRead: Boolean = false,
    @SerializedName("channel_id")
    val channelId: String = "",
)

// Budget Transaction Models
data class BudgetTransactionRequest(
    val name: String = "",
    val amount: Double = 0.0,
    // income, expense, saving
    val type: String = "",
)

data class BudgetTransactionOperation(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    // add, update, delete
    val action: String = "",
)

data class BudgetTransactionResponse(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    @SerializedName("created_at")
    val createdAt: String = "",
)

// Monthly Budget Models
data class CreateMonthlyBudgetRequest(
    // Client-generated UUID sent as the create idempotency key (ADR-0004) — see
    // CreateYearlyBudgetRequest.id for the same caveat/rationale.
    val id: String? = null,
    @SerializedName("yearly_budget_id")
    val yearlyBudgetId: String = "",
    val month: Int = 0,
    val year: Long = 0,
    val transactions: List<BudgetTransactionRequest> = emptyList(),
)

data class MonthlyBudgetResponse(
    val id: String = "",
    @SerializedName("user_id")
    val userId: String = "",
    @SerializedName("yearly_budget_id")
    val yearlyBudgetId: String = "",
    val month: Int = 0,
    val year: Long = 0,
    @SerializedName("total_expenditures")
    val totalExpenditures: Long = 0,
    @SerializedName("total_income")
    val totalIncome: Long = 0,
    @SerializedName("total_savings")
    val totalSavings: Long = 0,
    @SerializedName("total_transactions")
    val totalTransactions: Long = 0,
    val transactions: List<BudgetTransactionResponse> = emptyList(),
    @SerializedName("created_at")
    val createdAt: String = "",
    @SerializedName("updated_at")
    val updatedAt: String = "",
)

data class UpdateMonthlyBudgetRequest(
    val yearlyBudgetId: String? = null,
    val month: Int? = null,
    val year: Long? = null,
    val transactions: List<BudgetTransactionRequest> = emptyList(),
    @SerializedName("transaction_ops")
    val transactionOps: List<BudgetTransactionOperation> = emptyList(),
)

// Yearly Budget Models
data class CreateYearlyBudgetRequest(
    // Client-generated UUID sent as the create idempotency key (ADR-0004, same pattern as
    // CreateChannelRequest/TransactionRequest) — see TransactionRequest.id for the same
    // caveat. Nullable, not empty-string: Gson omits a null field, but the backend's
    // *uuid.UUID would fail to parse an explicit "" and reject the whole request.
    val id: String? = null,
    val year: Long = 0,
    val transactions: List<BudgetTransactionRequest> = emptyList(),
)

data class YearlyBudgetResponse(
    val id: String = "",
    @SerializedName("user_id")
    val userId: String = "",
    val year: Long = 0,
    @SerializedName("total_expenditures")
    val totalExpenditures: Long = 0,
    @SerializedName("total_income")
    val totalIncome: Long = 0,
    @SerializedName("total_savings")
    val totalSavings: Long = 0,
    @SerializedName("total_transactions")
    val totalTransactions: Long = 0,
    val transactions: List<BudgetTransactionResponse> = emptyList(),
    // or Date if you have a custom adapter
    @SerializedName("created_at")
    val createdAt: String,
    // or Date if you have a custom adapter
    @SerializedName("updated_at")
    val updatedAt: String,
    // Make it nullable since it can be null
    @SerializedName("deleted_at")
    val deletedAt: String? = null,
)

data class UpdateYearlyBudgetRequest(
    val year: Long? = null,
    val transactions: List<BudgetTransactionRequest> = emptyList(),
    @SerializedName("transaction_ops")
    val transactionOps: List<BudgetTransactionOperation> = emptyList(),
)

data class BudgetVsActualItem(
    val name: String = "",
    @SerializedName("budgeted_amount")
    val budgetedAmount: Double = 0.0,
    @SerializedName("actual_amount")
    val actualAmount: Double = 0.0,
    val variance: Double = 0.0,
    @SerializedName("variance_percent")
    val variancePercent: Double = 0.0,
    // "under", "on-track", "over"
    val status: String = "on-track",
)

data class BudgetVsActualResponse(
    val month: Int = 0,
    val year: Long = 0,
    @SerializedName("total_budgeted")
    val totalBudgeted: Double = 0.0,
    @SerializedName("total_actual")
    val totalActual: Double = 0.0,
    @SerializedName("total_variance")
    val totalVariance: Double = 0.0,
    @SerializedName("variance_percent")
    val variancePercent: Double = 0.0,
    @SerializedName("overall_status")
    val overallStatus: String = "on-track",
    @SerializedName("budget_vs_actual")
    val budgetVsActual: List<BudgetVsActualItem> = emptyList(),
)

// ─── Top-level response ───────────────────────────────────────────────────────

data class AnalyticResponse(
    // Every section field is nullable: the backend computes each of the 8 sections
    // independently and always returns 200, so one section's genuine failure (or a
    // legitimately-unavailable case, e.g. budget_vs_actual with no monthly budget set)
    // never blanks out the others — see [errors] for which sections failed and why.
    @SerializedName("summary") val summary: SummarySection? = null,
    @SerializedName("monthly_trends") val monthlyTrends: MonthlyTrendsSection? = null,
    @SerializedName("spending_velocity") val spendingVelocity: SpendingVelocitySection? = null,
    @SerializedName("budget_vs_actual") val budgetVsActual: BudgetVsActualSection? = null,
    @SerializedName("expense_forecast") val expenseForecast: ExpenseForecastSection? = null,
    @SerializedName("cash_flow_waterfall") val cashFlowWaterfall: CashFlowWaterfallSection? = null,
    @SerializedName("anomalies") val anomalies: AnomalySection? = null,
    // Same shape as the Dashboard screen's financial_health (from GET data/dashboard) —
    // reuses that type so FinancialHealthCard can be shared with zero adaptation. Null
    // without a monthly budget, same absent-not-error semantics as budget_vs_actual etc.
    @SerializedName("financial_health")
    val financialHealth: cc.dlabs.pesamind.core.network.analytics.FinancialHealthResponse? = null,
    @SerializedName("budget_utilization") val budgetUtilization: Double = 0.0,
    // Section name -> error message, only present for sections that failed server-side.
    // A section absent from both this map and its own field simply has no data yet
    // (e.g. no monthly budget), not an error.
    @SerializedName("errors") val errors: Map<String, String> = emptyMap(),
)

// ─── Summary ──────────────────────────────────────────────────────────────────

data class SummarySection(
    @SerializedName("data") val data: SummaryData,
    @SerializedName("context") val context: SummaryContext,
    @SerializedName("health") val health: Health,
    @SerializedName("recommendations") val recommendations: List<AnalyticsRecommendation>,
)

data class SummaryData(
    @SerializedName("total_income") val totalIncome: Double,
    @SerializedName("total_expense") val totalExpense: Double,
    @SerializedName("total_savings") val totalSavings: Double,
    @SerializedName("net_movement") val netMovement: Double,
    @SerializedName("transaction_count") val transactionCount: Int,
    @SerializedName("active_categories") val activeCategories: Int,
    @SerializedName("current_month") val currentMonth: String,
)

data class SummaryContext(
    @SerializedName("total_income") val totalIncome: Double,
    @SerializedName("total_expense") val totalExpense: Double,
    @SerializedName("total_savings") val totalSavings: Double,
    @SerializedName("net_movement") val netMovement: Double,
    @SerializedName("transaction_count") val transactionCount: Int,
    @SerializedName("active_categories") val activeCategories: Int,
    @SerializedName("previous_month") val previousMonth: String,
)

// ─── Health (shared) ──────────────────────────────────────────────────────────

data class Health(
    @SerializedName("score") val score: Int,
    @SerializedName("status") val status: String,
    @SerializedName("trend") val trend: String,
    @SerializedName("components") val components: Map<String, Any>? = null,
)

// ─── Monthly Trends ───────────────────────────────────────────────────────────

data class MonthlyTrendsSection(
    @SerializedName("data") val data: MonthlyTrendsData,
    @SerializedName("health") val health: Health,
    @SerializedName("recommendations") val recommendations: List<AnalyticsRecommendation>,
)

data class MonthlyTrendsData(
    @SerializedName("months") val months: List<MonthEntry>,
    @SerializedName("summary") val summary: MonthSummary,
)

data class MonthEntry(
    @SerializedName("date") val date: String,
    @SerializedName("income") val income: Double,
    @SerializedName("expense") val expense: Double,
    @SerializedName("savings") val savings: Double,
    @SerializedName("net") val net: Double,
    @SerializedName("transaction_count") val transactionCount: Int,
) {
    val shortLabel: String get() {
        val parts = date.split("-")
        if (parts.size < 2) return date
        val m = parts[1].toIntOrNull() ?: return date
        val names =
            listOf(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
            )
        return if (m in 1..12) names[m - 1] else date
    }
}

data class MonthSummary(
    @SerializedName("avg_income") val avgIncome: Double,
    @SerializedName("avg_expense") val avgExpense: Double,
    @SerializedName("avg_savings") val avgSavings: Double,
    @SerializedName("income_trend") val incomeTrend: String,
    @SerializedName("expense_trend") val expenseTrend: String,
    @SerializedName("savings_trend") val savingsTrend: String,
    @SerializedName("highest_income_month") val highestIncomeMonth: String,
    @SerializedName("highest_expense_month") val highestExpenseMonth: String,
)

// ─── Spending Velocity ────────────────────────────────────────────────────────

data class SpendingVelocitySection(
    @SerializedName("data") val data: SpendingVelocityData?,
    @SerializedName("health") val health: Health,
    @SerializedName("recommendations") val recommendations: List<AnalyticsRecommendation>,
)

data class SpendingVelocityData(
    @SerializedName("period") val period: String,
    @SerializedName("days_elapsed") val daysElapsed: Int,
    @SerializedName("days_remaining") val daysRemaining: Int,
    @SerializedName("total_spent") val totalSpent: Double,
    @SerializedName("daily_average") val dailyAverage: Double,
    @SerializedName("projected_month_end") val projectedMonthEnd: Double,
    @SerializedName("budget_limit") val budgetLimit: Double,
    @SerializedName("amount_remaining") val amountRemaining: Double,
    @SerializedName("spending_pattern") val spendingPattern: String,
    @SerializedName("alert_level") val alertLevel: String,
    @SerializedName("days_until_budget_exhausted") val daysUntilBudgetExhausted: Double?,
) {
    val daysTotal: Int get() = daysElapsed + daysRemaining
    val dayFraction: Float get() = if (daysTotal > 0) daysElapsed.toFloat() / daysTotal else 0f
    val budgetUsedFraction: Float get() = if (budgetLimit > 0) (totalSpent / budgetLimit).toFloat().coerceIn(0f, 1f) else 0f
}

// ─── Budget vs Actual ─────────────────────────────────────────────────────────

data class BvaExpenseComponent(
    val actual: Double,
    val budgeted: Double,
    val variance: Double,
    @SerializedName("variance_percent")
    val variancePercent: Double,
)

data class BvaIncomeComponent(
    val actual: Double,
    val budgeted: Double,
    val variance: Double,
    @SerializedName("variance_percent")
    val variancePercent: Double,
)

data class BvaSavingsComponent(
    val actual: Double,
    val budgeted: Double,
    val variance: Double,
    @SerializedName("variance_percent")
    val variancePercent: Double,
)

data class BvaHealthComponents(
    val expense: BvaExpenseComponent,
    val income: BvaIncomeComponent,
    val savings: BvaSavingsComponent,
)

data class BvaHealth(
    val score: Int,
    // "excellent" | "good" | "fair" | "poor"
    val status: String,
    // "stable" | "improving" | "declining"
    val trend: String,
    val components: BvaHealthComponents,
)

data class BvaData(
    // "2026-06"
    val period: String,
    @SerializedName("budget_total")
    val budgetTotal: Double,
    @SerializedName("actual_total")
    val actualTotal: Double,
    // positive = under budget (saved), negative = over
    val variance: Double,
    @SerializedName("variance_percent")
    val variancePercent: Double,
    // "under_budget" | "on_budget" | "over_budget"
    val status: String,
)

data class BvaMetadata(
    val period: String,
    @SerializedName("generated_at")
    val generatedAt: String,
    val currency: String,
    val timezone: String,
)

/**
 * The full budget_vs_actual block returned by the Django backend.
 */
data class BudgetVsActualSection(
    val data: BvaData,
    val metadata: BvaMetadata,
    val health: BvaHealth,
    // Backend returns full Recommendation objects here (type/title/message/confidence/severity),
    // same as every other section — not bare strings. This field was empty in practice until the
    // GetBudgetVsActual status bug fix started producing real recommendations, which is when the
    // List<String> mismatch first surfaced as a Gson JsonSyntaxException.
    val recommendations: List<AnalyticsRecommendation> = emptyList(),
)

data class BudgetVsActualData(
    @SerializedName("period") val period: String,
    @SerializedName("budget_total") val budgetTotal: Double,
    @SerializedName("actual_total") val actualTotal: Double,
    @SerializedName("variance") val variance: Double,
    @SerializedName("variance_percent") val variancePercent: Double,
    @SerializedName("status") val status: String,
    @SerializedName("items") val items: List<BudgetLineItem>?,
    @SerializedName("categories_on_track") val categoriesOnTrack: Int,
    @SerializedName("categories_over_budget") val categoriesOverBudget: Int,
) {
    val usageFraction: Float get() = if (budgetTotal > 0) (actualTotal / budgetTotal).toFloat().coerceIn(0f, 1f) else 0f
}

data class BudgetLineItem(
    @SerializedName("category") val category: String,
    @SerializedName("budget") val budget: Double,
    @SerializedName("actual") val actual: Double,
    @SerializedName("variance") val variance: Double,
    @SerializedName("variance_percent") val variancePercent: Double,
    @SerializedName("transactions") val transactions: Int?,
    @SerializedName("average_per_transaction") val averagePerTransaction: Double?,
) {
    val usageFraction: Float get() = if (budget > 0) (actual / budget).toFloat().coerceIn(0f, 1f) else 0f
    val status: String get() = if (budget == 0.0 || actual > budget) "over_budget" else "on_budget"
}

// ─── Expense Forecast ─────────────────────────────────────────────────────────

data class ForecastData(
    // "2026-06"
    val period: String,
    // 16
    @SerializedName("days_elapsed")
    val daysElapsed: Int,
    // 7250.0
    @SerializedName("days_remaining")
    val dailyBurnRate: Double,
    // 116000.0
    @SerializedName("actual_spent")
    val actualSpent: Double,
    // 217500.0
    @SerializedName("projected_total")
    val projectedTotal: Double,
    // 200000.0
    @SerializedName("budget_limit")
    val budgetLimit: Double,
    // 17500.0  (+ve = over, -ve = under)
    @SerializedName("amount_variance")
    val projectedVariance: Double,
    // true
    @SerializedName("will_exceed_budget")
    val willExceedBudget: Boolean,
    // 0.0–1.0
    val confidence: Double,
) {
    /** Derived — no separate backend field needed */
    val confidencePct: Int get() = (confidence * 100).toInt().coerceIn(0, 100)
}

data class ForecastRecommendation(
    // "alert" | "tip" | ...
    val type: String,
    val title: String,
    val message: String,
    // 0.0–1.0
    val confidence: Double,
    // "warning" | "critical" | "info" | "success"
    val severity: String,
)

data class ForecastMetadata(
    val period: String,
    val generatedAt: String,
    val currency: String,
    val timezone: String,
)

data class ExpenseForecastSection(
    val data: ForecastData,
    val metadata: ForecastMetadata,
    val recommendations: List<ForecastRecommendation> = emptyList(),
)

// ─── Cash Flow Waterfall ──────────────────────────────────────────────────────

data class CashFlowWaterfallSection(
    @SerializedName("data") val data: CashFlowData,
    @SerializedName("recommendations") val recommendations: List<AnalyticsRecommendation>,
)

data class CashFlowData(
    @SerializedName("opening_balance") val openingBalance: Double,
    @SerializedName("income") val income: CashFlowSide,
    @SerializedName("expenses") val expenses: CashFlowSide,
    @SerializedName("savings_transfers") val savingsTransfers: Double,
    @SerializedName("closing_balance") val closingBalance: Double,
)

data class CashFlowSide(
    @SerializedName("total") val total: Double,
    @SerializedName("sources") val sources: List<CashFlowEntry>? = null,
    @SerializedName("categories") val categories: List<CashFlowEntry>? = null,
)

data class CashFlowEntry(
    @SerializedName("channel") val channel: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("percent") val percent: Double?,
    @SerializedName("transaction_count") val transactionCount: Int?,
)

// ─── Anomalies ────────────────────────────────────────────────────────────────

// Note: AnomalyData should be defined elsewhere (likely already exists)
// ─── Shared ───────────────────────────────────────────────────────────────────

data class AnalyticsRecommendation(
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("severity") val severity: String,
)

// Also update the AnomalyData class (if not already defined):
//    kotlin
data class AnomalyData(
    @SerializedName("anomalies_detected") val anomaliesDetected: Int,
    @SerializedName("items") val items: List<AnomalyItem>,
    @SerializedName("critical_count") val criticalCount: Int,
    @SerializedName("warning_count") val warningCount: Int,
)

data class AnomalyItem(
    @SerializedName("transaction_id") val transactionId: String,
    @SerializedName("type") val type: String,
    @SerializedName("category") val category: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("normal_min") val normalMin: Double,
    @SerializedName("normal_max") val normalMax: Double,
    @SerializedName("severity") val severity: String,
    @SerializedName("sigma_multiple") val sigmaMultiple: Double,
    @SerializedName("detected_at") val detectedAt: String,
)

data class AnomalyMetadata(
    val period: String,
    val generatedAt: String,
    val currency: String,
    val timezone: String,
)

data class AnomalyRecommendation(
    val type: String,
    val title: String,
    val message: String,
    val confidence: Double,
    val severity: String,
)

data class AnomalySection(
    val data: AnomalyData,
    val metadata: AnomalyMetadata,
    val recommendations: List<AnomalyRecommendation> = emptyList(),
)

// ── Payment Models ────────────────────────────────────────────────────────────
//
// Subscription checkout. For mobile money, the app never talks to the payment
// provider directly — Flutterwave v4 credentials are full-access with no
// publishable-key equivalent, so every provider call happens on the backend.
//
// Google Play Billing is the one exception: the app talks to Play directly
// through PlayBillingManager, because that's the whole point of Play Billing
// Library and there is no way around it. But a purchase token is never treated
// as entitlement on its own — every purchase still has to be verified
// server-side via [ApiService.verifyPlayPurchase] before Premium is granted,
// exactly like every other payment method.

data class PlanResponse(
    val code: String = "",
    val name: String = "",
    val interval: String = "",
    val amount: Double = 0.0,
    val currency: String = "UGX",
    @SerializedName("play_product_id") val playProductId: String = "",
)

/**
 * Mobile money checkout only. Card is no longer offered on this (Play-distributed)
 * build — see [VerifyPlayPurchaseRequest] for the Google Play Billing path that
 * replaced it. The backend's card fields still exist (kept dormant for a future
 * non-Play channel) but this client never sends them.
 */
data class CheckoutRequest(
    @SerializedName("plan_code") val planCode: String = "",
    val method: String = "",
    val network: String = "",
    @SerializedName("phone_number") val phoneNumber: String = "",
)

/**
 * Submitted after [cc.dlabs.pesamind.core.billing.PlayBillingManager.launchPurchase]
 * completes. The purchase token is never trusted as entitlement on its own — the
 * backend re-verifies it against the Play Developer API before granting Premium.
 */
data class VerifyPlayPurchaseRequest(
    @SerializedName("product_id") val productId: String = "",
    @SerializedName("purchase_token") val purchaseToken: String = "",
)

/**
 * Invoice status is the contract between app and backend:
 * `pending` | `processing` | `paid` | `failed` | `expired`, the last three terminal.
 */
data class InvoiceResponse(
    val id: String = "",
    val status: String = "",
    val amount: Double = 0.0,
    val currency: String = "UGX",
    val reference: String = "",
    @SerializedName("plan_code") val planCode: String = "",
    @SerializedName("plan_name") val planName: String = "",
    @SerializedName("failure_reason") val failureReason: String = "",
    @SerializedName("paid_at") val paidAt: String? = null,
    @SerializedName("created_at") val createdAt: String = "",
)

/**
 * [nextAction] selects the authorisation branch:
 * - `payment_instruction` — Uganda mobile money. The customer approves with a PIN
 *   prompt on their handset; the app stays put and polls. [instruction] is the
 *   customer-facing note to display.
 * - `redirect_url` — 3DS cards. [redirectUrl] must be opened in a browser tab.
 */
data class CheckoutResponse(
    val invoice: InvoiceResponse = InvoiceResponse(),
    @SerializedName("next_action") val nextAction: String = "",
    val instruction: String = "",
    @SerializedName("redirect_url") val redirectUrl: String = "",
)

data class SubscriptionResponse(
    val tier: String = "Free",
    @SerializedName("is_premium") val isPremium: Boolean = false,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("is_trial") val isTrial: Boolean = false,
    @SerializedName("plan_code") val planCode: String = "",
    @SerializedName("plan_name") val planName: String = "",
)

// ── Lent & Borrowed / Saving Goals ────────────────────────────────────────────

/** A transaction line item embedded in a debt/credit or saving goal's hydrated response —
 * enough to render without a second fetch. Shared shape for both domains. */
data class LinkedTransactionSummary(
    val id: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    @SerializedName("occurred_at") val occurredAt: String? = null,
    @SerializedName("channel_name") val channelName: String? = null,
    @SerializedName("created_at") val createdAt: String = "",
)

data class CreateDebtCreditRequest(
    // Client-generated idempotency key — see TransactionRequest.id for the same pattern.
    val id: String? = null,
    val direction: String,
    @SerializedName("counterparty_name") val counterpartyName: String,
    @SerializedName("counterparty_phone") val counterpartyPhone: String? = null,
    @SerializedName("original_amount") val originalAmount: Double,
    val note: String? = null,
    @SerializedName("occurred_at") val occurredAt: String,
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("reminder_offsets") val reminderOffsets: List<Int>? = null,
)

/** Edits metadata only — never `original_amount`, which moves solely via linked transactions. */
data class UpdateDebtCreditRequest(
    @SerializedName("counterparty_name") val counterpartyName: String? = null,
    @SerializedName("counterparty_phone") val counterpartyPhone: String? = null,
    val note: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("reminder_offsets") val reminderOffsets: List<Int>? = null,
)

data class DebtCreditResponse(
    val id: String = "",
    // "lent" | "borrowed" — the raw enum value; never rendered directly, see
    // PesaMindStrings.DebtCredit.directionLabel.
    val direction: String = "",
    @SerializedName("counterparty_name") val counterpartyName: String = "",
    @SerializedName("counterparty_phone") val counterpartyPhone: String? = null,
    @SerializedName("original_amount") val originalAmount: Double = 0.0,
    // Server-computed.
    val outstanding: Double = 0.0,
    val note: String? = null,
    @SerializedName("occurred_at") val occurredAt: String = "",
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("reminder_offsets") val reminderOffsets: List<Int>? = null,
    @SerializedName("settled_at") val settledAt: String? = null,
    // "active" | "settled" — server-computed, precomputed alongside outstanding/settledAt.
    val status: String = "active",
    val transactions: List<LinkedTransactionSummary> = emptyList(),
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    // Local-only field (mirrors ChannelDetails/TransactionDetails's @Transient pattern): this
    // class does dual duty as both the API response DTO and the local repository's "details"
    // display model — never sent to or read from the server, populated from DebtCreditEntity.syncStatus.
    @Transient
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

data class CreateSavingGoalRequest(
    val id: String? = null,
    val name: String,
    @SerializedName("target_amount") val targetAmount: Double,
    val note: String? = null,
    @SerializedName("target_date") val targetDate: String? = null,
    @SerializedName("reminder_offsets") val reminderOffsets: List<Int>? = null,
)

/** Edits metadata only — never `target_amount`. */
data class UpdateSavingGoalRequest(
    val name: String? = null,
    val note: String? = null,
    @SerializedName("target_date") val targetDate: String? = null,
    @SerializedName("reminder_offsets") val reminderOffsets: List<Int>? = null,
)

data class SavingGoalResponse(
    val id: String = "",
    val name: String = "",
    @SerializedName("target_amount") val targetAmount: Double = 0.0,
    // Server-computed.
    val progress: Double = 0.0,
    val note: String? = null,
    @SerializedName("target_date") val targetDate: String? = null,
    @SerializedName("reminder_offsets") val reminderOffsets: List<Int>? = null,
    @SerializedName("achieved_at") val achievedAt: String? = null,
    // "active" | "achieved" — server-computed, precomputed alongside progress/achievedAt.
    val status: String = "active",
    val transactions: List<LinkedTransactionSummary> = emptyList(),
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    // Local-only field — see DebtCreditResponse.syncStatus's identical doc comment.
    @Transient
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
)

data class LinkTransactionRequest(
    @SerializedName("transaction_id") val transactionId: String,
)
