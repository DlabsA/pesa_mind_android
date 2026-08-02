package cc.dlabs.pesamind.features.settings.security

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LockMode { NONE, PIN, PATTERN }

data class SecurityState(
    val currentMode: LockMode = LockMode.NONE,
    val isLoading: Boolean = true,
    val message: String? = null,
)

class SecurityViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(SecurityState())
    val state: StateFlow<SecurityState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-reads the current lock state from [TokenManager]. Public, not just called from
     * [init] — this ViewModel is scoped to the Security route's `NavBackStackEntry`, so
     * navigating to `SetPinScreen`/`SetPatternScreen` and popping back returns the *same*
     * instance (init never re-runs); the screen calls this again on every re-entry so a
     * newly-set PIN/pattern actually shows up (e.g. the "Remove Lock" row).
     */
    fun refresh() {
        viewModelScope.launch {
            val mode =
                when {
                    TokenManager.isPinEnabled() -> LockMode.PIN
                    TokenManager.isPatternEnabled() -> LockMode.PATTERN
                    else -> LockMode.NONE
                }
            _state.value = _state.value.copy(currentMode = mode, isLoading = false)
        }
    }

    fun disableLock() {
        viewModelScope.launch {
            TokenManager.clearLock()
            _state.value =
                _state.value.copy(
                    currentMode = LockMode.NONE,
                    message = "Lock disabled",
                )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
