package cc.dlabs.pesamind.core.network.models

import com.google.gson.annotations.SerializedName
import cc.dlabs.pesamind.core.network.analytics.AnomalyData
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
    val type: String? = null,
    val balance: Double? = null
)

data class AuthRegisterResponse(
    val id: String? = null,
    val email: String? = null,
    val error: String? = null,
)

data class Account(
    val id: String = "",
    val username: String = "",
    val email: String = "",
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
    val summary:             SummarySection,
    val monthlyTrends:       MonthlyTrendsSection,
    val spendingVelocity:    SpendingVelocitySection,
    val budgetVsActual:      BudgetVsActualSection,
    val expenseForecast:     ExpenseForecastSection,
    val cashFlowWaterfall:   CashFlowWaterfallSection,
    val anomalies:           AnomalySection,
    val budgetUtilization:   Double,
)
// ─── Summary ──────────────────────────────────────────────────────────────────

data class SummarySection(
    val data:            SummaryData,
    val context:         SummaryContext,
    val health:          Health,
    val recommendations: List<AnalyticsRecommendation>,
)

data class SummaryData(
    val totalIncome:       Double,
    val totalExpense:      Double,
    val totalSavings:      Double,
    val netMovement:       Double,
    val transactionCount:  Int,
    val activeCategories:  Int,
    val currentMonth:      String,
)

data class SummaryContext(
    val totalIncome:      Double,
    val totalExpense:     Double,
    val totalSavings:     Double,
    val netMovement:      Double,
    val transactionCount: Int,
    val activeCategories: Int,
    val previousMonth:    String,
)

// ─── Monthly Trends ───────────────────────────────────────────────────────────

data class MonthlyTrendsSection(
    val data:            MonthlyTrendsData,
    val health:          Health,
    val recommendations: List<AnalyticsRecommendation>,
)

data class MonthlyTrendsData(
    val months:  List<MonthEntry>,
    val summary: MonthSummary,
)

data class MonthEntry(
    val date:             String,
    val income:           Double,
    val expense:          Double,
    val savings:          Double,
    val net:              Double,
    val transactionCount: Int,
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
    val avgIncome:           Double,
    val avgExpense:          Double,
    val avgSavings:          Double,
    val incomeTrend:         String,
    val expenseTrend:        String,
    val savingsTrend:        String,
    val highestIncomeMonth:  String,
    val highestExpenseMonth: String,
)

// ─── Spending Velocity ────────────────────────────────────────────────────────

data class SpendingVelocitySection(
    val data:            SpendingVelocityData?,
    val health:          Health,
    val recommendations: List<AnalyticsRecommendation>,
)

data class SpendingVelocityData(
    val period:                    String,
    val daysElapsed:               Int,
    val daysRemaining:             Int,
    val totalSpent:                Double,
    val dailyAverage:              Double,
    val projectedMonthEnd:         Double,
    val budgetLimit:               Double,
    val amountRemaining:           Double,
    val spendingPattern:           String,
    val alertLevel:                String,
    val daysUntilBudgetExhausted:  Double?,
) {
    val daysTotal: Int get() = daysElapsed + daysRemaining
    val dayFraction: Float get() = if (daysTotal > 0) daysElapsed.toFloat() / daysTotal else 0f
    val budgetUsedFraction: Float get() = if (budgetLimit > 0) (totalSpent / budgetLimit).toFloat().coerceIn(0f, 1f) else 0f
}

// ─── Budget vs Actual ─────────────────────────────────────────────────────────

data class BudgetVsActualSection(
    val data:            BudgetVsActualData,
    val health:          Health,
    val recommendations: List<AnalyticsRecommendation>,
)

data class BudgetVsActualData(
    val period:                 String,
    val budgetTotal:            Double,
    val actualTotal:            Double,
    val variance:               Double,
    val variancePercent:        Double,
    val status:                 String,
    val items:                  List<BudgetLineItem>?,
    val categoriesOnTrack:      Int,
    val categoriesOverBudget:   Int,
) {
    val usageFraction: Float get() = if (budgetTotal > 0) (actualTotal / budgetTotal).toFloat().coerceIn(0f, 1f) else 0f
}

data class BudgetLineItem(
    val category:               String,
    val budget:                 Double,
    val actual:                 Double,
    val variance:               Double,
    val variancePercent:        Double,
    val transactions:           Int?,
    val averagePerTransaction:  Double?,
) {
    val usageFraction: Float get() = if (budget > 0) (actual / budget).toFloat().coerceIn(0f, 1f) else 0f
    val status: String get() = if (budget == 0.0 || actual > budget) "over_budget" else "on_budget"
}

// ─── Expense Forecast ─────────────────────────────────────────────────────────

data class ExpenseForecastSection(
    val data:            ExpenseForecastData,
    val recommendations: List<AnalyticsRecommendation>,
)

data class ExpenseForecastData(
    val period:             String,
    val daysElapsed:        Int,
    val dailyBurnRate:      Double,
    val actualSpent:        Double,
    val projectedTotal:     Double,
    val budgetLimit:        Double,
    val projectedVariance:  Double,
    val willExceedBudget:   Boolean,
    val confidence:         Double,
) {
    val confidencePct: Int get() = (confidence * 100).toInt()
}

// ─── Cash Flow Waterfall ──────────────────────────────────────────────────────

data class CashFlowWaterfallSection(
    val data:            CashFlowData,
    val recommendations: List<AnalyticsRecommendation>,
)

data class CashFlowData(
    val openingBalance:   Double,
    val income:           CashFlowSide,
    val expenses:         CashFlowSide,
    val savingsTransfers: Double,
    val closingBalance:   Double,
)

data class CashFlowSide(
    val total:      Double,
    val sources:    List<CashFlowEntry>? = null,
    val categories: List<CashFlowEntry>? = null,
)

data class CashFlowEntry(
    val channel:          String,
    val amount:           Double,
    val percent:          Double?,
    val transactionCount: Int?,
)

// ─── Anomalies ────────────────────────────────────────────────────────────────

data class AnomalySection(
    val data:            AnomalyData,
    val recommendations: List<AnalyticsRecommendation>,
)

// ─── Shared ───────────────────────────────────────────────────────────────────

data class AnalyticsRecommendation(
    val type:       String,
    val title:      String,
    val message:    String,
    val confidence: Double,
    val severity:   String,
)
