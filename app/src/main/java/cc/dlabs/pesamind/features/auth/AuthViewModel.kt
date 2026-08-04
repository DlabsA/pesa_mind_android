package cc.dlabs.pesamind.features.auth

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.navigation.Routes
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

    // Google OAuth states
    data class GoogleSignInNeeded(val message: String = "") : AuthUiState

    data class GoogleSignupSuccess(val destination: String) : AuthUiState
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

class AuthViewModel : UnifiedViewModel() {
    // Repositories
    private val googleAuthRepository = GoogleAuthRepository()

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
        val emailErr =
            when {
                form.email.isBlank() -> "Email is required"
                !android.util.Patterns.EMAIL_ADDRESS.matcher(form.email.trim()).matches() -> "Enter a valid email"
                else -> null
            }
        val passwordErr =
            when {
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
                                id = profile.id ?: "",
                                email = form.email.trim(),
                                username = profile.username ?: "",
                                avatarUrl = profile.avatarUrl ?: "",
                                balance = profile.balance?.toString() ?: "",
                                type = profile.type ?: "",
                            )
                        }
                        // Server-true-wins: only ever flips the local flag true, never clears an
                        // already-true local flag back to false (covers finishing onboarding
                        // offline before this sync, and reinstall-on-already-onboarded-account).
                        if (body.profile?.channelsOnboarded == true) {
                            TokenManager.setChannelsOnboarded(true)
                        }

                        _authState.value = AuthUiState.LoginSuccess(resolvePostAuthDestination())
                    } else {
                        _authState.value = AuthUiState.Error(body?.error ?: "Invalid email or password")
                    }
                } else {
                    val message =
                        extractErrorFromResponse(response)
                            ?: when (response.code()) {
                                401 -> "Invalid email or password"
                                404 -> "Account not found"
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
        val emailErr =
            when {
                form.email.isBlank() -> "Email is required"
                !android.util.Patterns.EMAIL_ADDRESS.matcher(form.email.trim()).matches() -> "Enter a valid email"
                else -> null
            }
        val passwordErr =
            when {
                form.password.isBlank() -> "Password is required"
                form.password.length < 6 -> "Password must be at least 6 characters"
                else -> null
            }

        if (usernameErr != null || emailErr != null || passwordErr != null) {
            _registerForm.update {
                it.copy(
                    usernameError = usernameErr,
                    emailError = emailErr,
                    passwordError = passwordErr,
                )
            }
            return
        }

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            try {
                val response =
                    ApiClient.api.register(
                        RegisterRequest(form.username, form.email.trim(), form.password),
                    )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.id != null) {
                        _authState.value = AuthUiState.RegisterSuccess
                    } else {
                        _authState.value = AuthUiState.Error(body?.error ?: "Registration failed")
                    }
                } else {
                    val message =
                        extractErrorFromResponse(response)
                            ?: when (response.code()) {
                                409 -> "Email already in use"
                                400 -> "Invalid details"
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

    // ── Google OAuth ──────────────────────────────────────────────────────────

    /**
     * Handles Google Sign-In errors and displays them to the user
     */
    fun handleGoogleSignInError(errorMessage: String) {
        Log.e("AuthVM", "Google Sign-In error: $errorMessage")
        _authState.value = AuthUiState.Error(errorMessage)
    }

    /**
     * Handles Google Sign-In using platform-specific OAuth endpoint.
     *
     * NEW SIMPLIFIED FLOW:
     * - Backend now auto-generates username from google_display_name
     * - No username selection dialog needed
     * - Tokens are returned immediately on first request
     * - Both new and existing users get same flow
     */
    fun handleGoogleSignIn(
        email: String,
        googleId: String,
        displayName: String?,
        profilePhotoUrl: String?,
    ) {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            try {
                Log.d("AuthVM", "Signing in with Google account details using platform-specific endpoint...")
                val result =
                    googleAuthRepository.platformGoogleSignIn(
                        // Android platform identifier
                        platform = "android",
                        email = email,
                        googleId = googleId,
                        displayName = displayName,
                        profilePhotoUrl = profilePhotoUrl,
                    )

                result.onSuccess { response ->
                    Log.d("AuthVM", "Google sign-in response: isNewUser=${response.isNewUser}, hasTokens=${response.accessToken != null}")

                    // NEW FLOW: Backend handles everything (username generation, account creation, etc.)
                    // Both new and existing users get tokens back
                    if (response.accessToken != null && response.refreshToken != null) {
                        Log.d("AuthVM", "Saving tokens and account info")
                        TokenManager.saveTokens(response.accessToken, response.refreshToken)

                        response.profile?.let { profile ->
                            AccountManager.saveAccount(
                                id = profile.id ?: "",
                                email = email,
                                username = profile.username ?: "",
                                avatarUrl = profile.avatarUrl ?: "",
                                balance = profile.balance?.toString() ?: "",
                                type = profile.type ?: "",
                            )
                        }
                        // Server-true-wins — see login()'s identical comment.
                        if (response.profile?.channelsOnboarded == true) {
                            TokenManager.setChannelsOnboarded(true)
                        }

                        val destination = resolvePostAuthDestination()

                        // Both new users and returning users are logged in successfully
                        if (response.isNewUser) {
                            Log.d("AuthVM", "New user created with auto-generated username")
                            _authState.value = AuthUiState.GoogleSignupSuccess(destination)
                        } else {
                            Log.d("AuthVM", "Existing user logged in")
                            _authState.value = AuthUiState.LoginSuccess(destination)
                        }
                    } else {
                        _authState.value = AuthUiState.Error("No tokens received from server")
                    }
                }.onFailure { error ->
                    Log.e("AuthVM", "Google platform sign-in failed", error)
                    _authState.value = AuthUiState.Error(error.message ?: "Sign-in failed")
                }
            } catch (t: Throwable) {
                Log.e("AuthVM", "Google sign-in error", t)
                _authState.value = AuthUiState.Error(networkErrorMessage(t))
            }
        }
    }

    /**
     * DEPRECATED: No longer needed in simplified OAuth flow.
     * Backend now auto-generates username from google_display_name.
     *
     * Kept for backward compatibility only.
     * New flow uses handleGoogleSignIn() which returns tokens immediately.
     */
    @Deprecated("Backend now auto-generates username. Use handleGoogleSignIn() instead.")
    fun completeGoogleSignup(
        email: String,
        googleId: String,
        username: String,
        displayName: String?,
        profilePhotoUrl: String?,
    ) {
        // Validate username
        val usernameErr = validateUsername(username)
        if (usernameErr != null) {
            _authState.value = AuthUiState.Error(usernameErr)
            return
        }

        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            try {
                Log.d("AuthVM", "Completing Google signup with username: $username")
                val result =
                    googleAuthRepository.completeGoogleSignup(
                        email = email,
                        googleId = googleId,
                        username = username,
                        displayName = displayName,
                        profilePhotoUrl = profilePhotoUrl,
                    )

                result.onSuccess { response ->
                    Log.d("AuthVM", "Google signup completed, saving tokens")
                    if (response.accessToken != null && response.refreshToken != null) {
                        TokenManager.saveTokens(response.accessToken, response.refreshToken)

                        response.profile?.let { profile ->
                            AccountManager.saveAccount(
                                id = profile.id ?: "",
                                email = email,
                                username = profile.username ?: username,
                                avatarUrl = profile.avatarUrl ?: "",
                                balance = profile.balance?.toString() ?: "",
                                type = profile.type ?: "",
                            )
                        }
                        if (response.profile?.channelsOnboarded == true) {
                            TokenManager.setChannelsOnboarded(true)
                        }

                        _authState.value = AuthUiState.GoogleSignupSuccess(resolvePostAuthDestination())
                    } else {
                        _authState.value = AuthUiState.Error("No tokens received from server")
                    }
                }.onFailure { error ->
                    Log.e("AuthVM", "Google signup failed", error)
                    _authState.value = AuthUiState.Error(error.message ?: "Signup failed")
                }
            } catch (t: Throwable) {
                Log.e("AuthVM", "Google signup error", t)
                _authState.value = AuthUiState.Error(networkErrorMessage(t))
            }
        }
    }

    /**
     * DEPRECATED: No longer needed in simplified OAuth flow.
     * Backend now auto-generates and manages usernames.
     *
     * Kept for backward compatibility only.
     */
    @Deprecated("Backend now auto-generates username. Manual username checking not needed.")
    fun checkUsernameAvailability(username: String) {
        // Quick local validation first
        val localError = validateUsername(username)
        if (localError != null) {
            _authState.value = AuthUiState.Error(localError)
            return
        }

        viewModelScope.launch {
            try {
                Log.d("AuthVM", "Checking username availability: $username")
                val result = googleAuthRepository.checkUsername(username)

                result.onSuccess { response ->
                    if (!response.available) {
                        _authState.value = AuthUiState.Error("Username is already taken")
                    }
                    // Don't update state if available - let the user proceed
                }.onFailure { error ->
                    Log.e("AuthVM", "Username check failed", error)
                    // Don't update state on error - let user proceed anyway
                }
            } catch (t: Throwable) {
                Log.e("AuthVM", "Username check error", t)
                // Don't update state on error
            }
        }
    }

    /**
     * Validates username according to requirements:
     * - Min 3 chars, max 50 chars
     * - Alphanumeric and underscore only
     */
    private fun validateUsername(username: String): String? =
        when {
            username.isBlank() -> "Username is required"
            username.length < 3 -> "Username must be at least 3 characters"
            username.length > 50 -> "Username must be at most 50 characters"
            !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username can only contain letters, numbers, and underscores"
            else -> null
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Single source of truth for post-auth routing, called from every success path (email
     * login, Google sign-in of an existing user, Google sign-up of a new user) instead of each
     * one independently duplicating the same `when` — the duplication previously let the
     * channel-onboarding gate get added to some call sites and not others. A brand-new Google
     * signup can never already be onboarded, so this still returns the correct destination for
     * that case without a special-cased shortcut.
     */
    private suspend fun resolvePostAuthDestination(): String =
        when (TokenManager.getLockState()) {
            LockState.NONE ->
                if (!TokenManager.isChannelsOnboarded()) Routes.ChannelOnboardingIntro.route else Routes.Dashboard.route
            LockState.PIN -> Routes.PinUnlock.route
            LockState.PATTERN -> Routes.PatternUnlock.route
        }

    /** Clear error state when the user starts typing after a failure. */
    private fun resetErrorIfActive() {
        if (_authState.value is AuthUiState.Error) {
            _authState.value = AuthUiState.Idle
        }
    }

    private fun networkErrorMessage(t: Throwable): String =
        when (t) {
            is ExceptionInInitializerError -> "Check API base URL in ApiClient."
            else -> "Cannot reach server. Check your connection."
        }

    /**
     * Extract error message from HTTP response body (JSON: {"error": "message"}).
     * Returns null if unable to parse, letting the caller fall back to status-code-based messages.
     */
    private fun extractErrorFromResponse(response: retrofit2.Response<*>): String? {
        return try {
            val errorBody = response.errorBody()?.string() ?: return null
            val gson = com.google.gson.Gson()
            val errorObj = gson.fromJson(errorBody, Map::class.java)
            (errorObj?.get("error") as? String)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w("AuthVM", "Failed to parse error response", e)
            null
        }
    }
}
