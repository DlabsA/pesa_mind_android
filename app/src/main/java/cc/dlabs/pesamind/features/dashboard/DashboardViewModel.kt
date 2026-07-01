package cc.dlabs.pesamind.features.dashboard

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.network.ApiService
import cc.dlabs.pesamind.core.network.NetworkMonitor
import cc.dlabs.pesamind.core.network.analytics.DashboardResponse
import cc.dlabs.pesamind.core.storage.StreakSessionCache
import cc.dlabs.pesamind.core.utils.StreakUiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ─── Phase (mirrors iOS DashboardViewModel.Phase) ────────────────────────────

sealed class DashboardPhase {
    data object Idle    : DashboardPhase()
    data object Loading : DashboardPhase()
    data object Loaded  : DashboardPhase()
    data object Empty   : DashboardPhase()
    data class  Error(val message: String) : DashboardPhase()
}

// ─── UI State ─────────────────────────────────────────────────────────────────

data class DashboardUiState(
    val dashboard:    DashboardResponse? = null,
    val phase:        DashboardPhase     = DashboardPhase.Idle,
    val isRefreshing: Boolean            = false,
    val isOffline:    Boolean            = false,
    val lastUpdated:  Date?              = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val apiService:     ApiService,
    private val networkMonitor: NetworkMonitor,
) : UnifiedViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        // Mirror iOS: observe connectivity, auto-load when connection returns
        viewModelScope.launch {
            networkMonitor.isConnected.collect { connected ->
                _state.update { it.copy(isOffline = !connected) }
                if (connected && _state.value.dashboard == null) {
                    fetchFromNetwork()
                }
            }
        }
    }

    override fun onStateEvent(event: StateEvent) {
        when (event) {
            // Auto-refresh when transactions are created
            is StateEvent.TransactionCreated -> {
                viewModelScope.launch {
                    try {
                        refresh()
                        publishEvent(StateEvent.DashboardRefreshed)
                    } catch (e: Exception) {
                    }
                }
            }
            
            // Auto-refresh when channels change
            is StateEvent.ChannelCreated,
            is StateEvent.ChannelUpdated,
            is StateEvent.ChannelDeleted -> {
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
                        phase = DashboardPhase.Idle
                    )
                }
            }
            
            // Handle sync requests
            is StateEvent.SyncRequested -> {
                viewModelScope.launch { refresh() }
            }
            
            else -> {
                Log.d("DashboardViewModel", "Ignoring event: ${event::class.simpleName}")
            }
        }
    }

    // ─── Public API (mirrors iOS load() / refresh()) ──────────────────────────

    fun load() {
        if (_state.value.dashboard != null) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    phase     = DashboardPhase.Loading,
                    isOffline = !networkMonitor.isConnectedNow,
                )
            }
            if (networkMonitor.isConnectedNow) fetchFromNetwork()
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            if (networkMonitor.isConnectedNow) {
                fetchFromNetwork()
            } else {
                _state.update { it.copy(isOffline = true) }
            }
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    // ─── Network fetch (uses real ApiService.getDashboard()) ──────────────────

    private suspend fun fetchFromNetwork() {
        try {
            val response = apiService.getDashboard()

            if (response.isSuccessful) {
                val body = response.body()
                    ?: throw IllegalStateException("Empty response body from /analytics/dashboard")
                StreakSessionCache.set(
                    count = body.streak.currentStreak,
                    lastActiveDate = body.streak.lastActiveDate,
                )

                val isEmpty = body.summary.data.transactionCount == 0
                _state.update {
                    it.copy(
                        dashboard   = body,
                        phase       = if (isEmpty) DashboardPhase.Empty else DashboardPhase.Loaded,
                        lastUpdated = Date(),
                        isOffline   = false,
                    )
                }
            } else {
                val errorMsg = when (response.code()) {
                    401  -> "Session expired — please log in again"
                    403  -> "Access denied"
                    404  -> "Dashboard data not found"
                    500  -> "Server error — try again later"
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
                in 0..11  -> "Good morning"
                in 12..16 -> "Good afternoon"
                else      -> "Good evening"
            }
        }

    val currentPeriodLabel: String
        get() {
            val period = _state.value.dashboard?.summary?.data?.currentMonth
                ?: return "This Month"
            val parts  = period.split("-")
            if (parts.size != 2) return period
            val year   = parts[0].toIntOrNull() ?: return period
            val month  = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return period
            val months = arrayOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December",
            )
            return "${months[month - 1]} $year"
        }

    val formattedLastUpdated: String
        get() {
            val date    = _state.value.lastUpdated ?: return "Never synced"
            val diffMs  = System.currentTimeMillis() - date.time
            val diffMin = (diffMs / 60_000).toInt()
            return when {
                diffMin < 1  -> "Synced just now"
                diffMin < 60 -> "Synced ${diffMin}m ago"
                else         -> {
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