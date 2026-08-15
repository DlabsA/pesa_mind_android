package cc.dlabs.pesamind.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.paymentDataStore by preferencesDataStore("pesamind_payments")

/**
 * Remembers the invoice a payment is currently in flight for.
 *
 * This is what stands in for a payment webhook. If the user starts a mobile money
 * payment and then closes the app before approving the PIN prompt, nothing is left
 * polling when the charge completes, and the backend never learns about it either.
 * Persisting the invoice id lets the next launch ask about it once, which settles
 * the payment and grants Premium.
 *
 * An invoice id is not a secret — it is useless without the user's own JWT — so
 * unlike [TokenManager] this deliberately does not go through [TokenCryptoManager].
 * Adding crypto here would imply a confidentiality requirement that doesn't exist.
 */
object PaymentManager {
    private val PendingInvoiceId = stringPreferencesKey("pending_invoice_id")

    private lateinit var appContext: Context

    private fun isInitialized(): Boolean = ::appContext.isInitialized

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun setPendingInvoiceId(invoiceId: String) {
        if (!isInitialized()) return
        appContext.paymentDataStore.edit { it[PendingInvoiceId] = invoiceId }
    }

    suspend fun getPendingInvoiceId(): String? {
        if (!isInitialized()) return null
        return appContext.paymentDataStore.data
            .map { it[PendingInvoiceId] }
            .first()
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun clearPendingInvoiceId() {
        if (!isInitialized()) return
        appContext.paymentDataStore.edit { it.remove(PendingInvoiceId) }
    }

    /** Called from [AuthManager.logout] so a pending invoice never leaks across accounts. */
    suspend fun clearPayments() {
        if (!isInitialized()) return
        appContext.paymentDataStore.edit { it.clear() }
    }
}
