package cc.dlabs.pesamind.features.settings.account

import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.ChangePasswordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChangePasswordState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class ChangePasswordViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(ChangePasswordState())
    val state: StateFlow<ChangePasswordState> = _state.asStateFlow()

    fun onCurrentPasswordChange(value: String) {
        _state.value = _state.value.copy(currentPassword = value, error = null)
    }

    fun onNewPasswordChange(value: String) {
        _state.value = _state.value.copy(newPassword = value, error = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _state.value = _state.value.copy(confirmPassword = value, error = null)
    }

    fun submit() {
        val s = _state.value

        // Validation
        when {
            s.currentPassword.isBlank() -> {
                _state.value = s.copy(error = "Enter your current password")
                return
            }
            s.newPassword.length < 8 -> {
                _state.value = s.copy(error = "New password must be at least 8 characters")
                return
            }
            s.newPassword != s.confirmPassword -> {
                _state.value = s.copy(error = "New passwords do not match")
                return
            }
            s.newPassword == s.currentPassword -> {
                _state.value = s.copy(error = "New password must be different from current")
                return
            }
        }

        _state.launchWithState(
            call = {
                ApiClient.api.changePassword(
                    ChangePasswordRequest(
                        current_password = s.currentPassword,
                        new_password = s.newPassword,
                        confirm_password = s.confirmPassword,
                    ),
                )
            },
            setLoading = { state, loading -> state.copy(isLoading = loading) },
            setError = { state, err -> state.copy(error = err) },
            onSuccess = { state, _ -> state.copy(success = true) },
            mapHttpError = { response ->
                when (response.code()) {
                    401 -> "Current password is incorrect"
                    400 -> "Invalid request"
                    else -> "Failed (${response.code()})"
                }
            },
        )
    }
}
