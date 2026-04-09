package cc.dlabs.pesamind.features.settings.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LockMode { NONE, PIN, PATTERN }

data class SecurityState(
    val currentMode: LockMode = LockMode.NONE,
    val isLoading: Boolean = true,
    val message: String? = null
)

class SecurityViewModel : ViewModel() {

    private val _state = MutableStateFlow(SecurityState())
    val state: StateFlow<SecurityState> = _state.asStateFlow()

    init {
        loadCurrentMode()
    }

    private fun loadCurrentMode() {
        viewModelScope.launch {
            val mode = when {
                TokenManager.isPinEnabled() -> LockMode.PIN
                TokenManager.isPatternEnabled() -> LockMode.PATTERN
                else -> LockMode.NONE
            }
            _state.value = SecurityState(currentMode = mode, isLoading = false)
        }
    }

    fun disableLock() {
        viewModelScope.launch {
            TokenManager.clearLock()
            _state.value = _state.value.copy(
                currentMode = LockMode.NONE,
                message = "Lock disabled"
            )
        }
    }


    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}