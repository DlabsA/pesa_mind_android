package cc.dlabs.pesamind.features.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountState(
    val username: String = "",
    val email: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val balance: Double? = null,
    val type: String? = null
)

class AccountViewModel : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.getProfile()
                if (response.isSuccessful) {
                    val user = response.body()!!
                    _state.value = AccountState(
                        username = user.username,
                        email = user.email,
                        balance = user.balance,
                        type = user.type,
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load profile"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Cannot reach server ${e.message}"
                )
            }
        }
    }

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun onEmailChange(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun saveProfile() {
        val current = _state.value
        if (current.username.isBlank()) {
            _state.value = current.copy(error = "Username cannot be empty")
            return
        }
        if (current.email.isBlank() || !current.email.contains("@")) {
            _state.value = current.copy(error = "Enter a valid email")
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(isSaving = true, error = null)
            try {
                val response = ApiClient.api.updateProfile(
                    UpdateProfileRequest(
                        username = current.username,
                        email = current.email
                    )
                )
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        successMessage = "Profile updated successfully"
                    )
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = when (response.code()) {
                            409 -> "Email already in use"
                            400 -> "Invalid details"
                            else -> "Update failed (${response.code()})"
                        }
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = "Cannot reach server"
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(successMessage = null, error = null)
    }
}