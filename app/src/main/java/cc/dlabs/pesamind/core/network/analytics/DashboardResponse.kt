package cc.dlabs.pesamind.core.network.analytics

import cc.dlabs.pesamind.core.network.models.AnomalySection
import com.google.gson.annotations.SerializedName

data class DashboardResponse(
    val summary: AnalyticsSummaryResponse,
    @SerializedName("spending_velocity")
    val spendingVelocity: SpendingVelocityResponse,
    val anomalies: AnomalySection,
    val budgetActualData: BudgetActualData,
    val budgetVsActualResponse: BudgetVsActualResponse,
    @SerializedName("budget_utilization")
    val budgetUtilization: BudgetUtilizationResponse,
    @SerializedName("financial_health")
    val financialHealth: FinancialHealthResponse,
    val streak: StreakResponse? = null,
)
