package cc.dlabs.pesamind.core.storage

import android.content.Context
import cc.dlabs.pesamind.core.database.PesaMindDatabase
import cc.dlabs.pesamind.core.di.DatabaseEntryPoint
import dagger.hilt.EntryPoints
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

    // Nullable, not lateinit — mirrors ChannelRepository/TransactionRepository's identical
    // fields: a test that never calls [init] must not crash calling logout(), just skip the
    // (then-nonexistent) local wipe.
    private var database: PesaMindDatabase? = null

    fun init(context: Context) {
        database = EntryPoints.get(context.applicationContext, DatabaseEntryPoint::class.java).database()
    }

    /**
     * Called when the user should be logged out (e.g., token refresh failed, or the user tapped
     * Log Out). Clears tokens and every locally cached row — a device reused by a different
     * account must never see, or silently push under the new account's session, the previous
     * account's still-pending local data (see PesaMindDatabase.clearAllLocalData) — then emits a
     * logout event that UI can subscribe to.
     */
    suspend fun logout() {
        // Clear all stored authentication data
        TokenManager.clearTokens()
        TokenManager.clearLock()
        TokenManager.setChannelsOnboarded(false)
        AccountManager.clearAccount()
        // A pending invoice belongs to the account that started it — the next
        // account signing in on this device must not resume someone else's payment.
        PaymentManager.clearPayments()
        database?.clearAllLocalData()

        // Emit logout event so UI can react (navigate to login, show notification, etc.)
        _logoutEvent.emit(true)

        // Reset the event after emission so it can be triggered again
        _logoutEvent.emit(false)
    }
}
