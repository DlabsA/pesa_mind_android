package cc.dlabs.pesamind.features.budgets

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.BudgetRepository
import cc.dlabs.pesamind.core.network.ApiClient.api
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.StreakSessionCache
import cc.dlabs.pesamind.core.sync.SyncScheduler
import cc.dlabs.pesamind.core.utils.StreakUiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
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
    // Computed from data — with Room as the real local cache (ADR-0004 Slice B), there is no
    // more fetched-vs-cached distinction once Room is the only read path. Always false: dirty
    // state isn't currently surfaced by BudgetRepository's plain response Flows.
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

/**
 * Room-backed (ADR-0004 Slice B) — reads go through [BudgetRepository]'s Flows, never
 * `ApiClient`/`BudgetManager` directly. `BudgetManager`'s DataStore cache was never actually
 * live in production (its `init()` was never called from `PesaMindApp.onCreate()`), so this
 * isn't just a rewire — it's the first time budgets have had a working local cache at all.
 *
 * Deliberately does NOT subscribe to `StateEvent.SyncCompleted` the way `DashboardViewModel`/
 * `AnalyticsViewModel` do — those two are still 100% server-computed and need an explicit
 * "go refetch" trigger; budgets are now genuinely local-first, so the Room write `SyncWorker`
 * performs on a successful pull *is* the trigger — [observeBudgets]'s Flow collectors pick it
 * up automatically, no event needed.
 *
 * [fetchStreak]/`api.getDashboard()` stay network-backed, unchanged — gamification streak is
 * out of this fix's scope.
 */
@HiltViewModel
class BudgetViewModel
    @Inject
    constructor(
        private val networkMonitor: NetworkMonitor,
    ) : UnifiedViewModel() {
        private val _state = MutableStateFlow(BudgetUiState())
        val state: StateFlow<BudgetUiState> = _state.asStateFlow()

        init {
            loadUserProfile()
            observeConnectivity()
            observeBudgets()
            viewModelScope.launch { fetchStreak() }
        }

        private fun observeConnectivity() {
            viewModelScope.launch {
                networkMonitor.isConnected.collect { connected ->
                    _state.update { it.copy(isOffline = !connected) }
                }
            }
        }

        /** Sole writer of yearlyBudget/currentMonthlyBudget/nextMonthBudget — see this class's
         * doc comment for why no `StateEvent.SyncCompleted` handling is needed alongside it. */
        private fun observeBudgets() {
            val year = _state.value.displayYear.toLong()
            val month = _state.value.displayMonth
            val nextMonth = _state.value.nextMonthIndex
            val nextYear = _state.value.nextMonthYear.toLong()

            viewModelScope.launch {
                BudgetRepository.observeYearlyBudget(year).collect { budget ->
                    _state.update { it.copy(yearlyBudget = budget, isLoadingYearly = false) }
                }
            }
            viewModelScope.launch {
                BudgetRepository.observeMonthlyBudget(month, year).collect { budget ->
                    _state.update {
                        it.copy(
                            currentMonthlyBudget = budget,
                            isLoadingMonthly = false,
                            lastUpdated = System.currentTimeMillis(),
                        )
                    }
                }
            }
            viewModelScope.launch {
                BudgetRepository.observeMonthlyBudget(nextMonth, nextYear).collect { budget ->
                    _state.update { it.copy(nextMonthBudget = budget) }
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

        // ── Refresh ───────────────────────────────────────────────────────────────

        /** Triggers the actual server pull ([SyncScheduler.triggerSyncNow]); the Room re-read
         * below is kept for the pull-to-refresh UI action's immediate feedback, mirroring
         * `ChannelViewModel`/`TransactionViewModel.refresh()` — [observeBudgets]'s live Flow
         * already picks up the sync worker's write-back once it completes. */
        fun refresh() {
            SyncScheduler.triggerSyncNow()
            viewModelScope.launch {
                _state.update { it.copy(isRefreshing = true) }
                try {
                    val year = _state.value.displayYear.toLong()
                    val month = _state.value.displayMonth
                    val nextMonth = _state.value.nextMonthIndex
                    val nextYear = _state.value.nextMonthYear.toLong()
                    val yearly = BudgetRepository.getYearlyBudgetByYear(year)
                    val monthly = BudgetRepository.getMonthlyBudgetByMonthYear(month, year)
                    val next = BudgetRepository.getMonthlyBudgetByMonthYear(nextMonth, nextYear)
                    _state.update { it.copy(yearlyBudget = yearly, currentMonthlyBudget = monthly, nextMonthBudget = next) }
                } finally {
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
        }

        // ── Streak (unchanged — server/gamification-backed, out of A9's scope) ──────

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
                val now = Calendar.getInstance()
                val response =
                    api.getDashboard(
                        month = now.get(Calendar.MONTH) + 1,
                        year = now.get(Calendar.YEAR),
                    )
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
