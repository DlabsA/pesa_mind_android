package cc.dlabs.pesamind.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.analytics.*
import cc.dlabs.pesamind.core.storage.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        loadDashboardData()
    }

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

    fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val summaryDeferred = async { api.getAnalyticsSummary() }
                val velocityDeferred = async { api.getSpendingVelocity() }
                val anomaliesDeferred = async { api.getAnomalies() }
                val utilizationDeferred = async { api.getBudgetUtilization(getCurrentMonth(), getCurrentYear()) }

                val summaryResp = summaryDeferred.await()
                val velocityResp = velocityDeferred.await()
                val anomaliesResp = anomaliesDeferred.await()
                val utilizationResp = utilizationDeferred.await()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = summaryResp.body(),
                        spendingVelocity = velocityResp.body(),
                        anomalies = anomaliesResp.body(),
                        budgetUtilization = utilizationResp.body(),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load dashboard")
                }
            }
        }
    }

    fun refresh() = loadDashboardData()
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun getCurrentMonth() = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
    private fun getCurrentYear() = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val userDisplayName: String = "",
    val userInitials: String = "",
    val summary: AnalyticsSummaryResponse? = null,
    val spendingVelocity: SpendingVelocityResponse? = null,
    val anomalies: AnomaliesResponse? = null,
    val budgetUtilization: BudgetUtilizationResponse? = null
)