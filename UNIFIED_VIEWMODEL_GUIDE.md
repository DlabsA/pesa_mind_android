# Unified ViewModel Coordination System

## Overview

This is a centralized state coordination system that enables automatic synchronization of data across multiple ViewModels. When one ViewModel makes a change (like creating a transaction), other dependent ViewModels (like Dashboard and Analytics) automatically refresh their data.

## Key Components

### 1. **StateEvent** (`StateEvent.kt`)
- Sealed class defining all possible state change events in the application
- Events are published by ViewModels when significant data changes occur
- Examples: `TransactionCreated`, `ChannelUpdated`, `UserLoggedIn`

### 2. **UnifiedStateCoordinator** (`UnifiedStateCoordinator.kt`)
- Central singleton that manages event publishing and broadcasting
- All ViewModels subscribe to this coordinator's event flow
- Provides both suspending and non-suspending publish methods

### 3. **UnifiedViewModel** (`UnifiedViewModel.kt`)
- Base class that all feature ViewModels should extend
- Automatically subscribes to all state events
- Provides `publishEvent()` helper method
- Override `onStateEvent()` to react to specific events

### 4. **ViewModelDependencyRegistry** (`ViewModelDependencyRegistry.kt`)
- Utility for managing complex dependency relationships
- Provides type-safe event filtering helpers
- Extensible for advanced scenarios

## Architecture Pattern

```
┌─────────────────────────────────────────────────────┐
│          UnifiedStateCoordinator (Singleton)        │
│          (Central Event Bus)                         │
└──────────────────┬──────────────────────────────────┘
                   │
        ┌──────────┼──────────┬──────────┐
        │          │          │          │
        ▼          ▼          ▼          ▼
   TransactionVM  DashboardVM  AnalyticsVM  ChannelVM
   (subscribes)   (subscribes) (subscribes) (subscribes)
   (publishes)    (publishes)  (publishes)  (publishes)

Event Flow:
1. User creates a transaction in TransactionViewModel
2. TransactionViewModel publishes TransactionCreated event
3. UnifiedStateCoordinator broadcasts the event
4. DashboardViewModel receives event → refreshes data
5. AnalyticsViewModel receives event → refreshes data
6. All UIs automatically update
```

## Usage Guide

### Step 1: Convert ViewModel to Extend UnifiedViewModel

**Before:**
```kotlin
class TransactionViewModel : ViewModel() {
    private val _state = MutableStateFlow(TransactionState())
    val state: StateFlow<TransactionState> = _state.asStateFlow()
    
    fun createTransaction(...) {
        // ... API call ...
    }
}
```

**After:**
```kotlin
class TransactionViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(TransactionState())
    val state: StateFlow<TransactionState> = _state.asStateFlow()
    
    fun createTransaction(...) {
        // ... API call ...
        
        // Publish event so other ViewModels know about the change
        publishEvent(StateEvent.TransactionCreated(
            transactionId = createdTx.id,
            amount = amount,
            channelId = channelID,
        ))
    }
}
```

### Step 2: Override onStateEvent() to React to Changes

```kotlin
class DashboardViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()
    
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
                clearDashboard()
            }
            
            else -> {} // Ignore other events
        }
    }
}
```

### Step 3: Publish Events from API Operations

```kotlin
class ChannelViewModel : UnifiedViewModel() {
    
    fun createChannel(...) {
        viewModelScope.launch {
            try {
                val response = ApiClient.api.createChannel(body)
                if (response.isSuccessful) {
                    val created = response.body()
                    _state.value = _state.value.copy(
                        channels = _state.value.channels + created
                    )
                    
                    // Notify other ViewModels
                    publishEvent(StateEvent.ChannelCreated(
                        channelId = created.id,
                        channelName = created.name,
                    ))
                }
            } catch (e: Exception) {
                // error handling
            }
        }
    }
    
    fun updateChannel(id: String, ...) {
        // ... update logic ...
        
        publishEvent(StateEvent.ChannelUpdated(
            channelId = id,
            channelName = newName,
        ))
    }
    
    fun deleteChannel(id: String) {
        // ... delete logic ...
        
        publishEvent(StateEvent.ChannelDeleted(channelId = id))
    }
}
```

## Common Usage Patterns

### Pattern 1: Auto-Refresh on Related Changes
```kotlin
override fun onStateEvent(event: StateEvent) {
    when (event) {
        is StateEvent.TransactionCreated,
        is StateEvent.ChannelUpdated -> {
            viewModelScope.launch { refresh() }
        }
        else -> {}
    }
}
```

### Pattern 2: Filter Events by Type
```kotlin
viewModelScope.launch {
    UnifiedStateCoordinator.events
        .filterEventType<StateEvent.TransactionCreated>()
        .collect { event ->
            updateTotalBalance(event.amount)
        }
}
```

### Pattern 3: Cascade Updates
```kotlin
// When user logs in, load all critical data
override fun onStateEvent(event: StateEvent) {
    when (event) {
        is StateEvent.UserLoggedIn -> {
            viewModelScope.launch {
                loadTransactions()
                loadChannels()
                loadDashboard()
                publishEvent(StateEvent.SyncRequested)  // Tell others we're done
            }
        }
        else -> {}
    }
}
```

### Pattern 4: Handle Errors Across App
```kotlin
override fun onStateEvent(event: StateEvent) {
    when (event) {
        is StateEvent.ErrorOccurred -> {
            if (event.source == "api" && event.message.contains("401")) {
                // Session expired - trigger logout
                publishEvent(StateEvent.UserLoggedOut)
            }
        }
        else -> {}
    }
}
```

## Migration Checklist

- [ ] Create the coordinator files (already done)
- [ ] Update `TransactionViewModel` to extend `UnifiedViewModel`
- [ ] Update `DashboardViewModel` to extend `UnifiedViewModel`
- [ ] Update `AnalyticsViewModel` to extend `UnifiedViewModel`
- [ ] Update `ChannelViewModel` to extend `UnifiedViewModel`
- [ ] Update `AuthViewModel` to extend `UnifiedViewModel`
- [ ] Update `BudgetViewModel` to extend `UnifiedViewModel`
- [ ] Update `AccountViewModel` to extend `UnifiedViewModel`
- [ ] Update `UnlockViewModel` to extend `UnifiedViewModel`
- [ ] Add `publishEvent()` calls after successful API operations
- [ ] Add `onStateEvent()` overrides to handle cross-ViewModel updates
- [ ] Test cascade updates work correctly
- [ ] Add Hilt bindings if needed

## Benefits

✅ **Decoupled Architecture**: ViewModels don't need to know about each other  
✅ **Automatic Synchronization**: Changes automatically propagate  
✅ **Reduced Boilerplate**: No manual refresh calls in UI layer  
✅ **Single Source of Truth**: Coordinator manages all state changes  
✅ **Easy to Debug**: All events flow through one place  
✅ **Testable**: Events can be mocked and verified  
✅ **Scalable**: Easy to add new ViewModels and dependencies  

## Potential Pitfalls

⚠️ **Event Loops**: Avoid publishing the same event in response to it  
⚠️ **Memory Leaks**: UnifiedViewModel handles unsubscribe automatically  
⚠️ **Ordering**: Events are emitted asynchronously - don't assume order  
⚠️ **Performance**: With many events, consider filtering in `onStateEvent()`  

## Testing

```kotlin
@Test
fun testTransactionCreateTriggersRefresh() = runTest {
    val dashboardVM = DashboardViewModel()
    val collectedEvents = mutableListOf<DashboardUiState>()
    
    val job = launch {
        dashboardVM.state.collect { collectedEvents.add(it) }
    }
    
    // Simulate transaction created event
    UnifiedStateCoordinator.publishEvent(
        StateEvent.TransactionCreated(
            transactionId = "tx_123",
            amount = 100.0,
            channelId = "ch_456"
        )
    )
    
    advanceUntilIdle()
    
    // Verify dashboard refreshed
    assertTrue(collectedEvents.last().phase is DashboardPhase.Loaded)
    
    job.cancel()
}
```

## Advanced: Custom Event Filters

```kotlin
// Extension to filter events by multiple types
inline fun Flow<StateEvent>.filterEventTypes(
    vararg types: KClass<out StateEvent>
): Flow<StateEvent> {
    return filter { event ->
        types.any { it.isInstance(event) }
    }
}

// Usage
UnifiedStateCoordinator.events
    .filterEventTypes(
        StateEvent.TransactionCreated::class,
        StateEvent.ChannelUpdated::class
    )
    .collect { event ->
        refresh()
    }
```

## Performance Considerations

- Events are emitted with `extraBufferCapacity = 100` to handle bursts
- Consider using `distinctUntilChanged()` to reduce redundant refreshes
- Filter events early with `filterEventType()` to avoid processing irrelevant ones
- Use `throttle()` or `debounce()` for high-frequency events

## Next Steps

1. Apply these changes incrementally to each ViewModel
2. Test cross-ViewModel updates thoroughly
3. Monitor for event loops or cascading refreshes
4. Consider adding Hilt injection for the coordinator in production
5. Create integration tests for critical update paths

