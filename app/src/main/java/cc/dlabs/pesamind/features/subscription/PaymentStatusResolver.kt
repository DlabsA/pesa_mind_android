package cc.dlabs.pesamind.features.subscription

/**
 * Decides what the checkout screen does next after each invoice poll.
 *
 * Pure, for the same reason as [CheckoutValidator]: a ViewModel that can reach
 * `ApiClient.api` cannot be constructed in a JVM test without firing a real HTTP
 * request, so the logic that decides whether a user's money arrived lives here
 * where it can be tested directly.
 */
object PaymentStatusResolver {
    /** Invoice statuses, mirroring the backend's state machine. */
    const val PENDING = "pending"
    const val PROCESSING = "processing"
    const val PAID = "paid"
    const val FAILED = "failed"
    const val EXPIRED = "expired"

    /** Poll every 3s — fast enough to feel immediate, slow enough not to hammer the API. */
    const val POLL_INTERVAL_MS = 3_000L

    /**
     * Stop polling after 90s. The payment is not cancelled at this point — the
     * backend keeps reconciling, and the app settles it on next launch. This is
     * only about when to stop holding the user on a spinner.
     */
    const val POLL_TIMEOUT_MS = 90_000L

    sealed class Decision {
        /** A final answer arrived. */
        data class Settled(val status: String) : Decision()

        /** Still in flight; poll again after [POLL_INTERVAL_MS]. */
        data object KeepPolling : Decision()

        /**
         * We stopped waiting, but the payment may still complete. The UI must say
         * so rather than claiming failure — telling a user their payment failed
         * when it is merely slow is the worst thing this screen can do.
         */
        data object StoppedWaiting : Decision()
    }

    fun isTerminal(status: String): Boolean = status == PAID || status == FAILED || status == EXPIRED

    fun decide(
        status: String,
        elapsedMs: Long,
    ): Decision =
        when {
            isTerminal(status) -> Decision.Settled(status)
            elapsedMs >= POLL_TIMEOUT_MS -> Decision.StoppedWaiting
            else -> Decision.KeepPolling
        }

    /**
     * Customer-facing copy for a terminal status.
     *
     * @param failureReason the backend's reason, surfaced when it is actionable.
     */
    fun messageFor(
        status: String,
        failureReason: String = "",
    ): String =
        when (status) {
            PAID -> "Payment received — welcome to Premium!"
            FAILED -> failureReason.ifBlank { "That payment didn't go through. No money was taken." }
            EXPIRED -> "The payment request timed out. You can try again — nothing was charged."
            else -> "Waiting for your confirmation…"
        }
}
