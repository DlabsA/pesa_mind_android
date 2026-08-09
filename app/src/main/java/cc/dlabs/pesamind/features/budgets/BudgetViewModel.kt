package cc.dlabs.pesamind.features.budgets

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
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
    // Defaults true (unrestricted) so the next-month card doesn't flash an upsell before this
    // loads. Next-month lookahead is a Premium-only feature — see [observeBudgets].
    val isPremium: Boolean = true,
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
 * Deliberately does NOT subscribe to `StateEvent.SyncCompleted` for the *budget* fields the
 * way `DashboardViewModel`/`AnalyticsViewModel` do — budgets are genuinely local-first, so the
 * Room write `SyncWorker` performs on a successful pull *is* the trigger — [observeBudgets]'s
 * Flow collectors pick it up automatically, no event needed.
 *
 * [fetchStreak] is still 100% server-computed (`api.getDashboard()`, no local recomputation),
 * so — unlike the budget fields above — it *does* need [onStateEvent] to explicitly refetch on
 * `TransactionCreated`/`SyncCompleted`, mirroring `DashboardViewModel`'s pattern exactly.
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
            viewModelScope.launch {
                val premium = AccountManager.isPremium()
                _state.update { it.copy(isPremium = premium) }
                observeBudgets(premium)
            }
            viewModelScope.launch { fetchStreak() }
        }

        /** Streak is the one field on this screen that isn't Room-Flow-backed (see class doc
         * comment) — [DashboardViewModel]'s exact trigger set for "the streak may have
         * changed", minus its channel events, which don't affect daily-activity streaks. */
        override fun onStateEvent(event: StateEvent) {
            when (event) {
                is StateEvent.TransactionCreated, is StateEvent.SyncCompleted ->
                    viewModelScope.launch { fetchStreak(forceNetwork = true) }
                else -> {}
            }
        }

        private fun observeConnectivity() {
            viewModelScope.launch {
                networkMonitor.isConnected.collect { connected ->
                    _state.update { it.copy(isOffline = !connected) }
                }
            }
        }

        /** Sole writer of yearlyBudget/currentMonthlyBudget/nextMonthBudget — see this class's
         * doc comment for why no `StateEvent.SyncCompleted` handling is needed alongside it.
         * [isPremium] gates the next-month observer: Free tier never fetches/observes it at
         * all (not just hides it in the UI), saving the redundant Room read/collector for a
         * card that will just render an upsell teaser instead. */
        private fun observeBudgets(isPremium: Boolean) {
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
            if (isPremium) {
                viewModelScope.launch {
                    BudgetRepository.observeMonthlyBudget(nextMonth, nextYear).collect { budget ->
                        _state.update { it.copy(nextMonthBudget = budget) }
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

        // ── Refresh ───────────────────────────────────────────────────────────────

        /** Triggers the actual server pull ([SyncScheduler.triggerSyncNow]); the Room re-read
         * below is kept for the pull-to-refresh UI action's immediate feedback, mirroring
         * `ChannelViewModel`/`TransactionViewModel.refresh()` — [observeBudgets]'s live Flow
         * already picks up the sync worker's write-back once it completes. */
        fun refresh() {
            SyncScheduler.triggerSyncNow()
            viewModelScope.launch { fetchStreak(forceNetwork = true) }
            viewModelScope.launch {
                _state.update { it.copy(isRefreshing = true) }
                try {
                    val year = _state.value.displayYear.toLong()
                    val month = _state.value.displayMonth
                    val yearly = BudgetRepository.getYearlyBudgetByYear(year)
                    val monthly = BudgetRepository.getMonthlyBudgetByMonthYear(month, year)
                    val next =
                        if (_state.value.isPremium) {
                            val nextMonth = _state.value.nextMonthIndex
                            val nextYear = _state.value.nextMonthYear.toLong()
                            BudgetRepository.getMonthlyBudgetByMonthYear(nextMonth, nextYear)
                        } else {
                            null
                        }
                    _state.update { it.copy(yearlyBudget = yearly, currentMonthlyBudget = monthly, nextMonthBudget = next) }
                } finally {
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
        }

        // ── Streak (server/gamification-backed — see class doc comment) ────────────

        /** [forceNetwork] skips the cache-hit early-return: the cache-first path is a cheap
         * initial paint for [init], but a `TransactionCreated`/`SyncCompleted` event or an
         * explicit [refresh] means the cached value is now known-possibly-stale and must be
         * re-fetched, not just re-read. */
        private suspend fun fetchStreak(forceNetwork: Boolean = false) {
            if (!forceNetwork) {
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
