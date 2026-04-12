package cc.dlabs.pesamind.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.RefreshRequest
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UnlockState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSetupMode: Boolean = false
)

class UnlockViewModel : ViewModel() {

    private val _state = MutableStateFlow(UnlockState())
    val state: StateFlow<UnlockState> = _state.asStateFlow()

    /**
     * Save PIN during setup (no JWT refresh needed)
     */
    fun setupPin(pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                TokenManager.savePin(pin)
                onSuccess()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Failed to save PIN"
                )
            }
        }
    }

    /**
     * Verify PIN during unlock, then refresh JWT
     */
    fun unlockWithPin(enteredPin: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            try {
                val savedPin = TokenManager.getPin()

                if (savedPin == null) {
                    onError("PIN not found. Please set up a PIN first.")
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }

                if (savedPin != enteredPin) {
                    onError("Incorrect PIN")
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }

                // PIN is correct, now refresh JWT
                refreshJWT(onSuccess, onError)
            } catch (e: Exception) {
                onError("An error occurred: ${e.message}")
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Save pattern during setup (no JWT refresh needed)
     */
    fun setupPattern(pattern: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                TokenManager.savePattern(pattern)
                onSuccess()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Failed to save pattern"
                )
            }
        }
    }

    /**
     * Verify pattern during unlock, then refresh JWT
     */
    fun unlockWithPattern(enteredPattern: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            try {
                val savedPattern = TokenManager.getPattern()

                if (savedPattern == null) {
                    onError("Pattern not found. Please set up a pattern first.")
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }

                if (savedPattern != enteredPattern) {
                    onError("Incorrect pattern")
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }

                // Pattern is correct, now refresh JWT
                refreshJWT(onSuccess, onError)
            } catch (e: Exception) {
                onError("An error occurred: ${e.message}")
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Refresh JWT using the stored refresh token
     */
    private suspend fun refreshJWT(onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val refreshToken = TokenManager.getRefreshToken()

            if (refreshToken.isNullOrBlank()) {
                onError("Session expired. Please sign in again.")
                _state.value = _state.value.copy(isLoading = false)
                return
            }

            val response = ApiClient.api.refresh(RefreshRequest(refreshToken))

            if (response.isSuccessful) {
                val body = response.body()

                if (body?.accessToken != null && body.refreshToken != null) {
                    TokenManager.saveTokens(body.accessToken, body.refreshToken)
                    _state.value = _state.value.copy(isLoading = false)
                    onSuccess()
                } else {
                    onError("Failed to refresh session. Please sign in again.")
                    _state.value = _state.value.copy(isLoading = false)
                }
            } else {
                when (response.code()) {
                    401 -> onError("Session expired. Please sign in again.")
                    else -> onError("Failed to refresh session (${response.code()})")
                }
                _state.value = _state.value.copy(isLoading = false)
            }
        } catch (e: Exception) {
            onError("Cannot reach server. Check your connection.")
            _state.value = _state.value.copy(isLoading = false)
        }
    }
}
