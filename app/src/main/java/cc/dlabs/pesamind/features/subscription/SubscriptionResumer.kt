package cc.dlabs.pesamind.features.subscription

import android.content.Context
import android.util.Log
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedStateCoordinator
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.PaymentManager
import cc.dlabs.pesamind.core.storage.TokenManager

/**
 * Settles a payment the user walked away from.
 *
 * This is what stands in for a payment webhook. A customer can approve a mobile
 * money PIN prompt seconds after backgrounding the app, at which point nothing is
 * polling and the backend has no way to push the result to us. On next launch this
 * asks about the invoice once; the backend re-queries the provider as part of
 * answering, so a single call both discovers and settles the payment.
 *
 * Runs at app start rather than from the checkout screen, because a user who paid
 * and then closed the app may never open checkout again — but they will certainly
 * open the app, and expect to find Premium waiting.
 */
object SubscriptionResumer {
    private const val TAG = "SubscriptionResumer"

    // Cached like every other manager object in this codebase (SyncScheduler,
    // TokenManager, ...) so the reminder can be scheduled from suspend code with no
    // DI wiring. Null until init, in which case reminder scheduling is skipped.
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Launch-time entry point: settles anything in flight, then re-arms the local
     * renewal reminder.
     *
     * The reminder is re-armed on every launch rather than only after a payment,
     * because WorkManager's queue does not survive a reinstall or "clear data" —
     * without this, a user who paid on a previous install would never be reminded.
     */
    suspend fun onAppStart() {
        if (!TokenManager.isLoggedIn()) return
        resumeIfPending()
        // Quiet: no SubscriptionActivated on a routine launch, or every gated
        // ViewModel would refetch on every cold start for no reason.
        refreshEntitlement(publish = false)
    }

    /**
     * Checks any invoice left in flight. No-op when the user is logged out or has
     * nothing pending, so it is cheap to call on every launch.
     */
    suspend fun resumeIfPending() {
        if (!TokenManager.isLoggedIn()) return
        val invoiceId = PaymentManager.getPendingInvoiceId() ?: return

        try {
            val response = ApiClient.api.getInvoice(invoiceId)
            val invoice = response.body()
            if (!response.isSuccessful || invoice == null) {
                // Keep the id: a transient failure must not lose a real payment.
                Log.w(TAG, "Could not resolve pending invoice $invoiceId (${response.code()})")
                return
            }

            if (!PaymentStatusResolver.isTerminal(invoice.status)) {
                // Still genuinely in flight — leave it pending for the next launch.
                return
            }

            PaymentManager.clearPendingInvoiceId()
            if (invoice.status == PaymentStatusResolver.PAID) {
                Log.i(TAG, "Settled a payment completed while the app was closed")
                refreshEntitlement()
            }
        } catch (e: Exception) {
            // Offline at launch is the common case here; the id survives to retry.
            Log.w(TAG, "Pending invoice check failed, will retry next launch", e)
        }
    }

    /**
     * Re-reads the server's view of entitlement, updates the cached tier, and tells
     * the rest of the app.
     *
     * The event matters as much as the cache write: every ViewModel that gates on
     * `isPremium` reads the tier once in `init` and never again, so without it a
     * user would pay and watch nothing unlock until their JWT next refreshed.
     */
    suspend fun refreshEntitlement(publish: Boolean = true) {
        try {
            val response = ApiClient.api.getSubscription()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val cached = AccountManager.getAccount()
                AccountManager.saveAccount(
                    id = cached.id,
                    email = cached.email,
                    username = cached.username,
                    avatarUrl = cached.avatarUrl,
                    balance = cached.balance.toString(),
                    type = body.tier,
                    // A paid subscription is never a trial. Leaving this set would
                    // keep a trial countdown on screen for a paying customer.
                    trialExpiresAt = if (body.isTrial) body.expiresAt else null,
                )
                // The paid period end, kept separately from the trial one so the
                // subscription card and the Settings row can say "runs to 12 Oct"
                // without a network round-trip. Cleared when the account is no
                // longer on a paid period, which is what a lapse back to Free is.
                AccountManager.savePremiumExpiry(
                    if (!body.isTrial && body.isPremium) body.expiresAt else null,
                )
                // Re-arm the local "ends tomorrow" reminder against the new expiry.
                // REPLACE semantics mean a renewal cancels the stale one.
                appContext?.let { ctx ->
                    RenewalReminderWorker.schedule(ctx, body.expiresAt, body.isTrial)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh entitlement", e)
        }
        if (publish) {
            UnifiedStateCoordinator.publishEvent(StateEvent.SubscriptionActivated)
        }
    }
}
