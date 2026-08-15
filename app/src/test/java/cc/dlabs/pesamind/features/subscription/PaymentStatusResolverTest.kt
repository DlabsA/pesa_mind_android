package cc.dlabs.pesamind.features.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [PaymentStatusResolver] — when the checkout screen keeps waiting, and what
 * it tells the user when it stops.
 *
 * The distinction that matters most here is "stopped waiting" vs "failed". The
 * backend keeps reconciling after we give up polling, and the app settles the
 * invoice on next launch, so a timeout is not a failure. Telling a user their
 * payment failed when the money is merely slow is the worst outcome this screen
 * can produce.
 */
class PaymentStatusResolverTest {
    @Test
    fun `only paid failed and expired are terminal`() {
        assertTrue(PaymentStatusResolver.isTerminal(PaymentStatusResolver.PAID))
        assertTrue(PaymentStatusResolver.isTerminal(PaymentStatusResolver.FAILED))
        assertTrue(PaymentStatusResolver.isTerminal(PaymentStatusResolver.EXPIRED))

        assertFalse(PaymentStatusResolver.isTerminal(PaymentStatusResolver.PENDING))
        assertFalse(PaymentStatusResolver.isTerminal(PaymentStatusResolver.PROCESSING))
        assertFalse(PaymentStatusResolver.isTerminal("something-unknown"))
    }

    @Test
    fun `a terminal status settles immediately regardless of elapsed time`() {
        assertEquals(
            PaymentStatusResolver.Decision.Settled(PaymentStatusResolver.PAID),
            PaymentStatusResolver.decide(PaymentStatusResolver.PAID, elapsedMs = 0),
        )
        assertEquals(
            PaymentStatusResolver.Decision.Settled(PaymentStatusResolver.FAILED),
            PaymentStatusResolver.decide(PaymentStatusResolver.FAILED, elapsedMs = 500_000),
        )
    }

    @Test
    fun `keeps polling while in flight and inside the timeout`() {
        assertEquals(
            PaymentStatusResolver.Decision.KeepPolling,
            PaymentStatusResolver.decide(PaymentStatusResolver.PROCESSING, elapsedMs = 0),
        )
        assertEquals(
            PaymentStatusResolver.Decision.KeepPolling,
            PaymentStatusResolver.decide(
                PaymentStatusResolver.PROCESSING,
                elapsedMs = PaymentStatusResolver.POLL_TIMEOUT_MS - 1,
            ),
        )
    }

    @Test
    fun `stops waiting at the timeout boundary`() {
        assertEquals(
            PaymentStatusResolver.Decision.StoppedWaiting,
            PaymentStatusResolver.decide(
                PaymentStatusResolver.PROCESSING,
                elapsedMs = PaymentStatusResolver.POLL_TIMEOUT_MS,
            ),
        )
    }

    @Test
    fun `a paid status still settles even past the timeout`() {
        // Ordering matters: a result that arrives on the same poll as the timeout
        // must be honoured, not discarded as "stopped waiting".
        assertEquals(
            PaymentStatusResolver.Decision.Settled(PaymentStatusResolver.PAID),
            PaymentStatusResolver.decide(
                PaymentStatusResolver.PAID,
                elapsedMs = PaymentStatusResolver.POLL_TIMEOUT_MS + 10_000,
            ),
        )
    }

    @Test
    fun `a timeout never reads as a failure to the user`() {
        // The copy for a genuine failure reassures about money; the timeout copy
        // must not claim the payment failed at all.
        val failed = PaymentStatusResolver.messageFor(PaymentStatusResolver.FAILED)
        assertTrue(failed.contains("No money was taken"))

        val expired = PaymentStatusResolver.messageFor(PaymentStatusResolver.EXPIRED)
        assertTrue(expired.contains("nothing was charged"))
    }

    @Test
    fun `surfaces the backend failure reason when there is one`() {
        assertEquals(
            "Insufficient balance",
            PaymentStatusResolver.messageFor(PaymentStatusResolver.FAILED, "Insufficient balance"),
        )
    }

    @Test
    fun `falls back to generic copy when the backend gives no reason`() {
        val message = PaymentStatusResolver.messageFor(PaymentStatusResolver.FAILED, "")

        assertTrue(message.isNotBlank())
        assertTrue(message.contains("didn't go through"))
    }
}
