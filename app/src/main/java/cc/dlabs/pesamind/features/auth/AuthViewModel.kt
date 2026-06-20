package cc.dlabs.pesamind.features.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.LoginRequest
import cc.dlabs.pesamind.core.network.models.RegisterRequest
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.storage.TokenManager.LockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UI State ──────────────────────────────────────────────────────────────────

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
    data class LoginSuccess(val destination: String) : AuthUiState
    data object RegisterSuccess : AuthUiState
}

data class LoginFormState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
)

data class RegisterFormState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class AuthViewModel : ViewModel() {

    // Login
    private val _loginForm = MutableStateFlow(LoginFormState())
    val loginForm: StateFlow<LoginFormState> = _loginForm.asStateFlow()

    // Register
    private val _registerForm = MutableStateFlow(RegisterFormState())
    val registerForm: StateFlow<RegisterFormState> = _registerForm.asStateFlow()

    // Shared auth state
    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    // ── Login form updates ────────────────────────────────────────────────────

    fun onLoginEmailChange(value: String) {
        _loginForm.update {
            it.copy(email = value, emailError = null)
        }
        resetErrorIfActive()
    }

    fun onLoginPasswordChange(value: String) {
        _loginForm.update {
            it.copy(password = value, passwordError = null)
        }
        resetErrorIfActive()
    }

    // ── Register form updates ─────────────────────────────────────────────────

    fun onRegisterUsernameChange(value: String) {
        _registerForm.update { it.copy(username = value, usernameError = null) }
        resetErrorIfActive()
    }

    fun onRegisterEmailChange(value: String) {
        _registerForm.update { it.copy(email = value, emailError = null) }
        resetErrorIfActive()
    }

    fun onRegisterPasswordChange(value: String) {
        _registerForm.update { it.copy(password = value, passwordError = null) }
        resetErrorIfActive()
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    fun login() {
        val form = _loginForm.value

        // Inline validation — mirrors Swift's per-field emailError / passwordError
        val emailErr = when {
            form.email.isBlank() -> "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(form.email.trim()).matches() -> "Enter a valid email"
            else -> null
        }
        val passwordErr = when {
            form.password.isBlank() -> "Password is required"
            form.password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }

        if (emailErr != null || passwordErr != null) {
            _loginForm.update { it.copy(emailError = emailErr, passwordError = passwordErr) }
            return
        }

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            try {
                val response = ApiClient.api.login(LoginRequest(form.email.trim(), form.password))

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.accessToken != null && body.refreshToken != null) {
                        Log.d("AuthVM", "Login successful — saving tokens")

                        TokenManager.saveTokens(body.accessToken, body.refreshToken)

                        body.profile?.let { profile ->
                            AccountManager.saveAccount(
                                id       = profile.id ?: "",
                                email    = form.email.trim(),
                                username = profile.username ?: "",
                                balance  = profile.balance?.toString() ?: "",
                                type     = profile.type ?: "",
                            )
                        }

                        val destination = when (TokenManager.getLockState()) {
                            LockState.NONE    -> "lock_setup"
                            LockState.PIN     -> "pin_unlock"
                            LockState.PATTERN -> "pattern_unlock"
                        }
                        _authState.value = AuthUiState.LoginSuccess(destination)

                    } else {
                        _authState.value = AuthUiState.Error(body?.error ?: "Invalid email or password")
                    }

                } else {
                    val message = when (response.code()) {
                        401  -> "Invalid email or password"
                        404  -> "Account not found"
                        else -> "Login failed (${response.code()})"
                    }
                    _authState.value = AuthUiState.Error(message)
                }

            } catch (t: Throwable) {
                Log.e("AuthVM", "Login error", t)
                _authState.value = AuthUiState.Error(networkErrorMessage(t))
            }
        }
    }

    // ── Register ──────────────────────────────────────────────────────────────

    fun register() {
        val form = _registerForm.value

        val usernameErr = if (form.username.isBlank()) "Username is required" else null
        val emailErr = when {
            form.email.isBlank() -> "Email is required"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(form.email.trim()).matches() -> "Enter a valid email"
            else -> null
        }
        val passwordErr = when {
            form.password.isBlank() -> "Password is required"
            form.password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }

        if (usernameErr != null || emailErr != null || passwordErr != null) {
            _registerForm.update {
                it.copy(
                    usernameError = usernameErr,
                    emailError    = emailErr,
                    passwordError = passwordErr,
                )
            }
            return
        }

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            try {
                val response = ApiClient.api.register(
                    RegisterRequest(form.username, form.email.trim(), form.password)
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.id != null) {
                        _authState.value = AuthUiState.RegisterSuccess
                    } else {
                        _authState.value = AuthUiState.Error(body?.error ?: "Registration failed")
                    }
                } else {
                    val message = when (response.code()) {
                        409  -> "Email already in use"
                        400  -> "Invalid details"
                        else -> "Registration failed (${response.code()})"
                    }
                    _authState.value = AuthUiState.Error(message)
                }

            } catch (t: Throwable) {
                Log.e("AuthVM", "Register error", t)
                _authState.value = AuthUiState.Error(networkErrorMessage(t))
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Clear error state when the user starts typing after a failure. */
    private fun resetErrorIfActive() {
        if (_authState.value is AuthUiState.Error) {
            _authState.value = AuthUiState.Idle
        }
    }

    private fun networkErrorMessage(t: Throwable): String = when (t) {
        is ExceptionInInitializerError -> "Check API base URL in ApiClient."
        else -> "Cannot reach server. Check your connection."
    }
}