package cc.dlabs.pesamind.features.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.analytics.AnalyticsSummaryResponse
import cc.dlabs.pesamind.core.network.analytics.AnomaliesResponse
import cc.dlabs.pesamind.core.network.analytics.BudgetUtilizationResponse
import cc.dlabs.pesamind.core.network.analytics.CashFlowWaterfallResponse
import cc.dlabs.pesamind.core.network.analytics.ExpenseForecastResponse
import cc.dlabs.pesamind.core.network.analytics.MonthlyTrendsResponse
import cc.dlabs.pesamind.core.network.analytics.SpendingVelocityResponse
import cc.dlabs.pesamind.core.network.analytics.BudgetVsActualResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private var currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

    init {
        loadUserProfile()
        loadAllData()
    }

    fun loadAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Parallel requests with async
                val summaryDeferred = async { api.getAnalyticsSummary() }
                val velocityDeferred = async { api.getSpendingVelocity() }
                val trendsDeferred = async { api.getMonthlyTrends() }
                val utilizationDeferred = async { api.getBudgetUtilization(currentMonth, currentYear) }
                val forecastDeferred = async { api.getExpenseForecast() }
                val cashFlowDeferred = async { api.getCashFlowWaterfall(currentYear, currentMonth) }
                val budgetActualDeferred = async { api.getBudgetVsActual(currentYear, currentMonth) }
                val anomaliesDeferred = async { api.getAnomalies() }

                val summaryResp = summaryDeferred.await()
                val velocityResp = velocityDeferred.await()
                val trendsResp = trendsDeferred.await()
                val utilizationResp = utilizationDeferred.await()
                val forecastResp = forecastDeferred.await()
                val cashFlowResp = cashFlowDeferred.await()
                val budgetActualResp = budgetActualDeferred.await()
                val anomaliesResp = anomaliesDeferred.await()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = summaryResp.body(),
                        spendingVelocity = velocityResp.body(),
                        monthlyTrends = trendsResp.body(),
                        budgetUtilization = utilizationResp.body(),
                        expenseForecast = forecastResp.body(),
                        cashFlow = cashFlowResp.body(),
                        budgetVsActual = budgetActualResp.body(),
                        anomalies = anomaliesResp.body(),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun refresh() = loadAllData()

    private fun loadUserProfile() {
        viewModelScope.launch {
            val user = AccountManager.getAccount()
            val initials = buildInitials(user.username)
            _uiState.update {
                it.copy(
                    userDisplayName = user.username,
                    userInitials = initials
                )
            }
        }
    }
    private fun buildInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }
}


data class AnalyticsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val userDisplayName: String = "",
    val userInitials: String = "",
    val summary: AnalyticsSummaryResponse? = null,
    val spendingVelocity: SpendingVelocityResponse? = null,
    val monthlyTrends: MonthlyTrendsResponse? = null,
    val budgetUtilization: BudgetUtilizationResponse? = null,
    val expenseForecast: ExpenseForecastResponse? = null,
    val cashFlow: CashFlowWaterfallResponse? = null,
    val budgetVsActual: BudgetVsActualResponse? = null,
    val anomalies: AnomaliesResponse? = null
)