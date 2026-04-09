package cc.dlabs.pesamind.features.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.ChangePasswordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChangePasswordState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class ChangePasswordViewModel : ViewModel() {

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

        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val response = ApiClient.api.changePassword(
                    ChangePasswordRequest(
                        current_password = s.currentPassword,
                        new_password = s.newPassword,
                        confirm_password = s.confirmPassword
                    )
                )
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        success = true
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = when (response.code()) {
                            401 -> "Current password is incorrect"
                            400 -> "Invalid request"
                            else -> "Failed (${response.code()})"
                        }
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Cannot reach server"
                )
            }
        }
    }
}