# Unified ViewModel Architecture - Best Practices & Patterns

## Core Principles

### 1. Single Responsibility
Each ViewModel should handle one domain entity or feature. The coordinator handles cross-ViewModel communication.

### 2. Event-Driven Updates
Changes flow through events, not direct method calls between ViewModels.

```kotlin
// ❌ DON'T: Direct coupling
dashboardViewModel.refresh()  // Don't call other ViewModels directly

// ✅ DO: Use events
publishEvent(StateEvent.TransactionCreated(...))
// DashboardViewModel subscribes and refreshes automatically
```

### 3. Immutable State
Always use data classes with `.copy()` to ensure state immutability.

```kotlin
// ✅ DO: Immutable state updates
_state.value = _state.value.copy(
    isLoading = false,
    data = newData,
    error = null
)

// ❌ DON'T: Mutate state
_state.value.data = newData  // Wrong!
```

## Common Patterns

### Pattern 1: Automatic Refresh on Related Changes

When one resource changes, automatically refresh dependent resources.

```kotlin
class DashboardViewModel : UnifiedViewModel() {
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            // Transactions affect dashboard totals
            is StateEvent.TransactionCreated -> {
                viewModelScope.launch { refresh() }
            }
            // Channel changes affect dashboard structure
            is StateEvent.ChannelCreated,
            is StateEvent.ChannelDeleted -> {
                viewModelScope.launch { refresh() }
            }
            else -> {}
        }
    }
}
```

### Pattern 2: Cascade Initialization

When user logs in, load all required data in correct order.

```kotlin
class AppInitializationViewModel : UnifiedViewModel() {
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.UserLoggedIn -> {
                viewModelScope.launch {
                    // Load in order of dependency
                    loadAccounts()           // Must come first
                    publishEvent(StateEvent.AccountUpdated)
                    
                    loadChannels()           // Depends on account
                    publishEvent(StateEvent.ChannelsLoaded)
                    
                    loadTransactions()       // Can load in parallel with channels
                    publishEvent(StateEvent.TransactionsLoaded)
                    
                    loadDashboard()          // Depends on all above
                    publishEvent(StateEvent.DashboardLoaded)
                    
                    // Signal complete initialization
                    publishEvent(StateEvent.SyncRequested)
                }
            }
            else -> {}
        }
    }
}
```

### Pattern 3: Error Propagation

Handle errors at source, propagate for app-wide handling if needed.

```kotlin
class TransactionViewModel : UnifiedViewModel() {
    fun createTransaction(...) {
        viewModelScope.launch {
            try {
                val response = apiService.createTransaction(...)
                if (response.isSuccessful) {
                    publishEvent(StateEvent.TransactionCreated(...))
                } else {
                    // Handle known errors locally
                    _state.value = _state.value.copy(
                        error = "Failed to create transaction"
                    )
                    // Also publish for app-wide error tracking
                    publishEvent(StateEvent.ErrorOccurred(
                        source = "TransactionViewModel",
                        message = "API error: ${response.code()}"
                    ))
                }
            } catch (e: Exception) {
                // Handle session expired
                if (isSessionExpired(e)) {
                    publishEvent(StateEvent.UserLoggedOut)
                } else {
                    _state.value = _state.value.copy(error = e.message)
                    publishEvent(StateEvent.ErrorOccurred(
                        source = "TransactionViewModel",
                        message = e.message ?: "Unknown error"
                    ))
                }
            }
        }
    }
}
```

### Pattern 4: Prevent Redundant Refreshes

Use state tracking to avoid unnecessary API calls.

```kotlin
class DashboardViewModel : UnifiedViewModel() {
    private var isRefreshInProgress = false
    
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.TransactionCreated -> {
                if (!isRefreshInProgress) {
                    isRefreshInProgress = true
                    viewModelScope.launch {
                        try {
                            refresh()
                        } finally {
                            isRefreshInProgress = false
                        }
                    }
                }
            }
            else -> {}
        }
    }
}
```

Or use a flow-based approach:

```kotlin
private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

init {
    viewModelScope.launch {
        refreshTrigger
            .debounce(300)  // Avoid rapid cascades
            .collect { refresh() }
    }
}

override fun onStateEvent(event: StateEvent) {
    when (event) {
        is StateEvent.TransactionCreated -> {
            refreshTrigger.tryEmit(Unit)
        }
        else -> {}
    }
}
```

### Pattern 5: Optimistic Updates

Update UI immediately while API request is in flight.

```kotlin
class ChannelViewModel : UnifiedViewModel() {
    fun updateChannel(id: String, name: String) {
        viewModelScope.launch {
            // Optimistic update
            val updatedChannels = _state.value.channels.map { channel ->
                if (channel.id == id) channel.copy(name = name) else channel
            }
            _state.value = _state.value.copy(channels = updatedChannels)
            
            try {
                val response = apiService.updateChannel(id, UpdateRequest(name))
                if (response.isSuccessful) {
                    publishEvent(StateEvent.ChannelUpdated(id, name))
                } else {
                    // Revert on failure
                    _state.value = _state.value.copy(
                        channels = _state.value.channels.map { channel ->
                            if (channel.id == id) channel.copy(name = channel.oldName) else channel
                        },
                        error = "Failed to update"
                    )
                }
            } catch (e: Exception) {
                // Revert on error
                _state.value = _state.value.copy(error = "Network error")
            }
        }
    }
}
```

### Pattern 6: Throttling High-Frequency Events

Prevent event storms from triggering excessive refreshes.

```kotlin
class AnalyticsViewModel : UnifiedViewModel() {
    private val eventThrottler = MutableSharedFlow<StateEvent>(
        extraBufferCapacity = 1
    )
    
    override fun onStateEvent(event: StateEvent) {
        eventThrottler.tryEmit(event)
    }
    
    init {
        viewModelScope.launch {
            eventThrottler
                .debounce(500)  // Wait 500ms before processing
                .collect { event ->
                    handleThrottledEvent(event)
                }
        }
    }
    
    private fun handleThrottledEvent(event: StateEvent) {
        when (event) {
            is StateEvent.TransactionCreated -> {
                viewModelScope.launch { refresh() }
            }
            else -> {}
        }
    }
}
```

### Pattern 7: Conditional Updates

Only refresh if relevant data actually changed.

```kotlin
class BudgetViewModel : UnifiedViewModel() {
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.TransactionCreated -> {
                // Only refresh if transaction is in current period
                if (isInCurrentBudgetPeriod(event)) {
                    viewModelScope.launch { refresh() }
                }
            }
            is StateEvent.ChannelDeleted -> {
                // Only refresh if we have budgets for this channel
                if (hasChannelBudgets(event.channelId)) {
                    viewModelScope.launch { refresh() }
                }
            }
            else -> {}
        }
    }
}
```

### Pattern 8: Batch Notifications

Group related changes before notifying.

```kotlin
class TransactionViewModel : UnifiedViewModel() {
    private val pendingNotifications = mutableListOf<String>()
    
    fun createMultipleTransactions(requests: List<TransactionRequest>) {
        viewModelScope.launch {
            for (request in requests) {
                val response = apiService.createTransaction(request)
                if (response.isSuccessful) {
                    pendingNotifications.add(response.body()?.id ?: "")
                }
            }
            
            // Single batch notification instead of one per transaction
            if (pendingNotifications.isNotEmpty()) {
                publishEvent(StateEvent.TransactionsLoaded)  // Generic refresh event
                pendingNotifications.clear()
            }
        }
    }
}
```

## Anti-Patterns

### ❌ Circular Event Dependencies

```kotlin
// DON'T create event cycles!
class ViewModelA : UnifiedViewModel() {
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.EventFromB -> publishEvent(StateEvent.EventForB)
        }
    }
}

class ViewModelB : UnifiedViewModel() {
    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.EventForB -> publishEvent(StateEvent.EventFromB)
        }
    }
}
```

**Fix**: Add checks to break cycles
```kotlin
private var isProcessing = false

override fun onStateEvent(event: StateEvent) {
    if (isProcessing) return
    isProcessing = true
    try {
        when (event) { /* ... */ }
    } finally {
        isProcessing = false
    }
}
```

### ❌ Blocking Operations in Event Handlers

```kotlin
// DON'T block the event thread
override fun onStateEvent(event: StateEvent) {
    when (event) {
        is StateEvent.TransactionCreated -> {
            Thread.sleep(1000)  // WRONG!
            refresh()
        }
        else -> {}
    }
}
```

**Fix**: Use coroutines
```kotlin
override fun onStateEvent(event: StateEvent) {
    when (event) {
        is StateEvent.TransactionCreated -> {
            viewModelScope.launch {
                delay(1000)
                refresh()
            }
        }
        else -> {}
    }
}
```

### ❌ Ignoring Event Result Failures

```kotlin
// DON'T ignore when events fail to process
override fun onStateEvent(event: StateEvent) {
    when (event) {
        is StateEvent.TransactionCreated -> {
            try {
                // Process event
            } catch (e: Exception) {
                // Silent failure!
            }
        }
        else -> {}
    }
}
```

**Fix**: Log and handle errors appropriately
```kotlin
override fun onStateEvent(event: StateEvent) {
    when (event) {
        is StateEvent.TransactionCreated -> {
            viewModelScope.launch {
                try {
                    // Process event
                } catch (e: Exception) {
                    Log.e("ViewModel", "Failed to handle event", e)
                    publishEvent(StateEvent.ErrorOccurred(
                        source = this::class.simpleName ?: "Unknown",
                        message = e.message ?: "Unknown error"
                    ))
                }
            }
        }
        else -> {}
    }
}
```

## Testing Strategies

### Unit Testing Event Publishing

```kotlin
@Test
fun testTransactionCreatePublishesEvent() = runTest {
    val publishedEvents = mutableListOf<StateEvent>()
    
    val job = launch {
        UnifiedStateCoordinator.events.collect { event ->
            publishedEvents.add(event)
        }
    }
    
    val viewModel = TransactionViewModel()
    viewModel.createTransaction("ch_1", 100.0, "DEBIT", "test")
    
    advanceUntilIdle()
    
    val createdEvent = publishedEvents.find { it is StateEvent.TransactionCreated }
    assertNotNull(createdEvent)
    
    job.cancel()
}
```

### Integration Testing Event Flow

```kotlin
@Test
fun testTransactionCreateRefreshesDashboard() = runTest {
    val dashboardVM = DashboardViewModel(mockApiService, mockNetworkMonitor)
    val transactionVM = TransactionViewModel(mockApiService)
    
    var dashboardRefreshCount = 0
    val job = launch {
        dashboardVM.state.collect { state ->
            if (state.phase is DashboardPhase.Loaded) {
                dashboardRefreshCount++
            }
        }
    }
    
    // Simulate transaction creation
    UnifiedStateCoordinator.publishEvent(
        StateEvent.TransactionCreated("tx_1", 100.0, "ch_1")
    )
    
    advanceUntilIdle()
    
    assertTrue(dashboardRefreshCount > 0, "Dashboard should refresh on transaction")
    job.cancel()
}
```

## Performance Tips

1. **Use Type-Specific Filters**
   ```kotlin
   // Better than checking instance in every event
   subscribeToEvent<StateEvent.TransactionCreated> { event ->
       // Only called for this event type
   }
   ```

2. **Debounce Rapid Events**
   ```kotlin
   UnifiedStateCoordinator.events
       .debounce(300)
       .collect { event -> handleEvent(event) }
   ```

3. **Cancel Subscriptions When Needed**
   ```kotlin
   viewModelScope.launch {
       // Automatically cancelled when ViewModel is cleared
       UnifiedStateCoordinator.events.collect { event -> }
   }
   ```

4. **Monitor Event Queue Size**
   - Events are buffered with `extraBufferCapacity = 100`
   - If you see buffer overflow warnings, increase or throttle events

## Debugging

### Enable Event Logging

```kotlin
// During development, log all events
if (BuildConfig.DEBUG) {
    viewModelScope.launch {
        UnifiedStateCoordinator.events
            .logEvents()
            .collect { event ->
                handleEvent(event)
            }
    }
}
```

### Check Event Dependencies

```kotlin
// In your coordinator setup
fun setupEventDependencies() {
    // Document expected event flow
    mapOf(
        StateEvent.UserLoggedIn::class to listOf(
            StateEvent.AccountUpdated::class,
            StateEvent.ChannelsLoaded::class,
        ),
        StateEvent.TransactionCreated::class to listOf(
            StateEvent.DashboardRefreshed::class,
            StateEvent.AnalyticsRefreshed::class,
        ),
    )
}
```

## Migration Path

1. **Phase 1**: Create coordinator files (done ✅)
2. **Phase 2**: Convert ViewModels one by one, starting with leaf nodes
   - TransactionViewModel
   - ChannelViewModel
   - AccountViewModel
3. **Phase 3**: Convert dependent ViewModels
   - DashboardViewModel
   - AnalyticsViewModel
   - BudgetViewModel
4. **Phase 4**: Convert root ViewModels
   - AuthViewModel
   - UnlockViewModel
5. **Phase 5**: Integration testing and optimization

## Summary

The unified ViewModel architecture provides:
- ✅ Loose coupling between features
- ✅ Automatic data synchronization
- ✅ Centralized event tracking
- ✅ Easy debugging and testing
- ✅ Scalable to many ViewModels

By following these patterns and best practices, you'll build a robust, maintainable Android architecture.

