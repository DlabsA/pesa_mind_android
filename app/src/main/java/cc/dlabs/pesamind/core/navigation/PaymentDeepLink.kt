package cc.dlabs.pesamind.core.navigation

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a payment-authorisation return from the Activity's intent into Compose.
 *
 * The card 3DS flow leaves the app for a Custom Tab; the backend's
 * `/payments/return` verifies the charge with the provider and then redirects to
 * `pesamind://payment/return?invoice=…`. `MainActivity` is `singleTask`, so that
 * arrives through `onNewIntent` on the *existing* task, outside the Compose tree.
 * This is the seam between the two.
 *
 * The `status` parameter on that URL is deliberately ignored. It travels through
 * the customer's own browser, so anyone could open the link with
 * `status=paid` — only the id is taken, and the app re-reads the invoice over an
 * authenticated call to find out what actually happened.
 */
object PaymentDeepLink {
    private const val TAG = "PaymentDeepLink"
    private const val SCHEME = "pesamind"
    private const val HOST = "payment"

    private val _returnedInvoiceId = MutableStateFlow<String?>(null)
    val returnedInvoiceId: StateFlow<String?> = _returnedInvoiceId.asStateFlow()

    /**
     * Set when the user taps a renewal reminder notification
     * (`pesamind://payment/upgrade`), so the nav graph can take them straight to
     * the upgrade screen instead of dropping them on the dashboard.
     */
    private val _showUpgrade = MutableStateFlow(false)
    val showUpgrade: StateFlow<Boolean> = _showUpgrade.asStateFlow()

    /** Called from `MainActivity.onCreate` and `onNewIntent`. Ignores unrelated intents. */
    fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != SCHEME || data.host != HOST) return

        if (data.path?.trimEnd('/') == "/upgrade") {
            Log.d(TAG, "Renewal reminder tapped")
            _showUpgrade.value = true
            return
        }

        val invoiceId = data.getQueryParameter("invoice")
        if (invoiceId.isNullOrBlank()) {
            Log.w(TAG, "Payment return with no invoice id")
            return
        }
        Log.d(TAG, "Payment authorization returned for invoice $invoiceId")
        _returnedInvoiceId.value = invoiceId
    }

    /** Clears the signal once a screen has acted on it, so it isn't replayed. */
    fun consume() {
        _returnedInvoiceId.value = null
    }

    fun consumeUpgrade() {
        _showUpgrade.value = false
    }
}
