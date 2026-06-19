package cc.dlabs.pesamind.core.storage

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages authentication state and logout events.
 * Used by TokenRefreshInterceptor to trigger logout when token refresh fails.
 */
object AuthManager {
    private const val TAG = "AuthManager"
    
    private val _logoutEvent = MutableStateFlow(false)
    val logoutEvent: StateFlow<Boolean> = _logoutEvent.asStateFlow()
    
    /**
     * Called when the user should be logged out (e.g., token refresh failed).
     * Clears tokens and emits a logout event that UI can subscribe to.
     */
    suspend fun logout() {
        Log.w(TAG, "🔓 Logout triggered - clearing tokens and notifying UI")
        // Clear all stored authentication data
        TokenManager.clearTokens()
        TokenManager.clearLock()
        
        // Emit logout event so UI can react (navigate to login, show notification, etc.)
        _logoutEvent.emit(true)
        
        // Reset the event after emission so it can be triggered again
        _logoutEvent.emit(false)
    }
}

