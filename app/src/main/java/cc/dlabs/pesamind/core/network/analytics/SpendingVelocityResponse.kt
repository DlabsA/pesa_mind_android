package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.annotations.SerializedName

data class SpendingVelocityResponse(
    val data: VelocityData,
    val metadata: Metadata,
    val health: Health,
    val recommendations: List<Recommendation>
)

data class VelocityData(
    val period: String,
    @SerializedName("days_elapsed")
    val daysElapsed: Int,
    @SerializedName("days_remaining")
    val daysRemaining: Int,
    @SerializedName("total_spent")
    val totalSpent: Long,
    @SerializedName("daily_average")
    val dailyAverage: Double,
    @SerializedName("projected_month_end")
    val projectedMonthEnd: Double,
    @SerializedName("budget_limit")
    val budgetLimit: Double,
    @SerializedName("amount_remaining")
    val amountRemaining: Double,
    @SerializedName("spending_pattern")
    val spendingPattern: String,
    @SerializedName("alert_level")
    val alertLevel: String,
    @SerializedName("days_until_budget_exhausted")
    val daysUntilBudgetExhausted: Double
)