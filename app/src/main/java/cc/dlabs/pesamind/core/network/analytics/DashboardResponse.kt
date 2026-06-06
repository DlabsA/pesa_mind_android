package cc.dlabs.pesamind.core.network.analytics

import com.google.gson.annotations.SerializedName

data class DashboardResponse(
    val summary: AnalyticsSummaryResponse,
    @SerializedName("spending_velocity")
    val spendingVelocity: SpendingVelocityResponse,
    val anomalies: AnomaliesResponse,
    @SerializedName("budget_utilization")
    val budgetUtilization: BudgetUtilizationResponse,
    @SerializedName("financial_health")
    val financialHealth: FinancialHealthResponse
)