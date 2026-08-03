package cc.dlabs.pesamind.features.auth

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
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
    val isSetupMode: Boolean = false,
)

class UnlockViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(UnlockState())
    val state: StateFlow<UnlockState> = _state.asStateFlow()

    /**
     * Save PIN during setup (no JWT refresh needed)
     */
    fun setupPin(
        pin: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                TokenManager.savePin(pin)
                onSuccess()
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        errorMessage = "Failed to save PIN",
                    )
            }
        }
    }

    /**
     * Verify PIN during unlock, then refresh JWT
     */
    fun unlockWithPin(
        enteredPin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
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

                // PIN is correct — unlock now, refresh the JWT best-effort in the background.
                // Offline-first: local verification alone must be sufficient to enter the app.
                _state.value = _state.value.copy(isLoading = false)
                onSuccess()
                refreshJWTBestEffort()
            } catch (e: Exception) {
                onError("An error occurred: ${e.message}")
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Save pattern during setup (no JWT refresh needed)
     */
    fun setupPattern(
        pattern: String,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                TokenManager.savePattern(pattern)
                onSuccess()
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        errorMessage = "Failed to save pattern",
                    )
            }
        }
    }

    /**
     * Verify pattern during unlock, then refresh JWT
     */
    fun unlockWithPattern(
        enteredPattern: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
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

                // Pattern is correct — unlock now, refresh the JWT best-effort in the background.
                // Offline-first: local verification alone must be sufficient to enter the app.
                _state.value = _state.value.copy(isLoading = false)
                onSuccess()
                refreshJWTBestEffort()
            } catch (e: Exception) {
                onError("An error occurred: ${e.message}")
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Called after the OS BiometricPrompt (triggered from the UI layer — see
     * `core/utils/BiometricAuthHelper.kt`) reports success. Unlike [unlockWithPin]/
     * [unlockWithPattern], there's no local secret to compare — the OS authentication itself
     * is the check — so this unlocks immediately and refreshes the JWT best-effort afterward.
     */
    fun unlockWithBiometric(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = false, errorMessage = null)
            onSuccess()
            refreshJWTBestEffort()
        }
    }

    /**
     * Best-effort JWT refresh after a local unlock has already succeeded and the caller has
     * already navigated onward. Offline-first: unlocking must never depend on connectivity, so
     * failures here (offline, expired refresh token, server error) are swallowed rather than
     * surfaced — a stale access token is handled reactively by [TokenRefreshInterceptor] the
     * next time an authenticated call is made, once connectivity is available.
     */
    private suspend fun refreshJWTBestEffort() {
        try {
            val refreshToken = TokenManager.getRefreshToken() ?: return
            if (refreshToken.isBlank()) return

            val response = ApiClient.api.refresh(RefreshRequest(refreshToken))
            val body = response.body()
            if (response.isSuccessful && body?.accessToken != null && body.refreshToken != null) {
                TokenManager.saveTokens(body.accessToken, body.refreshToken)
            }
        } catch (e: Exception) {
            // Offline or unreachable — ignore, already unlocked locally.
        }
    }
}
