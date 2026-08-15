package cc.dlabs.pesamind.features.dashboard

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.analytics.DashboardResponse
import cc.dlabs.pesamind.core.network.analytics.SummaryData
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.StreakSessionCache
import cc.dlabs.pesamind.core.utils.StreakUiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─── Phase (mirrors iOS DashboardViewModel.Phase) ────────────────────────────

sealed class DashboardPhase {
    data object Idle : DashboardPhase()

    data object Loading : DashboardPhase()

    data object Loaded : DashboardPhase()

    data object Empty : DashboardPhase()

    data class Error(val message: String) : DashboardPhase()
}

// ─── UI State ─────────────────────────────────────────────────────────────────

data class DashboardUiState(
    val dashboard: DashboardResponse? = null,
    // Income/expense/savings/net-movement, computed live from local Room transactions — see
    // TransactionRepository.observeMonthlySummary. Populated independently of [dashboard]/
    // [phase]: it doesn't need a network round-trip, so it's available even when the network
    // fetch below has never succeeded (fresh install, offline-since-launch).
    val localSummary: SummaryData? = null,
    val phase: DashboardPhase = DashboardPhase.Idle,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val lastUpdated: Date? = null,
    // Defaults to true so a gated card never flashes an upsell to a paying user
    // during the async tier read — the same convention as AnalyticsUiState.isPremium.
    val isPremium: Boolean = true,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val apiService: ApiService,
        private val networkMonitor: NetworkMonitor,
    ) : UnifiedViewModel() {
        private val _state = MutableStateFlow(DashboardUiState())
        val state: StateFlow<DashboardUiState> = _state.asStateFlow()

        /** Serializes every network-refetch entry point — [load]/[init]'s cold-launch fetch and
         * [refreshSuspend]/[refreshAfterSyncSuspend] — against each other, for two different
         * reasons depending on the caller:
         *  - [load]/[init] use it to DEDUPE: both can independently decide "nothing loaded yet,
         *    fetch" within milliseconds of each other at cold launch (init's connectivity
         *    collector fires ~instantly on ViewModel construction; load() follows ~30ms later
         *    via DashboardScreen's LaunchedEffect(Unit)). The guard is re-checked *inside* the
         *    lock so whichever caller loses the race sees the winner's now-populated
         *    `dashboard` and skips its own redundant fetch, instead of both firing a real
         *    GET /dashboard.
         *  - [refreshSuspend]/[refreshAfterSyncSuspend] use it so two overlapping callers (e.g.
         *    a manual pull-to-refresh racing the [StateEvent.TransactionCreated] handler) both
         *    genuinely wait for and perform a real fetch, rather than a plain `isRefreshing`
         *    boolean letting the second caller skip work and return instantly with stale data —
         *    that would silently reintroduce the exact "refreshed signal fires before the fetch
         *    finishes" bug this split exists to fix. These two do NOT re-check any guard inside
         *    the lock — every call always performs a real fetch.
         *
         * Must be declared *before* [init], not just anywhere in the class: `viewModelScope`
         * runs on `Dispatchers.Main.immediate`, and [NetworkMonitor.isConnected] seeds its
         * current value synchronously on collection, so init's `collect` lambda — including its
         * own `refreshMutex.withLock` call — runs synchronously, still inside this constructor.
         * Kotlin initializes properties/init blocks in textual order, so declaring this after
         * [init] leaves it null at that point: `.withLock` then throws a NullPointerException
         * on `Mutex.lock`. Confirmed via a production crash, 2026-08-11 — don't move this back
         * down. */
        private val refreshMutex = Mutex()

        init {
            viewModelScope.launch {
                _state.update { it.copy(isPremium = AccountManager.isPremium()) }
            }

            // Mirror iOS: observe connectivity, auto-load when connection returns
            viewModelScope.launch {
                var wasConnected: Boolean? = null
                networkMonitor.isConnected.collect { connected ->
                    _state.update { it.copy(isOffline = !connected) }
                    // dashboard == null covers "never loaded"; wasConnected == false covers
                    // "loaded, but stale because we were offline since" — without the second
                    // check, reconnecting after an offline stretch never refetched once a
                    // dashboard had already loaded once this session.
                    val reconnected = wasConnected == false
                    if (connected && (_state.value.dashboard == null || reconnected)) {
                        // Re-checked fresh *inside* the lock so whichever of init/load() loses
                        // the race to acquire refreshMutex sees the winner's now-populated
                        // `dashboard` and skips its own redundant fetch — see refreshMutex's
                        // doc comment. `reconnected` is intentionally reused as-is (a stable
                        // val from this specific isConnected emission): a genuine reconnect
                        // still refetches even if a concurrent load() already populated
                        // `dashboard` while this waited for the lock, matching
                        // refreshSuspend()'s "always fetch when explicitly triggered" contract.
                        refreshMutex.withLock {
                            if (_state.value.dashboard == null || reconnected) {
                                fetchFromNetwork()
                            }
                        }
                    }
                    wasConnected = connected
                }
            }
            observeLocalSummary()
        }

        /** Sole writer of [DashboardUiState.localSummary] — a live Room Flow, so this needs no
         * connectivity check and no explicit refresh call anywhere: a transaction being
         * created (dirty, unsynced) already changes the local numbers immediately, and a later
         * [StateEvent.SyncCompleted] doesn't change them again (sync doesn't alter amounts,
         * only sync-status metadata this summary never reads). */
        private fun observeLocalSummary() {
            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1
            viewModelScope.launch {
                TransactionRepository.observeMonthlySummary(year, month).collect { summary ->
                    _state.update { it.copy(localSummary = summary) }
                }
            }
        }

        override fun onStateEvent(event: StateEvent) {
            when (event) {
                // A payment landed: re-read the tier so the gated cards swap from an
                // upsell to real data without waiting for the next JWT refresh.
                is StateEvent.SubscriptionActivated ->
                    viewModelScope.launch {
                        _state.update { it.copy(isPremium = AccountManager.isPremium()) }
                        try {
                            refreshSuspend()
                        } catch (e: Exception) {
                            Log.e("DashboardViewModel", "Refresh after subscription activation failed", e)
                        }
                    }

                // Auto-refresh when transactions are created
                is StateEvent.TransactionCreated -> {
                    viewModelScope.launch {
                        try {
                            refreshSuspend()
                            publishEvent(StateEvent.DashboardRefreshed)
                        } catch (e: Exception) {
                        }
                    }
                }

                // Auto-refresh when channels change
                is StateEvent.ChannelCreated,
                is StateEvent.ChannelUpdated,
                is StateEvent.ChannelDeleted,
                -> {
                    viewModelScope.launch {
                        try {
                            refresh()
                        } catch (e: Exception) {
                        }
                    }
                }

                // Respond to logout
                is StateEvent.UserLoggedOut -> {
                    StreakSessionCache.clear()
                    _state.update {
                        it.copy(
                            dashboard = null,
                            phase = DashboardPhase.Idle,
                        )
                    }
                }

                // Handle sync requests
                is StateEvent.SyncRequested -> {
                    viewModelScope.launch { refresh() }
                }

                // SyncWorker just finished a push+pull cycle — the accurate correction after
                // TransactionCreated's immediate (possibly-stale) refresh above.
                is StateEvent.SyncCompleted -> refreshAfterSync()

                else -> {
                    Log.d("DashboardViewModel", "Ignoring event: ${event::class.simpleName}")
                }
            }
        }

        // ─── Public API (mirrors iOS load() / refresh()) ──────────────────────────

        fun load() {
            // Cheap, unsynchronized fast path: once loaded, every re-entry to this screen
            // returns immediately without launching a coroutine or touching refreshMutex.
            if (_state.value.dashboard != null) return
            viewModelScope.launch {
                // Set immediately, unsynchronized — this is what shows the Loading skeleton
                // promptly on cold launch. Deliberately NOT inside refreshMutex.withLock below:
                // init's own cold-launch fetch almost always wins the race to acquire that lock
                // first and can hold it for the full ~1s+ GET /dashboard round trip, so gating
                // this update behind the same lock would delay — or entirely skip — the
                // loading-skeleton feedback the very first frame is supposed to show.
                _state.update {
                    it.copy(
                        phase = DashboardPhase.Loading,
                        isOffline = !networkMonitor.isConnectedNow,
                    )
                }
                // Shares refreshMutex with init()/refreshSuspend()/refreshAfterSyncSuspend() so
                // this and init's near-simultaneous cold-launch fetch can't both hit the
                // network at once. Re-checked fresh *inside* the lock so whichever caller loses
                // the race sees the winner's now-populated `dashboard` and skips its own
                // redundant fetch.
                refreshMutex.withLock {
                    if (_state.value.dashboard != null) return@withLock
                    if (networkMonitor.isConnectedNow) fetchFromNetwork()
                }
            }
        }

        /** Suspend core of [refresh] — awaits the actual network refetch (or the offline
         * short-circuit) before returning, so a caller that needs to know a refresh has
         * genuinely *finished* (not just started) can await this directly. `internal`, not
         * `private`, so a JVM test can call it without going through the event bus. */
        internal suspend fun refreshSuspend() {
            refreshMutex.withLock {
                _state.update { it.copy(isRefreshing = true) }
                try {
                    if (networkMonitor.isConnectedNow) {
                        fetchFromNetwork()
                    } else {
                        _state.update { it.copy(isOffline = true) }
                    }
                } finally {
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
        }

        fun refresh() {
            if (_state.value.isRefreshing) return
            viewModelScope.launch { refreshSuspend() }
        }

        /** Same as [refreshSuspend] but skips the `isConnectedNow` guard — only called from
         * [StateEvent.SyncCompleted], where connectivity is already implied by a sync having
         * just completed, so that guard would only add a redundant, possibly-racy recheck. */
        internal suspend fun refreshAfterSyncSuspend() {
            refreshMutex.withLock {
                _state.update { it.copy(isRefreshing = true) }
                try {
                    fetchFromNetwork()
                } finally {
                    _state.update { it.copy(isRefreshing = false) }
                }
            }
        }

        private fun refreshAfterSync() {
            if (_state.value.isRefreshing) return
            viewModelScope.launch { refreshAfterSyncSuspend() }
        }

        // ─── Network fetch (uses real ApiService.getDashboard()) ──────────────────

        private suspend fun fetchFromNetwork() {
            try {
                val now = Calendar.getInstance()
                val response =
                    apiService.getDashboard(
                        month = now.get(Calendar.MONTH) + 1,
                        year = now.get(Calendar.YEAR),
                    )

                if (response.isSuccessful) {
                    val body =
                        response.body()
                            ?: throw IllegalStateException("Empty response body from /analytics/dashboard")

                    // Guard against null streak response
                    body.streak?.let {
                        StreakSessionCache.set(
                            count = it.currentStreak,
                            lastActiveDate = it.lastActiveDate,
                        )
                    } ?: run {
                        // If streak is null, clear the cache or use defaults
                        StreakSessionCache.clear()
                    }

                    val isEmpty = body.summary.data.transactionCount == 0
                    _state.update {
                        it.copy(
                            dashboard = body,
                            phase = if (isEmpty) DashboardPhase.Empty else DashboardPhase.Loaded,
                            lastUpdated = Date(),
                            isOffline = false,
                        )
                    }
                } else {
                    val errorMsg =
                        when (response.code()) {
                            401 -> "Session expired — please log in again"
                            403 -> "Access denied"
                            404 -> "Dashboard data not found"
                            500 -> "Server error — try again later"
                            else -> "Unexpected error (${response.code()})"
                        }
                    if (_state.value.dashboard == null) {
                        _state.update { it.copy(phase = DashboardPhase.Error(errorMsg)) }
                    }
                }
            } catch (e: Exception) {
                if (_state.value.dashboard == null) {
                    _state.update {
                        it.copy(phase = DashboardPhase.Error(e.message ?: "Unknown error"))
                    }
                }
            }
        }

        // ─── Computed helpers (mirrors iOS DashboardViewModel) ────────────────────

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
                val period =
                    _state.value.dashboard?.summary?.data?.currentMonth
                        ?: return "This Month"
                val parts = period.split("-")
                if (parts.size != 2) return period
                val year = parts[0].toIntOrNull() ?: return period
                val month = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return period
                val months =
                    arrayOf(
                        "January", "February", "March", "April", "May", "June",
                        "July", "August", "September", "October", "November", "December",
                    )
                return "${months[month - 1]} $year"
            }

        val formattedLastUpdated: String
            get() {
                val date = _state.value.lastUpdated ?: return "Never synced"
                val diffMs = System.currentTimeMillis() - date.time
                val diffMin = (diffMs / 60_000).toInt()
                return when {
                    diffMin < 1 -> "Synced just now"
                    diffMin < 60 -> "Synced ${diffMin}m ago"
                    else -> {
                        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                        "Synced at ${sdf.format(date)}"
                    }
                }
            }

        val hasAnomalies: Boolean
            get() = (_state.value.dashboard?.anomalies?.data?.anomaliesDetected ?: 0) > 0

        val streakCount: Int
            get() = _state.value.dashboard?.streak?.currentStreak ?: 0

        val streakDrawable: Int?
            get() {
                val streak = _state.value.dashboard?.streak ?: return null
                return StreakUiHelper.drawable(streakCount, streak.lastActiveDate)
            }

        val streakLabel: String
            get() = StreakUiHelper.label(streakCount)
    }
