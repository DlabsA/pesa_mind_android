package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.annotations.SerializedName

data class BudgetVsActualResponse(
    val data: BudgetActualData,
    val metadata: Metadata,
    val health: Health,
    val recommendations: List<Recommendation> = emptyList(),
)

data class BudgetActualData(
    val period: String,
    @SerializedName("budget_total")
    val budgetTotal: Double,
    @SerializedName("actual_total")
    val actualTotal: Double,
    val variance: Double,
    @SerializedName("variance_percent")
    val variancePercent: Double,
    val status: String,
    val items: List<BudgetActualItem> = emptyList(),
    @SerializedName("categories_on_track")
    val categoriesOnTrack: Int,
    @SerializedName("categories_over_budget")
    val categoriesOverBudget: Int,
)

data class BudgetActualItem(
    val category: String,
    val budget: Double,
    val actual: Double,
    val variance: Double,
    @SerializedName("variance_percent")
    val variancePercent: Double,
    val transactions: Int,
    @SerializedName("average_per_transaction")
    val averagePerTransaction: Double,
)
