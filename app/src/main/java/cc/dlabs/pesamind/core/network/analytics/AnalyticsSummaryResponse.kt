package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.annotations.SerializedName

data class AnalyticsSummaryResponse(
    val data: SummaryData,
    val metadata: Metadata,
    val context: ContextData,
    var health: Health,
    val recommendations: List<Recommendation>
)

data class SummaryData(
    @SerializedName("total_income")
    val totalIncome: Long,
    @SerializedName("total_expense")
    val totalExpense: Long,
    @SerializedName("total_savings")
    val totalSavings: Long,
    @SerializedName("net_movement")
    val netMovement: Long,
    @SerializedName("transaction_count")
    val transactionCount: Int,
    @SerializedName("active_categories")
    val activeCategories: Int,
    @SerializedName("current_month")
    val currentMonth: String
)

data class Metadata(
    val period: String,
    @SerializedName("generated_at")
    val generatedAt: String,
    val currency: String,
    val timezone: String
)

data class ContextData(
    @SerializedName("total_income")
    val totalIncome: Long,
    @SerializedName("total_expense")
    val totalExpense: Long,
    @SerializedName("total_savings")
    val totalSavings: Long,
    @SerializedName("net_movement")
    val netMovement: Long,
    @SerializedName("transaction_count")
    val transactionCount: Int,
    @SerializedName("active_categories")
    val activeCategories: Int,
    @SerializedName("previous_month")
    val previousMonth: String
)
data class FinancialHealthResponse(
    val data: Health,
    val metadata: Metadata,
    val recommendations: List<Recommendation> = emptyList()
)
data class Health(
    @SerializedName("health_score")
    val score: Int,
    val status: String,
    val trend: String,
    val components: Map<String, Component>? = null,
    val strengths: List<String>? = null,
    val weaknesses: List<String>? = null
)

data class Component(
    val score: Int,
    val status: String,
    val description: String,
    val trend: String
)
data class Recommendation(
    val type: String,
    val title: String,
    val message: String,
    val confidence: Double,
    val severity: String
)