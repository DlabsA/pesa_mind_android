package cc.dlabs.pesamind.core.coordinator

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * Base ViewModel that integrates with the UnifiedStateCoordinator.
 *
 * All ViewModels should extend this class to:
 * - Automatically listen to state events from other ViewModels
 * - Publish state events when they make changes
 * - Handle reactive updates across the application
 * - Run network calls via [launchWithState], which centralizes the
 *   loading-toggle / success-mapping / HTTP-and-exception-error-mapping
 *   boilerplate every screen VM was otherwise duplicating by hand.
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

    /**
     * Runs [call] in [viewModelScope] against `this` state flow, toggling a loading flag
     * around it and mapping HTTP failure / thrown exceptions into an error message. On a
     * successful response with a non-null body, [onSuccess] folds the body into the current
     * state. [setLoading]/[setError] are explicit because VMs commonly juggle more than one
     * in-flight flag on the same state (`isLoading` for the initial load, `isSaving`,
     * `isDeleting`, ... for other actions) — the call site picks which one this call drives.
     *
     * Mutates via direct `.value =` assignment (matching every VM's existing convention),
     * not `update { }`, so [onSuccess] can safely contain a one-shot side effect (e.g.
     * writing through to a storage manager) without it re-running under CAS retry.
     */
    protected fun <S, T> MutableStateFlow<S>.launchWithState(
        call: suspend () -> Response<T>,
        setLoading: (S, Boolean) -> S,
        setError: (S, String?) -> S,
        onSuccess: suspend (S, T?) -> S,
        mapHttpError: (Response<T>) -> String = { "Request failed (${it.code()})" },
    ) {
        viewModelScope.launch {
            value = setLoading(setError(value, null), true)
            try {
                val response = call()
                value =
                    if (response.isSuccessful) {
                        setLoading(onSuccess(value, response.body()), false)
                    } else {
                        setError(setLoading(value, false), mapHttpError(response))
                    }
            } catch (e: Exception) {
                value = setError(setLoading(value, false), "Cannot reach server: ${e.message ?: "Unknown error"}")
            }
        }
    }
}
