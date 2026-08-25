package cc.dlabs.pesamind.features.settings.account

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.UpdateProfileRequest
import cc.dlabs.pesamind.core.storage.AccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.toString

data class AccountState(
    val username: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val balance: Double? = null,
    val type: String? = null,
    // Non-null only while on an active Premium trial — see [AccountManager.trialDaysRemaining].
    val trialDaysRemaining: Int? = null,
) {
    val isPremium: Boolean get() = type == "Premium" || type == "Enterprise"
}

class AccountViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    /** Refreshes the Plan row the moment a payment lands, rather than on next launch. */
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.SubscriptionActivated -> loadProfile()
            else -> {}
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                val cachedAccount = AccountManager.getAccount()

                val hasCachedData =
                    cachedAccount.id.isNotBlank() ||
                        cachedAccount.username.isNotBlank() ||
                        cachedAccount.email.isNotBlank()

                if (hasCachedData) {
                    _state.value =
                        AccountState(
                            username = cachedAccount.username,
                            email = cachedAccount.email,
                            avatarUrl = cachedAccount.avatarUrl,
                            balance = cachedAccount.balance,
                            type = cachedAccount.type,
                            trialDaysRemaining = AccountManager.trialDaysRemaining(),
                            isLoading = false,
                        )
                    return@launch
                }

                val response = ApiClient.api.getProfile()
                if (response.isSuccessful) {
                    val user = response.body()
                    val profile = user?.profile

                    if (user != null && profile != null) {
                        // trialExpiresAt must be passed through: saveAccount removes
                        // the stored key when it is null, so omitting it here wiped
                        // the trial expiry and silently killed the countdown on
                        // every no-cache load.
                        AccountManager.saveAccount(
                            id = user.id,
                            email = user.email,
                            username = profile.username.orEmpty(),
                            avatarUrl = profile.avatarUrl.orEmpty(),
                            balance = (profile.balance ?: 0.0).toString(),
                            type = profile.type.orEmpty(),
                            trialExpiresAt = profile.trialExpiresAt,
                        )

                        _state.value =
                            AccountState(
                                username = profile.username.orEmpty(),
                                email = user.email,
                                avatarUrl = profile.avatarUrl.orEmpty(),
                                balance = profile.balance ?: 0.0,
                                type = profile.type,
                                trialDaysRemaining = AccountManager.trialDaysRemaining(),
                                isLoading = false,
                            )
                    } else {
                        _state.value =
                            _state.value.copy(
                                isLoading = false,
                                error = "Empty profile response",
                            )
                    }
                } else {
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            error = "Failed to load profile",
                        )
                }
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = "Cannot reach server: ${e.message}",
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

        _state.launchWithState(
            call = {
                ApiClient.api.updateProfile(
                    UpdateProfileRequest(
                        username = current.username,
                        email = current.email,
                    ),
                )
            },
            setLoading = { s, saving -> s.copy(isSaving = saving) },
            setError = { s, err -> s.copy(error = err) },
            onSuccess = { s, _ ->
                AccountManager.saveEmail(current.email)
                AccountManager.saveUsername(current.username)
                s.copy(successMessage = "Profile updated successfully")
            },
            mapHttpError = { response ->
                when (response.code()) {
                    409 -> "Email already in use"
                    400 -> "Invalid details"
                    else -> "Update failed (${response.code()})"
                }
            },
        )
    }

    fun clearMessage() {
        _state.value = _state.value.copy(successMessage = null, error = null)
    }
}
