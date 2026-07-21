package cc.dlabs.pesamind.features.budgets

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.network.ApiClient.api
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.BudgetManager
import cc.dlabs.pesamind.core.storage.StreakSessionCache
import cc.dlabs.pesamind.core.utils.StreakUiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────────────────────

data class BudgetUiState(
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
    // 1-based
    val displayMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    // User profile
    val userDisplayName: String = "",
    val userInitials: String = "",
    val userAvatarUrl: String? = null,
    // UX
    val error: String? = null,
    val isDarkMode: Boolean = false,
    val isOffline: Boolean = false,
    // Computed from data
    val isFromCache: Boolean = false,
    val lastUpdated: Long? = null,
    val streakCount: Int = 0,
    val streakLastActiveDate: String? = null,
) {
    /** Net balance = income - expenditure for the current monthly budget */
    val monthlyBalance: Long
        get() =
            (currentMonthlyBudget?.totalIncome ?: 0L) -
                (currentMonthlyBudget?.totalExpenditures ?: 0L)

    val isMonthlyDeficit: Boolean get() = monthlyBalance < 0L

    val nextMonthIndex: Int get() = if (displayMonth == 12) 1 else displayMonth + 1
    val nextMonthYear: Int get() = if (displayMonth == 12) displayYear + 1 else displayYear

    val hasNextMonthBudget: Boolean get() = nextMonthBudget != null

    val isLoading: Boolean get() = isLoadingYearly || isLoadingMonthly

    // ── New computed properties ────────────────────────────────────────────

    val greetingText: String
        get() {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 0..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
        }

    val currentPeriodLabel: String
        get() {
            val monthNames =
                listOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December",
                )
            return "${monthNames[displayMonth - 1]} $displayYear"
        }
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class BudgetViewModel
    @Inject
    constructor(
        private val networkMonitor: NetworkMonitor,
    ) : UnifiedViewModel() {
        companion object {
            private const val TAG = "BudgetViewModel"
        }

        private val _state = MutableStateFlow(BudgetUiState())
        val state: StateFlow<BudgetUiState> = _state.asStateFlow()
        private var pendingSync = false

        init {
            loadUserProfile()
            observeConnectivity()
            loadDashboard(forceRefresh = false)
        }

        private fun observeConnectivity() {
            viewModelScope.launch {
                networkMonitor.isConnected.collect { connected ->
                    if (!connected) {
                        _state.update { it.copy(isOffline = true) }
                        return@collect
                    }

                    _state.update { it.copy(isOffline = false) }
                    if (pendingSync) {
                        loadDashboard(forceRefresh = true)
                    }
                }
            }
        }

        // ── User ──────────────────────────────────────────────────────────────────

        private fun loadUserProfile() {
            viewModelScope.launch {
                val user = AccountManager.getAccount()
                val initials = buildInitials(user.username)
                _state.update {
                    it.copy(
                        userDisplayName = user.username,
                        userInitials = initials,
                        userAvatarUrl = user.email,
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
                val hasCache =
                    if (!forceRefresh) {
                        loadFromCache(month, year.toInt())
                    } else {
                        false
                    }

                val cacheIsFresh =
                    if (!forceRefresh && hasCache) {
                        val monthlyFresh = !BudgetManager.isMonthlyBudgetsCacheStale()
                        val yearlyFresh = !BudgetManager.isYearlyBudgetsCacheStale()
                        monthlyFresh && yearlyFresh
                    } else {
                        false
                    }

                if (!forceRefresh && cacheIsFresh) {
                    pendingSync = false
                    _state.update { it.copy(isOffline = !networkMonitor.isConnectedNow) }
                    return@launch
                }

                val canFetchNetwork = networkMonitor.isConnectedNow
                if (!canFetchNetwork) {
                    pendingSync = hasCache
                    _state.update {
                        it.copy(
                            isLoadingYearly = false,
                            isLoadingMonthly = false,
                            isRefreshing = false,
                            isOffline = true,
                        )
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        isLoadingYearly = true,
                        isLoadingMonthly = true,
                        isRefreshing = forceRefresh,
                        error = null,
                    )
                }

                try {
                    val yearlyDeferred = async { fetchYearlyBudget(year.toInt()) }
                    val monthlyDeferred = async { fetchMonthlyBudget(month, year) }
                    val nextMonthDeferred =
                        async {
                            val nextM = if (month == 12) 1 else month + 1
                            val nextY = if (month == 12) year + 1 else year
                            fetchMonthlyBudget(nextM, nextY, isNext = true)
                        }
                    val streakDeferred = async { fetchStreak() }

                    yearlyDeferred.await()
                    monthlyDeferred.await()
                    nextMonthDeferred.await()
                    streakDeferred.await()
                } finally {
                    pendingSync = false
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
        }

        fun refresh() = loadDashboard(forceRefresh = true)

        // ── Cache load ────────────────────────────────────────────────────────────

        private suspend fun loadFromCache(
            month: Int,
            year: Int,
        ): Boolean {
            val cachedYearly =
                BudgetManager.getYearlyBudgets()
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
                        isFromCache = true,
                        isOffline = !networkMonitor.isConnectedNow,
                    )
                }
                return true
            }

            return false
        }

        // ── Network fetches ───────────────────────────────────────────────────────

        private suspend fun fetchYearlyBudget(year: Int) {
            try {
                val response = api.getYearlyBudgets()
                when {
                    response.isSuccessful -> {
                        val match = response.body()?.find { it.year == year.toLong() }
                        BudgetManager.saveYearlyBudgets(response.body() ?: emptyList())
                        _state.update {
                            it.copy(
                                yearlyBudget = match,
                                isLoadingYearly = false,
                                isFromCache = false,
                                isOffline = false,
                            )
                        }
                    }
                    response.code() == 404 -> {
                        // Resource doesn't exist - not an error, just no data yet
                        Log.d(TAG, "No yearly budget available for year $year (404)")
                        _state.update {
                            it.copy(
                                yearlyBudget = null,
                                isLoadingYearly = false,
                                isOffline = false,
                                // Don't set error - this is normal
                            )
                        }
                    }
                    else -> {
                        // Other HTTP errors (401 is handled by TokenRefreshInterceptor)
                        _state.update {
                            it.copy(
                                isLoadingYearly = false,
                                error = "Failed to load yearly budget (${response.code()})",
                                isOffline = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                pendingSync = true
                _state.update {
                    it.copy(
                        isLoadingYearly = false,
                        error = "Could not load yearly budget: ${e.message}",
                        isOffline = true,
                    )
                }
            }
        }

        private suspend fun fetchMonthlyBudget(
            month: Int,
            year: Long,
            isNext: Boolean = false,
        ) {
            try {
                val response = api.getMonthlyBudgetByMonthYear(month, year)
                when {
                    response.isSuccessful -> {
                        val budget = response.body()
                        if (budget != null) {
                            BudgetManager.saveCurrentMonthlyBudget(budget)
                            _state.update {
                                if (isNext) {
                                    it.copy(nextMonthBudget = budget, isLoadingMonthly = false, isOffline = false)
                                } else {
                                    it.copy(
                                        currentMonthlyBudget = budget,
                                        isLoadingMonthly = false,
                                        isFromCache = false,
                                        isOffline = false,
                                    )
                                }
                            }
                        } else {
                            _state.update { it.copy(isLoadingMonthly = false, isOffline = false) }
                        }
                    }
                    response.code() == 404 -> {
                        // Budget doesn't exist yet - not an error, normal state
                        Log.d(TAG, "No budget available for $month/$year (404)")
                        _state.update {
                            if (isNext) {
                                it.copy(nextMonthBudget = null, isLoadingMonthly = false, isOffline = false)
                            } else {
                                it.copy(
                                    currentMonthlyBudget = null,
                                    isLoadingMonthly = false,
                                    isOffline = false,
                                    // Don't set error - this is normal
                                )
                            }
                        }
                    }
                    else -> {
                        // Other HTTP errors (401 is handled by TokenRefreshInterceptor)
                        _state.update {
                            it.copy(
                                isLoadingMonthly = false,
                                error = if (!isNext) "Failed to load monthly budget (${response.code()})" else it.error,
                                isOffline = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isNext) pendingSync = true
                _state.update {
                    it.copy(
                        isLoadingMonthly = false,
                        error = if (!isNext) "Could not load monthly budget: ${e.message}" else it.error,
                        isOffline = true,
                    )
                }
            }
        }

        private suspend fun fetchStreak() {
            val cachedStreak = StreakSessionCache.get()
            if (cachedStreak != null) {
                _state.update {
                    it.copy(
                        streakCount = cachedStreak.count,
                        streakLastActiveDate = cachedStreak.lastActiveDate,
                    )
                }
                return
            }

            try {
                val response = api.getDashboard()
                if (response.isSuccessful) {
                    val streak = response.body()?.streak
                    if (streak != null) {
                        StreakSessionCache.set(
                            count = streak.currentStreak,
                            lastActiveDate = streak.lastActiveDate,
                        )
                    }
                    _state.update {
                        it.copy(
                            streakCount = streak?.currentStreak ?: it.streakCount,
                            streakLastActiveDate = streak?.lastActiveDate ?: it.streakLastActiveDate,
                        )
                    }
                }
            } catch (_: Exception) {
                // Keep existing streak values when request fails.
            }
        }

        val streakDrawable: Int?
            get() = StreakUiHelper.drawable(_state.value.streakCount, _state.value.streakLastActiveDate)

        val streakLabel: String
            get() = StreakUiHelper.label(_state.value.streakCount)

        // ── Error handling ────────────────────────────────────────────────────────

        fun clearError() = _state.update { it.copy(error = null) }
    }
