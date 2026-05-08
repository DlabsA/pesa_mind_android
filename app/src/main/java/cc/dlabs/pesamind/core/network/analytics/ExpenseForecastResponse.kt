package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.annotations.SerializedName

data class ExpenseForecastResponse(
    val data: ForecastData,
    val metadata: Metadata,
    val recommendations: List<Recommendation>
)

data class ForecastData(
    val period: String,
    @SerializedName("days_elapsed")
    val daysElapsed: Int,
    @SerializedName("daily_burn_rate")
    val dailyBurnRate: Double,
    @SerializedName("actual_spent")
    val actualSpent: Long,
    @SerializedName("projected_total")
    val projectedTotal: Double,
    @SerializedName("budget_limit")
    val budgetLimit: Double,
    @SerializedName("projected_variance")
    val projectedVariance: Double,
    @SerializedName("will_exceed_budget")
    val willExceedBudget: Boolean,
    val confidence: Double
)