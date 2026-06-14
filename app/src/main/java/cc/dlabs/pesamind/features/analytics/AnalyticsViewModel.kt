package cc.dlabs.pesamind.features.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.AnalyticResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

// ─── Phase ────────────────────────────────────────────────────────────────────

sealed interface AnalyticsPhase {
    data object Idle    : AnalyticsPhase
    data object Loading : AnalyticsPhase
    data object Loaded  : AnalyticsPhase
    data object Empty   : AnalyticsPhase
    data class  Error(val message: String) : AnalyticsPhase
}

// ─── UI State ─────────────────────────────────────────────────────────────────

data class AnalyticsUiState(
    val analytics:    AnalyticResponse? = null,
    val phase:        AnalyticsPhase     = AnalyticsPhase.Idle,
    val isRefreshing: Boolean            = false,
    val isOffline:    Boolean            = false,
    val lastUpdated:  Long?              = null,   // epoch millis
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class AnalyticsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    fun load() {
        if (_state.value.analytics != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(phase = AnalyticsPhase.Loading)
            fetchFromNetwork()
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            fetchFromNetwork()
            _state.value = _state.value.copy(isRefreshing = false)
        }
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private suspend fun fetchFromNetwork() {
        try {
            val response = ApiClient.api.getAnalytics()
            if (response.isSuccessful) {
                val body = response.body()!!
                val isEmpty = body.summary?.data?.transactionCount == 0
                _state.value = _state.value.copy(
                    analytics   = body,
                    phase       = if (isEmpty) AnalyticsPhase.Empty else AnalyticsPhase.Loaded,
                    lastUpdated = System.currentTimeMillis(),
                    isOffline   = false,
                )
            } else {
                if (_state.value.analytics == null) {
                    _state.value = _state.value.copy(
                        phase = AnalyticsPhase.Error("Server error (${response.code()})")
                    )
                }
            }
        } catch (e: Exception) {
            if (_state.value.analytics == null) {
                _state.value = _state.value.copy(
                    phase = AnalyticsPhase.Error(e.message ?: "Unknown error")
                )
            }
            _state.value = _state.value.copy(isOffline = true)
        }
    }

    // ── Computed helpers ──────────────────────────────────────────────────────

    val overallHealthScore: Int get() {
        val a = _state.value.analytics ?: return 75

        val scores = mutableListOf<Int>()

        a.summary?.health?.score?.let { scores.add(it) }
        a.monthlyTrends?.health?.score?.let { scores.add(it) }
        a.budgetVsActual?.health?.score?.let { scores.add(it) }
        a.spendingVelocity?.health?.score?.let { scores.add(it) }

        return if (scores.isEmpty()) 75 else scores.sum() / scores.size
    }

    val currentPeriodLabel: String get() {
        val raw = _state.value.analytics?.summary?.data?.currentMonth ?: return "This Month"
        val parts = raw.split("-")
        if (parts.size < 2) return raw
        val year  = parts[0].toIntOrNull() ?: return raw
        val month = parts[1].toIntOrNull() ?: return raw
        if (month !in 1..12) return raw
        val names = listOf("January","February","March","April","May","June",
            "July","August","September","October","November","December")
        return "${names[month - 1]} $year"
    }
    val greetingText: String
        get() {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 0..11  -> "Good morning"
                in 12..16 -> "Good afternoon"
                else      -> "Good evening"
            }
        }

    val formattedLastUpdated: String get() {
        val ts = _state.value.lastUpdated ?: return "Never synced"
        val diffMs = System.currentTimeMillis() - ts
        return when {
            diffMs < 60_000              -> "Synced just now"
            diffMs < 3_600_000           -> "Synced ${diffMs / 60_000}m ago"
            else                         -> "Synced ${diffMs / 3_600_000}h ago"
        }
    }
}