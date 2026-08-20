package cc.dlabs.pesamind.core.navigation

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Set when the user taps the "SIM cards changed" alert notification
 * (`pesamind://account/simslots`), so the nav graph takes them straight to Account Settings
 * instead of dropping them on the dashboard. Mirrors [PaymentDeepLink]'s `showUpgrade` signal.
 */
object SimSlotDeepLink {
    private const val SCHEME = "pesamind"
    private const val HOST = "account"

    private val _showSimSlots = MutableStateFlow(false)
    val showSimSlots: StateFlow<Boolean> = _showSimSlots.asStateFlow()

    /** Called from `MainActivity.onCreate` and `onNewIntent`. Ignores unrelated intents. */
    fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != SCHEME || data.host != HOST) return
        if (data.path?.trimEnd('/') == "/simslots") {
            _showSimSlots.value = true
        }
    }

    /** Clears the signal once the nav graph has acted on it, so it isn't replayed. */
    fun consume() {
        _showSimSlots.value = false
    }
}
