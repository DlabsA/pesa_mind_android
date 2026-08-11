package cc.dlabs.pesamind.features.analytics

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.ChannelSpend
import cc.dlabs.pesamind.core.data.DayOfWeekSpend
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.data.monthRangeMillis
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.AnalyticResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.StreakSessionCache
import cc.dlabs.pesamind.core.utils.StreakUiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import javax.inject.Inject

// ─── Phase ────────────────────────────────────────────────────────────────────

sealed interface AnalyticsPhase {
    data object Idle : AnalyticsPhase

    data object Loading : AnalyticsPhase

    data object Loaded : AnalyticsPhase

    data object Empty : AnalyticsPhase

    data class Error(val message: String) : AnalyticsPhase
}

// ─── Period ───────────────────────────────────────────────────────────────────
// Applies only to the Transaction-based insights section — Budget-based cards always compute
// for the current month server-side regardless of this toggle (budgets don't have a lifetime
// analog), so the backend ignores `period` entirely for those.

enum class AnalyticsPeriod(val queryValue: String) {
    MONTH("month"),
    LIFETIME("lifetime"),
}

// ─── UI State ─────────────────────────────────────────────────────────────────

data class AnalyticsUiState(
    val analytics: AnalyticResponse? = null,
    val phase: AnalyticsPhase = AnalyticsPhase.Idle,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    // epoch millis
    val lastUpdated: Long? = null,
    val streakCount: Int = 0,
    val streakLastActiveDate: String? = null,
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val isPeriodChanging: Boolean = false,
    // Locally-computed (Room-only, no backend endpoint) — see [AnalyticsViewModel]'s
    // `observeLocalAnalytics`.
    // Defaults true (unrestricted) so gated cards don't flash an upsell before this loads —
    // the backend response itself is already correctly gated regardless (see fetchFromNetwork),
    // this is purely for rendering the right placeholder/toggle state.
    val isPremium: Boolean = true,
)

// ─── ViewModel ────────────────────────────────────────────────────────────

@HiltViewModel
class AnalyticsViewModel
    @Inject
    constructor() : UnifiedViewModel() {
        private val _state = MutableStateFlow(AnalyticsUiState())
        val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                _state.value = _state.value.copy(isPremium = AccountManager.isPremium())
            }
        }

        /** `null` means "no bound" (Lifetime); a `[start, end)` pair scopes to the current
         * calendar month (Month), reusing [monthRangeMillis] rather than re-deriving it. */
        private fun periodRangeMillis(period: AnalyticsPeriod): Pair<Long, Long>? =
            when (period) {
                AnalyticsPeriod.LIFETIME -> null
                AnalyticsPeriod.MONTH -> {
                    val now = Calendar.getInstance()
                    monthRangeMillis(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
                }
            }

        override fun onStateEvent(event: StateEvent) {
            when (event) {
                // Auto-refresh when transactions are created
                is StateEvent.TransactionCreated -> {
                    // Clear synchronously (before the coroutine below is queued) so no
                    // concurrent reader — this ViewModel's own fetchFromNetwork(), or
                    // BudgetViewModel's fetchStreak() reading the same shared
                    // StreakSessionCache — can observe a stale snapshot. Previously only
                    // cleared on UserLoggedOut, so once any screen populated it, the
                    // displayed streak froze until logout regardless of new transactions.
                    StreakSessionCache.clear()
                    viewModelScope.launch {
                        try {
                            refreshSuspend()
                            publishEvent(StateEvent.AnalyticsRefreshed)
                        } catch (e: Exception) {
                            Log.e("AnalyticsViewModel", "❌ Analytics refresh failed", e)
                        }
                    }
                }

                // Auto-refresh when channels change
                is StateEvent.ChannelCreated,
                is StateEvent.ChannelUpdated,
                is StateEvent.ChannelDeleted,
                -> {
                    StreakSessionCache.clear()
                    viewModelScope.launch {
                        try {
                            refresh()
                        } catch (e: Exception) {
                        }
                    }
                }

                // SyncWorker just finished a push+pull cycle — the accurate correction after
                // TransactionCreated's immediate (possibly-stale) refresh above. refreshSuspend()
                // already has no isConnectedNow guard to bypass (unlike Dashboard's). Must also
                // clear the streak cache here: TransactionCreated's own fetchFromNetwork()
                // already repopulated it with whatever /dashboard returned at that moment,
                // which the backend's own same-day dedup semantics can still render
                // stale-looking seconds later — without clearing again, this "accurate
                // correction" pass would just silently re-serve that same cached snapshot
                // instead of refetching, defeating its own documented purpose.
                is StateEvent.SyncCompleted -> {
                    StreakSessionCache.clear()
                    viewModelScope.launch {
                        try {
                            refreshSuspend()
                        } catch (e: Exception) {
                        }
                    }
                }

                // Respond to logout
                is StateEvent.UserLoggedOut -> {
                    StreakSessionCache.clear()
                    _state.value =
                        _state.value.copy(
                            analytics = null,
                            phase = AnalyticsPhase.Idle,
                            streakCount = 0,
                            streakLastActiveDate = null,
                        )
                }

                else -> {
                }
            }
        }

        // ── Public API ────────────────────────────────────────────────────────────

        fun load() {
            if (_state.value.analytics != null) return
            viewModelScope.launch {
                _state.value = _state.value.copy(phase = AnalyticsPhase.Loading)
                fetchFromNetwork()
            }
        }

        /** Serializes [refreshSuspend] so two overlapping callers (e.g. a manual
         * pull-to-refresh racing the [StateEvent.TransactionCreated] handler) both
         * genuinely wait for a real fetch to finish, rather than a plain `isRefreshing`
         * boolean letting the second caller skip work and return instantly with stale
         * data. */
        private val refreshMutex = Mutex()

        /** Suspend core of [refresh] — awaits the actual network refetch before returning,
         * so a caller that needs to know a refresh has genuinely *finished* (not just
         * started) can await this directly. `internal`, not `private`, so a JVM test can
         * call it without going through the event bus. */
        internal suspend fun refreshSuspend() {
            refreshMutex.withLock {
                try {
                    _state.value = _state.value.copy(isRefreshing = true)
                    fetchFromNetwork()
                } catch (e: Exception) {
                } finally {
                    _state.value = _state.value.copy(isRefreshing = false)
                }
            }
        }

        fun refresh() {
            if (_state.value.isRefreshing) return
            viewModelScope.launch { refreshSuspend() }
        }

        // Switching periods re-fetches from scratch every time (no local dual-cache of both
        // Month and Lifetime responses) — a deliberate simplification for v1, not an oversight.
        // Uses its own isPeriodChanging flag rather than refresh()'s isRefreshing so the screen
        // can scope the loading UI to just the transaction-based-insights section instead of
        // swapping the whole page to a skeleton (period doesn't affect budget-based cards).
        fun setPeriod(period: AnalyticsPeriod) {
            if (_state.value.period == period || _state.value.isPeriodChanging) return
            _state.value = _state.value.copy(period = period, isPeriodChanging = true)
            viewModelScope.launch {
                try {
                    fetchFromNetwork()
                } finally {
                    _state.value = _state.value.copy(isPeriodChanging = false)
                }
            }
        }

        // ── Network ───────────────────────────────────────────────────────────────

        private suspend fun fetchFromNetwork() {
            try {
                val response = ApiClient.api.getAnalytics(period = _state.value.period.queryValue)
                if (response.isSuccessful) {
                    val body = response.body()!!
                    val cachedStreak = StreakSessionCache.get()
                    val streak =
                        if (cachedStreak != null) {
                            cachedStreak
                        } else {
                            try {
                                val now = Calendar.getInstance()
                                val dashboardStreak =
                                    ApiClient.api.getDashboard(
                                        month = now.get(Calendar.MONTH) + 1,
                                        year = now.get(Calendar.YEAR),
                                    ).body()?.streak
                                if (dashboardStreak != null) {
                                    StreakSessionCache.set(
                                        count = dashboardStreak.currentStreak,
                                        lastActiveDate = dashboardStreak.lastActiveDate,
                                    )
                                    StreakSessionCache.get()
                                } else {
                                    null
                                }
                            } catch (_: Exception) {
                                null
                            }
                        }
                    val isEmpty = body.summary?.data?.transactionCount == 0
                    _state.value =
                        _state.value.copy(
                            analytics = body,
                            phase = if (isEmpty) AnalyticsPhase.Empty else AnalyticsPhase.Loaded,
                            lastUpdated = System.currentTimeMillis(),
                            isOffline = false,
                            streakCount = streak?.count ?: _state.value.streakCount,
                            streakLastActiveDate = streak?.lastActiveDate ?: _state.value.streakLastActiveDate,
                        )
                } else {
                    if (_state.value.analytics == null) {
                        _state.value =
                            _state.value.copy(
                                phase = AnalyticsPhase.Error("Server error (${response.code()})"),
                            )
                    }
                }
            } catch (e: Exception) {
                Log.e("AnalyticsViewModel", "fetchFromNetwork failed", e)
                if (_state.value.analytics == null) {
                    _state.value =
                        _state.value.copy(
                            phase = AnalyticsPhase.Error(friendlyErrorMessage(e)),
                        )
                }
                _state.value = _state.value.copy(isOffline = e is java.io.IOException)
            }
        }

        /** A thrown [java.io.IOException] (`UnknownHostException`/`ConnectException`/
         * `SocketTimeoutException`/...) means the request never reached the server — the raw
         * exception message (e.g. `"Unable to resolve host \"api.dlabs.cc\"..."`) is a Java
         * string, not something to show a user. Anything else (JSON parsing, an unexpected
         * runtime failure) gets an equally friendly, equally non-technical fallback. */
        private fun friendlyErrorMessage(e: Exception): String =
            if (e is java.io.IOException) {
                "No internet connection. Check your connection and try again."
            } else {
                "Something went wrong loading your analytics. Please try again."
            }

        // ── Computed helpers ──────────────────────────────────────────────────────

        val currentPeriodLabel: String get() {
            val raw = _state.value.analytics?.summary?.data?.currentMonth ?: return "This Month"
            val parts = raw.split("-")
            if (parts.size < 2) return raw
            val year = parts[0].toIntOrNull() ?: return raw
            val month = parts[1].toIntOrNull() ?: return raw
            if (month !in 1..12) return raw
            val names =
                listOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December",
                )
            return "${names[month - 1]} $year"
        }
        val greetingText: String
            get() {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                return when (hour) {
                    in 0..11 -> "Good morning"
                    in 12..16 -> "Good afternoon"
                    else -> "Good evening"
                }
            }

        val streakDrawable: Int?
            get() = StreakUiHelper.drawable(_state.value.streakCount, _state.value.streakLastActiveDate)

        val streakLabel: String
            get() = StreakUiHelper.label(_state.value.streakCount)

        val formattedLastUpdated: String get() {
            val ts = _state.value.lastUpdated ?: return "Never synced"
            val diffMs = System.currentTimeMillis() - ts
            return when {
                diffMs < 60_000 -> "Synced just now"
                diffMs < 3_600_000 -> "Synced ${diffMs / 60_000}m ago"
                else -> "Synced ${diffMs / 3_600_000}h ago"
            }
        }
    }
