# Quick Start: Implementing Unified ViewModels

This is a step-by-step implementation guide to apply the unified ViewModel architecture to your existing ViewModels.

## Prerequisites

✅ All coordinator files created in `/app/src/main/java/cc/dlabs/pesamind/core/coordinator/`
- `StateEvent.kt`
- `UnifiedStateCoordinator.kt`
- `UnifiedViewModel.kt`
- `ViewModelDependencyRegistry.kt`
- `UnifiedStateExtensions.kt`
- `UnifiedStateCoordinatorModule.kt` (optional, for Hilt)

## Step 1: Update TransactionViewModel

**File**: `app/src/main/java/cc/dlabs/pesamind/core/utils/TransactionViewModel.kt`

```kotlin
// BEFORE
class TransactionViewModel : ViewModel() {
    private val _state = MutableStateFlow(TransactionState(isLoading = true))
    val state: StateFlow<TransactionState> = _state.asStateFlow()

    // AFTER
class TransactionViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(TransactionState(isLoading = true))
    val state: StateFlow<TransactionState> = _state.asStateFlow()
    
    // Add this to react to related changes
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            // If channel is deleted, refresh transactions
            is StateEvent.ChannelDeleted -> {
                loadTransactions()
            }
            // If user logs out, clear transactions
            is StateEvent.UserLoggedOut -> {
                _state.value = _state.value.copy(
                    transactions = emptyList(),
                    error = null,
                    isLoading = false
                )
            }
            else -> {}
        }
    }

    // In CreateTransaction method, after successful creation, add:
    public fun CreateTransaction(
        channelID: String,
        amount: Double,
        type: String,
        note: String,
    ){
        // ... existing validation ...
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val response = ApiClient.api.createTransaction(
                    TransactionRequest(
                        channelId = channelID,
                        amount = amount,
                        type = normalizedType,
                        note = note.trim(),
                    )
                )
                if (response.isSuccessful) {
                    val created = response.body()
                    _state.value = _state.value.copy(
                        isSaving = false,
                        message = "Transaction created successfully",
                        transactions = if (created != null) _state.value.transactions + created else _state.value.transactions
                    )
                    
                    // 🔥 ADD THIS - Notify other ViewModels
                    publishEvent(StateEvent.TransactionCreated(
                        transactionId = created?.id ?: "",
                        amount = amount,
                        channelId = channelID
                    ))
                    
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = "Failed to create transaction (${response.code()})"
                    )
                }
            }
            catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }
}
```

**Changes Summary**:
- Line 1: Change from `ViewModel` to `UnifiedViewModel`
- Add: `override fun onStateEvent(...)` method
- Add: `publishEvent(StateEvent.TransactionCreated(...))` after successful creation
- Add: Import `cc.dlabs.pesamind.core.coordinator.*`

---

## Step 2: Update DashboardViewModel

**File**: `app/src/main/java/cc/dlabs/pesamind/features/dashboard/DashboardViewModel.kt`

```kotlin
// BEFORE
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val apiService: ApiService,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    // AFTER
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val apiService: ApiService,
    private val networkMonitor: NetworkMonitor,
) : UnifiedViewModel() {
    
    // Add this right after init block (line ~63)
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            // Auto-refresh when transactions are created
            is StateEvent.TransactionCreated -> {
                viewModelScope.launch {
                    refresh()
                    publishEvent(StateEvent.DashboardRefreshed)
                }
            }
            
            // Auto-refresh when channels change
            is StateEvent.ChannelCreated,
            is StateEvent.ChannelUpdated,
            is StateEvent.ChannelDeleted -> {
                viewModelScope.launch {
                    refresh()
                }
            }
            
            // Respond to logout
            is StateEvent.UserLoggedOut -> {
                _state.update {
                    it.copy(
                        dashboard = null,
                        phase = DashboardPhase.Idle
                    )
                }
            }
            
            // Handle sync requests
            is StateEvent.SyncRequested -> {
                viewModelScope.launch { refresh() }
            }
            
            else -> {}
        }
    }
}
```

**Changes Summary**:
- Line 44: Change from `ViewModel` to `UnifiedViewModel`
- Add: `override fun onStateEvent(...)` method to handle related events
- This enables auto-refresh when transactions are created

---

## Step 3: Update AnalyticsViewModel

**File**: `app/src/main/java/cc/dlabs/pesamind/features/analytics/AnalyticsViewModel.kt`

```kotlin
// BEFORE
@HiltViewModel
class AnalyticsViewModel @Inject constructor() : ViewModel() {

    // AFTER
@HiltViewModel
class AnalyticsViewModel @Inject constructor() : UnifiedViewModel() {
    
    // Add this right after state definitions (line ~44)
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            // Auto-refresh when transactions are created
            is StateEvent.TransactionCreated -> {
                viewModelScope.launch {
                    refresh()
                    publishEvent(StateEvent.AnalyticsRefreshed)
                }
            }
            
            // Auto-refresh when channels change
            is StateEvent.ChannelCreated,
            is StateEvent.ChannelUpdated,
            is StateEvent.ChannelDeleted -> {
                viewModelScope.launch {
                    refresh()
                }
            }
            
            // Respond to logout
            is StateEvent.UserLoggedOut -> {
                _state.value = _state.value.copy(
                    analytics = null,
                    phase = AnalyticsPhase.Idle
                )
            }
            
            else -> {}
        }
    }
}
```

**Changes Summary**:
- Line 41: Change from `ViewModel` to `UnifiedViewModel`
- Add: `override fun onStateEvent(...)` method
- Analytics will now auto-refresh when transactions/channels change

---

## Step 4: Update ChannelViewModel

**File**: `app/src/main/java/cc/dlabs/pesamind/features/settings/channels/ChannelViewModel.kt`

```kotlin
// BEFORE
class ChannelViewModel : ViewModel() {

    // AFTER
class ChannelViewModel : UnifiedViewModel() {
    
    // Add this after state definitions (line ~27)
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.UserLoggedOut -> {
                _state.value = _state.value.copy(
                    channels = emptyList(),
                    error = null,
                    isLoading = false
                )
            }
            else -> {}
        }
    }

    // In createChannel method, after successful response, add (line ~190):
    fun createChannel(...) {
        // ... existing validation ...
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val response = ApiClient.api.createChannel(body = body)
                
                if (response.isSuccessful) {
                    val created = response.body()
                    _state.value = _state.value.copy(
                        isSaving = false,
                        message = "Channel created successfully",
                        channels = if (created != null) _state.value.channels + created else _state.value.channels
                    )
                    
                    // 🔥 ADD THIS
                    publishEvent(StateEvent.ChannelCreated(
                        channelId = created?.id ?: "",
                        channelName = created?.name ?: ""
                    ))
                    
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = "Failed to create channel (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    // In updateChannel method, after successful response (line ~234):
    fun updateChannel(...) {
        if (id.isBlank()) {
            _state.value = _state.value.copy(error = "Invalid channel id")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val response = ApiClient.api.updateChannel(
                    id = id,
                    body = UpdateChannelRequest(
                        name = name.trim(),
                        description = description.trim(),
                        status = status
                    )
                )

                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        message = response.body()?.message ?: "Channel updated successfully"
                    )
                    loadChannels()
                    
                    // 🔥 ADD THIS
                    publishEvent(StateEvent.ChannelUpdated(
                        channelId = id,
                        channelName = name
                    ))
                    
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = "Failed to update channel (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    // In deleteChannel method, after successful response (line ~265):
    fun deleteChannel(id: String) {
        if (id.isBlank()) {
            _state.value = _state.value.copy(error = "Invalid channel id")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isDeleting = true, error = null)
            try {
                val response = ApiClient.api.deleteChannel(id)
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        isDeleting = false,
                        message = response.body()?.message ?: "Channel deleted successfully",
                        channels = _state.value.channels.filterNot { it.id == id }
                    )
                    
                    // 🔥 ADD THIS
                    publishEvent(StateEvent.ChannelDeleted(channelId = id))
                    
                } else {
                    _state.value = _state.value.copy(
                        isDeleting = false,
                        error = "Failed to delete channel (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isDeleting = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }
}
```

**Changes Summary**:
- Line 24: Change from `ViewModel` to `UnifiedViewModel`
- Add: `override fun onStateEvent(...)` method
- Add: `publishEvent()` calls in `createChannel()`, `updateChannel()`, `deleteChannel()`

---

## Step 5: Update AuthViewModel

**File**: `app/src/main/java/cc/dlabs/pesamind/features/auth/AuthViewModel.kt`

```kotlin
// BEFORE
class AuthViewModel : ViewModel() {

    // AFTER
class AuthViewModel : UnifiedViewModel() {
    
    // Update login method (line ~115), after successful login:
    fun login() {
        // ... existing validation ...
        
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
                        
                        // 🔥 ADD THIS - Notify other ViewModels
                        publishEvent(StateEvent.UserLoggedIn)

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
    
    // Add logout method that publishes event
    fun logout() {
        TokenManager.clearTokens()
        AccountManager.clearAccount()
        _authState.value = AuthUiState.Idle
        _loginForm.value = LoginFormState()
        _registerForm.value = RegisterFormState()
        
        // 🔥 Notify other ViewModels
        publishEvent(StateEvent.UserLoggedOut)
    }
}
```

**Changes Summary**:
- Line 46: Change from `ViewModel` to `UnifiedViewModel`
- Add: `publishEvent(StateEvent.UserLoggedIn)` in login method
- Add: `publishEvent(StateEvent.UserLoggedOut)` in logout method

---

## Step 6: Update BudgetViewModel

**File**: `app/src/main/java/cc/dlabs/pesamind/features/tools/BudgetViewModel.kt`

```kotlin
// Add at class level
class BudgetViewModel : UnifiedViewModel() {
    
    // Add this method
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            // Auto-refresh when transactions are created
            is StateEvent.TransactionCreated -> {
                viewModelScope.launch {
                    // Only refresh if it affects current budget
                    if (isInCurrentBudgetPeriod()) {
                        refresh()
                        publishEvent(StateEvent.BudgetUpdated)
                    }
                }
            }
            
            // Respond to logout
            is StateEvent.UserLoggedOut -> {
                clearBudgets()
            }
            
            else -> {}
        }
    }
    
    // Add this helper
    private fun isInCurrentBudgetPeriod(): Boolean {
        // Implement based on your budget period logic
        return true
    }
}
```

---

## Step 7: Update AccountViewModel

**File**: `app/src/main/java/cc/dlabs/pesamind/features/settings/account/AccountViewModel.kt`

```kotlin
// Add at class level
class AccountViewModel : UnifiedViewModel() {
    
    // Add this method
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            // Refresh account on certain events
            is StateEvent.UserLoggedIn -> {
                loadAccount()
            }
            
            is StateEvent.UserLoggedOut -> {
                _state.value = _state.value.copy(
                    // Clear account data
                )
            }
            
            else -> {}
        }
    }
}
```

---

## Step 8: Update UnlockViewModel

**File**: `app/src/main/java/cc/dlabs/pesamind/features/auth/UnlockViewModel.kt`

```kotlin
// Add at class level
class UnlockViewModel : UnifiedViewModel() {
    
    // Add this method (minimal changes needed)
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.UserLoggedOut -> {
                // Might need to reset unlock state
            }
            else -> {}
        }
    }
}
```

---

## Verification Checklist

After implementing these changes:

- [ ] All ViewModels extend `UnifiedViewModel`
- [ ] All ViewModels import `cc.dlabs.pesamind.core.coordinator.*`
- [ ] All significant operations publish events:
  - [ ] Transaction created → `publishEvent(StateEvent.TransactionCreated(...))`
  - [ ] Channel created → `publishEvent(StateEvent.ChannelCreated(...))`
  - [ ] Channel updated → `publishEvent(StateEvent.ChannelUpdated(...))`
  - [ ] Channel deleted → `publishEvent(StateEvent.ChannelDeleted(...))`
  - [ ] User logged in → `publishEvent(StateEvent.UserLoggedIn)`
  - [ ] User logged out → `publishEvent(StateEvent.UserLoggedOut)`
- [ ] All dependent ViewModels implement `onStateEvent()` to handle relevant events
- [ ] App compiles without errors
- [ ] Test cascade updates:
  - Create transaction → Dashboard refreshes
  - Create channel → Dashboard & Analytics refresh
  - Login → All ViewModels initialize data
  - Logout → All ViewModels clear data

## Testing

```kotlin
@Test
fun testTransactionCreateRefreshesDashboard() = runTest {
    val dashboardVM = DashboardViewModel(mockApiService, mockNetworkMonitor)
    
    var dashboardRefreshCount = 0
    val job = launch {
        dashboardVM.state.collect { state ->
            if (state.phase is DashboardPhase.Loaded) {
                dashboardRefreshCount++
            }
        }
    }
    
    // Simulate event
    UnifiedStateCoordinator.publishEvent(
        StateEvent.TransactionCreated("tx_1", 100.0, "ch_1")
    )
    
    advanceUntilIdle()
    assertTrue(dashboardRefreshCount > 0)
    job.cancel()
}
```

## That's It! 🎉

You now have a fully unified ViewModel architecture with automatic state synchronization across your entire app.

When a transaction is created:
1. TransactionViewModel creates it and publishes `TransactionCreated` event
2. DashboardViewModel receives the event and auto-refreshes
3. AnalyticsViewModel receives the event and auto-refreshes
4. BudgetViewModel receives the event and auto-refreshes
5. All UIs automatically update without manual orchestration

No tight coupling. No manual coordination. Just reactive state management.

