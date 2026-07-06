package cc.dlabs.pesamind.core.network.models

import com.google.gson.annotations.SerializedName
import cc.dlabs.pesamind.core.network.analytics.Health

data class RegisterRequest(
    val username: String = "",
    val email: String = "",
    val password: String = ""
)

data class LoginRequest(
    val email: String = "",
    val password: String = ""
)

data class RefreshRequest(
    @SerializedName("refresh_token")
    val refreshToken: String = ""
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    val error: String? = null,
    val profile: AuthProfile? = null
)

data class AuthProfile(
    val id: String? = null,
    @SerializedName("user_id")
    val userId: String? = null,
    val username: String? = null,
    @SerializedName(value = "avatar_url", alternate = ["avatarUrl", "AvatarURL"])
    val avatarUrl: String? = null,
    val type: String? = null,
    val balance: Double? = null
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
    val idToken: String
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
    val error: String? = null
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
    val googleProfilePhoto: String? = null
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
    val error: String? = null
)

/**
 * Request to check if username is available
 */
data class CheckUsernameRequest(
    val username: String
)

/**
 * Response from /api/v1/auth/google/check-username
 */
data class CheckUsernameResponse(
    val available: Boolean,
    val message: String? = null,
    val error: String? = null
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
    val googleProfilePhoto: String? = null
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
    val error: String? = null
)

/**
 * Request for platform-specific Google sign-in.
 * The platform field indicates which OAuth client should be used:
 * - android: GOOGLE_OAUTH_ANDROID_CLIENT_ID
 * - web: GOOGLE_CLIENT_ID
 * - ios: GOOGLE_OAUTH_IOS_CLIENT_ID
 */
data class GooglePlatformSigninRequest(
    val platform: String, // "android", "web", or "ios"
    val email: String,
    @SerializedName("google_id")
    val googleId: String,
    @SerializedName("google_display_name")
    val googleDisplayName: String? = null,
    @SerializedName("google_profile_photo")
    val googleProfilePhoto: String? = null
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
    val error: String? = null
)

// ── Account Models ────────────────────────────────────────────────────────────

data class Account(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val type: String = "",
    val balance: Double = 0.0
)

data class TransactionRequest(
    val amount: Double = 0.0,
    val type: String = "",
    val note: String = "",
    @SerializedName("channel_details_id")
    val channelId: String = ""
)

data class TransactionDetails(
    val id: String = "",
    val amount: Double = 0.0,
    val type: String = "",
    val note: String = "",
    @SerializedName("channel_details_name")
    val channelDetailsName: String = "",
    val username: String = "",
)

data class AnalyticsResponse(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0
)

data class Budget(
    val id: String = "",
    val category: String = "",
    val limit: Double = 0.0,
    val spent: Double = 0.0
)

data class UpdateProfileRequest(
    val username: String? = null,
    val email: String? = null
)

data class ChangePasswordRequest(
    val current_password: String,
    val new_password: String,
    val confirm_password: String
)

data class UserResponse(
    val id: String,
    @SerializedName("name")
    val username: String,
    val email: String,
    @SerializedName(value = "avatar_url", alternate = ["avatarUrl", "AvatarURL"])
    val avatarUrl: String = "",
    val balance: Double,
    val type: String
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
    // Local-only field: SMS notification flag (not sent to backend)
    @Transient
    val smsNotificationEnabled: Boolean = true
)

data class CreateChannelRequest(
    val name: String,
    @SerializedName("channel_type")
    val channelType: String,
    val description: String,
    @SerializedName("channel_desc")
    val channelDesc: String,
    val status: Boolean
)

data class UpdateChannelRequest(
    val name: String,
    val description: String,
    val status: Boolean
)

data class ApiMessageResponse(
    val message: String? = null,
    val error: String? = null
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
	val channelId: String = ""
)

// Budget Transaction Models
data class BudgetTransactionRequest(
	val name: String = "",
	val amount: Double = 0.0,
	val type: String = "" // income, expense, saving
)

data class BudgetTransactionOperation(
	val id: String = "",
	val name: String = "",
	val amount: Double = 0.0,
	val type: String = "",
	val action: String = "" // add, update, delete
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
	@SerializedName("yearly_budget_id")
	val yearlyBudgetId: String = "",
	val month: Int = 0,
	val year: Long = 0,
	val transactions: List<BudgetTransactionRequest> = emptyList()
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
	val updatedAt: String = ""
)

data class UpdateMonthlyBudgetRequest(
    val yearlyBudgetId: String? = null,
	val month: Int? = null,
	val year: Long? = null,
	val transactions: List<BudgetTransactionRequest> = emptyList(),
	@SerializedName("transaction_ops")
	val transactionOps: List<BudgetTransactionOperation> = emptyList()
)

// Yearly Budget Models
data class CreateYearlyBudgetRequest(
	val year: Long = 0,
	val transactions: List<BudgetTransactionRequest> = emptyList()
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
    @SerializedName("created_at")
    val createdAt: String,  // or Date if you have a custom adapter

    @SerializedName("updated_at")
    val updatedAt: String,  // or Date if you have a custom adapter

    @SerializedName("deleted_at")
    val deletedAt: String? = null  // Make it nullable since it can be null
)

data class UpdateYearlyBudgetRequest(
	val year: Long? = null,
	val transactions: List<BudgetTransactionRequest> = emptyList(),
	@SerializedName("transaction_ops")
	val transactionOps: List<BudgetTransactionOperation> = emptyList()
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
    val status: String = "on-track" // "under", "on-track", "over"
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
    val budgetVsActual: List<BudgetVsActualItem> = emptyList()
)


// ─── Top-level response ───────────────────────────────────────────────────────


data class AnalyticResponse(
    @SerializedName("summary") val summary: SummarySection,
    @SerializedName("monthly_trends") val monthlyTrends: MonthlyTrendsSection,
    @SerializedName("spending_velocity") val spendingVelocity: SpendingVelocitySection,
    @SerializedName("budget_vs_actual") val budgetVsActual: BudgetVsActualSection,
    @SerializedName("expense_forecast") val expenseForecast: ExpenseForecastSection,
    @SerializedName("cash_flow_waterfall") val cashFlowWaterfall: CashFlowWaterfallSection,
    @SerializedName("anomalies") val anomalies: AnomalySection,
    @SerializedName("budget_utilization") val budgetUtilization: Double,
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
        val names = listOf("Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec")
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
    val status: String,   // "excellent" | "good" | "fair" | "poor"
    val trend: String,    // "stable" | "improving" | "declining"
    val components: BvaHealthComponents,
)

data class BvaData(
    val period: String,           // "2026-06"
    @SerializedName("budget_total")
    val budgetTotal: Double,
    @SerializedName("actual_total")
    val actualTotal: Double,
    val variance: Double,         // positive = under budget (saved), negative = over
    @SerializedName("variance_percent")
    val variancePercent: Double,
    val status: String,           // "under_budget" | "on_budget" | "over_budget"
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
    val recommendations: List<String> = emptyList(),
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
    val period: String,             // "2026-06"
    @SerializedName("days_elapsed")
    val daysElapsed: Int,           // 16
    @SerializedName("days_remaining")
    val dailyBurnRate: Double,      // 7250.0
    @SerializedName("actual_spent")
    val actualSpent: Double,        // 116000.0
    @SerializedName("projected_total")
    val projectedTotal: Double,     // 217500.0
    @SerializedName("budget_limit")
    val budgetLimit: Double,        // 200000.0
    @SerializedName("amount_variance")
    val projectedVariance: Double,  // 17500.0  (+ve = over, -ve = under)
    @SerializedName("will_exceed_budget")
    val willExceedBudget: Boolean,  // true
    val confidence: Double,         // 0.0–1.0
) {
    /** Derived — no separate backend field needed */
    val confidencePct: Int get() = (confidence * 100).toInt().coerceIn(0, 100)
}

data class ForecastRecommendation(
    val type: String,       // "alert" | "tip" | ...
    val title: String,
    val message: String,
    val confidence: Double, // 0.0–1.0
    val severity: String,   // "warning" | "critical" | "info" | "success"
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
//Also update the AnomalyData class (if not already defined):
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

