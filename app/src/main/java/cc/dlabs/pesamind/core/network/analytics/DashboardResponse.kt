package cc.dlabs.pesamind.core.network.analytics

import cc.dlabs.pesamind.core.network.models.AnomalySection
import com.google.gson.annotations.SerializedName

data class DashboardResponse(
    val summary: AnalyticsSummaryResponse,
    // spending_velocity, anomalies and financial_health are Premium-gated server-side
    // and come back as JSON `null` for a Free-tier account — confirmed against
    // production: `{"anomalies":null,"budget_utilization":null,"financial_health":null,
    // "spending_velocity":null,...}`.
    //
    // They must be declared nullable even though a Kotlin non-null type "looks" safer:
    // Gson populates fields reflectively and honours neither Kotlin nullability nor a
    // default value, so a non-null declaration does not prevent a null — it only hides
    // it from the compiler until it NPEs at the use site. That is exactly how the
    // dashboard crashed for every Free user (DashboardScreen's anomalies section).
    @SerializedName("spending_velocity")
    val spendingVelocity: SpendingVelocityResponse? = null,
    val anomalies: AnomalySection? = null,
    // The backend's /data/dashboard "budget_utilization" key is, despite the name, the full
    // budget-vs-actual object ({data, metadata, health, recommendations}) — same shape as
    // BudgetVsActualResponse, not the flat {budget_utilization, month, year} of
    // BudgetUtilizationResponse (that type belongs to a different, unrelated endpoint,
    // ApiService.getBudgetUtilization). Nullable: absent when no monthly budget is set for
    // the requested period, matching AnalyticResponse.budgetVsActual's identical nullability.
    @SerializedName("budget_utilization")
    val budgetUtilization: BudgetVsActualResponse? = null,
    @SerializedName("financial_health")
    val financialHealth: FinancialHealthResponse? = null,
    val streak: StreakResponse? = null,
)
