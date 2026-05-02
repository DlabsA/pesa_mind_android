package cc.dlabs.pesamind.features.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient.api
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import cc.dlabs.pesamind.core.storage.BudgetManager
import cc.dlabs.pesamind.core.storage.AccountManager
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── UI State ─────────────────────────────────────────────────────────────────

data class DashboardUiState(
    // Loading flags
    val isLoadingYearly: Boolean = false,
    val isLoadingMonthly: Boolean = false,
    val isRefreshing: Boolean = false,

    // Data
    val yearlyBudget: YearlyBudgetResponse? = null,
    val currentMonthlyBudget: MonthlyBudgetResponse? = null,
    val nextMonthBudget: MonthlyBudgetResponse? = null,

    // Period context
    val displayYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val displayMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1, // 1-based

    // User profile
    val userDisplayName: String = "",
    val userInitials: String = "",
    val userAvatarUrl: String? = null,

    // UX
    val error: String? = null,
    val isDarkMode: Boolean = false,

    // Computed from data
    val isFromCache: Boolean = false
) {
    /** Net balance = income - expenditure for the current monthly budget */
    val monthlyBalance: Long
        get() = (currentMonthlyBudget?.totalIncome ?: 0L) -
                (currentMonthlyBudget?.totalExpenditures ?: 0L)

    val isMonthlyDeficit: Boolean get() = monthlyBalance < 0L

    val nextMonthIndex: Int get() = if (displayMonth == 12) 1 else displayMonth + 1
    val nextMonthYear: Int get() = if (displayMonth == 12) displayYear + 1 else displayYear

    val hasNextMonthBudget: Boolean get() = nextMonthBudget != null

    val isLoading: Boolean get() = isLoadingYearly || isLoadingMonthly
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class BudgetViewModel : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        loadUserProfile()
        loadDashboard()
    }

    // ── User ──────────────────────────────────────────────────────────────────

    private  fun loadUserProfile() {
        viewModelScope.launch {
        val user = AccountManager.getAccount()
        val initials = buildInitials(user.username)
        _state.update {
            it.copy(
                userDisplayName = user.username,
                userInitials = initials,
                userAvatarUrl = user.email
            )
        }}
    }

    private fun buildInitials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }

    // ── Dark mode ─────────────────────────────────────────────────────────────

    fun toggleDarkMode() {
        _state.update { it.copy(isDarkMode = !it.isDarkMode) }
    }

    // ── Load dashboard ────────────────────────────────────────────────────────

    fun loadDashboard(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val now = Calendar.getInstance()
            val month = now.get(Calendar.MONTH) + 1
            val year = now.get(Calendar.YEAR).toLong()

            // Show cached data immediately while fetching fresh
            if (!forceRefresh) {
                loadFromCache(month, year.toInt())
            }

            _state.update {
                it.copy(
                    isLoadingYearly = true,
                    isLoadingMonthly = true,
                    isRefreshing = forceRefresh,
                    error = null
                )
            }

            val yearlyDeferred = async { fetchYearlyBudget(year.toInt()) }
            val monthlyDeferred = async { fetchMonthlyBudget(month, year) }
            val nextMonthDeferred = async {
                val nextM = if (month == 12) 1 else month + 1
                val nextY = if (month == 12) year + 1 else year
                fetchMonthlyBudget(nextM, nextY, isNext = true)
            }

            yearlyDeferred.await()
            monthlyDeferred.await()
            nextMonthDeferred.await()

            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun refresh() = loadDashboard(forceRefresh = true)

    // ── Cache load ────────────────────────────────────────────────────────────

    private suspend fun loadFromCache(month: Int, year: Int) {
        val cachedYearly = BudgetManager.getYearlyBudgets()
            .find { it.year == year.toLong() }
        val cachedMonthly = BudgetManager.getMonthlyBudgetByMonthYear(month, year.toLong())
        val nextM = if (month == 12) 1 else month + 1
        val nextY = if (month == 12) year + 1 else year
        val cachedNext = BudgetManager.getMonthlyBudgetByMonthYear(nextM, nextY.toLong())

        if (cachedYearly != null || cachedMonthly != null) {
            _state.update {
                it.copy(
                    yearlyBudget = cachedYearly ?: it.yearlyBudget,
                    currentMonthlyBudget = cachedMonthly ?: it.currentMonthlyBudget,
                    nextMonthBudget = cachedNext,
                    isFromCache = true
                )
            }
        }
    }

    // ── Network fetches ───────────────────────────────────────────────────────

    private suspend fun fetchYearlyBudget(year: Int) {
        try {
            val response = api.getYearlyBudgets()
            if (response.isSuccessful) {
                val match = response.body()?.find { it.year == year.toLong() }
                BudgetManager.saveYearlyBudgets(response.body() ?: emptyList())
                _state.update {
                    it.copy(
                        yearlyBudget = match,
                        isLoadingYearly = false,
                        isFromCache = false
                    )
                }
            } else {
                _state.update { it.copy(isLoadingYearly = false) }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoadingYearly = false,
                    error = "Could not load yearly budget"
                )
            }
        }
    }

    private suspend fun fetchMonthlyBudget(
        month: Int,
        year: Long,
        isNext: Boolean = false
    ) {
        try {
            val response = api.getMonthlyBudgetByMonthYear(month, year)
            if (response.isSuccessful) {
                val budget = response.body()
                if (budget != null) {
                    BudgetManager.saveCurrentMonthlyBudget(budget)
                    _state.update {
                        if (isNext) it.copy(nextMonthBudget = budget, isLoadingMonthly = false)
                        else it.copy(
                            currentMonthlyBudget = budget,
                            isLoadingMonthly = false,
                            isFromCache = false
                        )
                    }
                } else {
                    _state.update { it.copy(isLoadingMonthly = false) }
                }
            } else {
                _state.update { it.copy(isLoadingMonthly = false) }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoadingMonthly = false,
                    error = if (!isNext) "Could not load monthly budget" else it.error
                )
            }
        }
    }

    // ── Error handling ────────────────────────────────────────────────────────

    fun clearError() = _state.update { it.copy(error = null) }
}