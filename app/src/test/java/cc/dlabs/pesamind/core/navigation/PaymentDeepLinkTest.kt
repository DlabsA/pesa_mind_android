package cc.dlabs.pesamind.core.navigation

import android.content.Intent
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers [PaymentDeepLink] — the seam the card 3DS flow returns through.
 *
 * This branch cannot be reached from the Flutterwave developer sandbox: it settles
 * card charges immediately and never issues a `redirect_url`, so `AWAITING_REDIRECT`
 * and everything downstream of it is unreachable by a sandbox checkout. These tests
 * are how that path gets exercised at all.
 *
 * Robolectric because [Intent]/[Uri] parsing is the thing under test — `Uri.parse`
 * is a stub that returns null on plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
class PaymentDeepLinkTest {
    private fun intentFor(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    @After
    fun tearDown() {
        // Object singleton — state leaks between tests otherwise.
        PaymentDeepLink.consume()
        PaymentDeepLink.consumeUpgrade()
    }

    @Test
    fun `picks up the invoice id from a payment return`() {
        PaymentDeepLink.handle(intentFor("pesamind://payment/return?invoice=inv-123&status=paid"))

        assertEquals("inv-123", PaymentDeepLink.returnedInvoiceId.value)
    }

    @Test
    fun `ignores the status on the return url entirely`() {
        // The URL travels through the customer's own browser, so anyone can craft
        // one that says status=paid. Only the id is taken; the app re-reads the
        // invoice over an authenticated call to find out what actually happened.
        // If this ever starts trusting `status`, Premium becomes free.
        PaymentDeepLink.handle(intentFor("pesamind://payment/return?invoice=forged&status=paid"))

        assertEquals("forged", PaymentDeepLink.returnedInvoiceId.value)
        assertFalse(
            "a return deep link must never by itself grant anything",
            PaymentDeepLink.showUpgrade.value,
        )
    }

    @Test
    fun `ignores intents that are not ours`() {
        listOf(
            "https://api.dlabs.cc/api/v1/payments/return?invoice=inv-123",
            "pesamind://transaction/return?invoice=inv-123",
            "othersapp://payment/return?invoice=inv-123",
        ).forEach { url ->
            PaymentDeepLink.handle(intentFor(url))
            assertNull("should have ignored: $url", PaymentDeepLink.returnedInvoiceId.value)
        }
    }

    @Test
    fun `ignores a return with no invoice id`() {
        PaymentDeepLink.handle(intentFor("pesamind://payment/return?status=paid"))
        assertNull(PaymentDeepLink.returnedInvoiceId.value)

        PaymentDeepLink.handle(intentFor("pesamind://payment/return?invoice="))
        assertNull(PaymentDeepLink.returnedInvoiceId.value)
    }

    @Test
    fun `survives an intent with no data at all`() {
        // onCreate hands over whatever launched the Activity — usually a plain
        // launcher intent with no data.
        PaymentDeepLink.handle(Intent(Intent.ACTION_MAIN))
        PaymentDeepLink.handle(null)

        assertNull(PaymentDeepLink.returnedInvoiceId.value)
    }

    @Test
    fun `routes a renewal reminder tap to the upgrade screen`() {
        PaymentDeepLink.handle(intentFor("pesamind://payment/upgrade"))

        assertTrue(PaymentDeepLink.showUpgrade.value)
        assertNull("an upgrade tap is not an invoice return", PaymentDeepLink.returnedInvoiceId.value)
    }

    @Test
    fun `tolerates a trailing slash on the upgrade path`() {
        PaymentDeepLink.handle(intentFor("pesamind://payment/upgrade/"))

        assertTrue(PaymentDeepLink.showUpgrade.value)
    }

    @Test
    fun `consume clears the signal so it is not replayed`() {
        PaymentDeepLink.handle(intentFor("pesamind://payment/return?invoice=inv-123"))
        assertEquals("inv-123", PaymentDeepLink.returnedInvoiceId.value)

        PaymentDeepLink.consume()

        // Without this, rotating the device would re-trigger the settle path.
        assertNull(PaymentDeepLink.returnedInvoiceId.value)
    }
}
