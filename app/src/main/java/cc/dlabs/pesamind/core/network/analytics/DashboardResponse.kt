package cc.dlabs.pesamind.core.network.analytics

import cc.dlabs.pesamind.core.network.models.AnomalySection
import com.google.gson.annotations.SerializedName

data class DashboardResponse(
    val summary: AnalyticsSummaryResponse,
    @SerializedName("spending_velocity")
    val spendingVelocity: SpendingVelocityResponse,
    val anomalies: AnomalySection,
    // The backend's /data/dashboard "budget_utilization" key is, despite the name, the full
    // budget-vs-actual object ({data, metadata, health, recommendations}) — same shape as
    // BudgetVsActualResponse, not the flat {budget_utilization, month, year} of
    // BudgetUtilizationResponse (that type belongs to a different, unrelated endpoint,
    // ApiService.getBudgetUtilization). Nullable: absent when no monthly budget is set for
    // the requested period, matching AnalyticResponse.budgetVsActual's identical nullability.
    @SerializedName("budget_utilization")
    val budgetUtilization: BudgetVsActualResponse? = null,
    @SerializedName("financial_health")
    val financialHealth: FinancialHealthResponse,
    val streak: StreakResponse? = null,
)
