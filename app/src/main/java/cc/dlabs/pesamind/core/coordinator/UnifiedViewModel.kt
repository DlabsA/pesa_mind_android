package cc.dlabs.pesamind.core.coordinator

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Base ViewModel that integrates with the UnifiedStateCoordinator.
 * 
 * All ViewModels should extend this class to:
 * - Automatically listen to state events from other ViewModels
 * - Publish state events when they make changes
 * - Handle reactive updates across the application
 * 
 * Override [onStateEvent] to react to state changes from other ViewModels.
 */
abstract class UnifiedViewModel : ViewModel() {
    
    private val tag = this::class.simpleName ?: "UnifiedViewModel"
    
    init {
        // Subscribe to all state events from the coordinator
        viewModelScope.launch {
            UnifiedStateCoordinator.events.collect { event ->
                try {
                    onStateEvent(event)
                } catch (e: Exception) {
                    Log.e(tag, "❌ Error handling event in $tag", e)
                }
            }
        }
    }
    
    /**
     * Called when any state event is published by any ViewModel in the app.
     * 
     * Override this method to react to specific events:
     * 
     * ```kotlin
     * override fun onStateEvent(event: StateEvent) {
     *     when (event) {
     *         is StateEvent.TransactionCreated -> handleTransactionCreated(event)
     *         is StateEvent.ChannelUpdated -> refreshChannels()
     *         else -> {}
     *     }
     * }
     * ```
     * 
     * @param event The state event that was published
     */
    protected open fun onStateEvent(event: StateEvent) {
        // Default implementation: do nothing
        // Subclasses override this to handle specific events
    }
    
    /**
     * Publish a state event to all subscribed ViewModels.
     * 
     * @param event The event to publish
     */
    protected fun publishEvent(event: StateEvent) {
        viewModelScope.launch {
            try {
                UnifiedStateCoordinator.publishEvent(event)
            } catch (e: Exception) {
                Log.e(tag, "❌ $tag failed to publish event: ${event::class.simpleName}", e)
            }
        }
    }
}


