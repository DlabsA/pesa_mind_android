package cc.dlabs.pesamind.core.coordinator

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Central state coordinator that manages event propagation across all ViewModels.
 * 
 * This singleton is responsible for:
 * - Broadcasting state events when data changes
 * - Allowing ViewModels to subscribe to relevant events
 * - Coordinating updates across dependent ViewModels
 * 
 * Usage:
 * ```
 * // Publishing an event
 * UnifiedStateCoordinator.publishEvent(StateEvent.TransactionCreated(...))
 * 
 * // Subscribing to events
 * UnifiedStateCoordinator.events.collect { event ->
 *     when (event) {
 *         is StateEvent.TransactionCreated -> handleTransactionCreated(event)
 *         else -> {}
 *     }
 * }
 * ```
 */
object UnifiedStateCoordinator {
    
    // Using MutableSharedFlow for multi-consumer broadcast
    private val _events = MutableSharedFlow<StateEvent>(
        replay = 0,  // Don't replay events to new subscribers
        extraBufferCapacity = 100,  // Buffer for bursty events
    )
    
    /**
     * Public read-only flow of state events.
     * ViewModels and other components can collect from this to react to changes.
     */
    val events: Flow<StateEvent> = _events.asSharedFlow()
    
    /**
     * Publish a state event to all subscribers.
     * 
     * This should be called whenever a ViewModel makes a significant data change.
     * 
     * @param event The state event to broadcast
     */
    suspend fun publishEvent(event: StateEvent) {
        _events.emit(event)
    }
    
    /**
     * Non-suspending variant for publishing events.
     * Use this when you're not in a coroutine context.
     * 
     * @param event The state event to broadcast
     */
    fun publishEventNonSuspending(event: StateEvent) {
        _events.tryEmit(event)
    }
}

