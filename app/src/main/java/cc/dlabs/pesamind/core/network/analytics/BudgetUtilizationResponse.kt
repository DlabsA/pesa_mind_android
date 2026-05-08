package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.annotations.SerializedName

data class BudgetUtilizationResponse(
    @SerializedName("budget_utilization")
    val budgetUtilization: Double,
    val month: Int,
    val year: Int
)