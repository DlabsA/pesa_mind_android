package cc.dlabs.pesamind.features.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [CheckoutValidator] — the rules that decide whether a payment attempt is
 * even worth sending.
 *
 * Plain JUnit with no coroutines and no Android runtime, which is the whole reason
 * this logic was extracted from [SubscriptionViewModel]: `ApiClient.api` is an
 * eagerly-built `val` on a plain `object`, so constructing that ViewModel in a test
 * risks a real HTTP call to production (see
 * `core/utils/TransactionViewModelValidationTest.kt` for the incident that
 * established this rule).
 *
 * The backend re-validates all of this. These rules exist so a typo is caught
 * before a network round-trip, not as the security boundary.
 */
class CheckoutValidatorTest {
    // ─── MSISDN normalisation ────────────────────────────────────────────────

    @Test
    fun `accepts the forms Ugandan users actually type`() {
        val expected = "772123456"
        listOf(
            "0772123456",
            "+256772123456",
            "256772123456",
            "772123456",
            "+256 772 123 456",
            "0772-123-456",
            "  0772123456  ",
        ).forEach { input ->
            assertEquals("input: $input", expected, CheckoutValidator.normalizeMsisdn(input))
        }
    }

    @Test
    fun `rejects numbers that cannot hold a mobile money wallet`() {
        // Rejected because: too short, too long, landline prefixes (which cannot
        // hold a mobile money wallet), a Kenyan number, and junk.
        listOf(
            "",
            "07721234",
            "07721234567",
            "0412345678",
            "0312345678",
            "+254772123456",
            "not a number",
        ).forEach { input ->
            assertNull("expected null for: $input", CheckoutValidator.normalizeMsisdn(input))
        }
    }

    // ─── Network inference ───────────────────────────────────────────────────

    @Test
    fun `infers network from the prefix`() {
        assertEquals(CheckoutValidator.NETWORK_MTN, CheckoutValidator.inferNetwork("772123456"))
        assertEquals(CheckoutValidator.NETWORK_MTN, CheckoutValidator.inferNetwork("782123456"))
        assertEquals(CheckoutValidator.NETWORK_AIRTEL, CheckoutValidator.inferNetwork("702123456"))
        assertEquals(CheckoutValidator.NETWORK_AIRTEL, CheckoutValidator.inferNetwork("752123456"))
    }

    @Test
    fun `returns null for an unrecognised prefix rather than guessing`() {
        // A wrong guess would preselect the wrong network for a ported number. Null
        // leaves the user's own choice standing.
        assertNull(CheckoutValidator.inferNetwork("712123456"))
    }

    @Test
    fun `formats a number for display`() {
        assertEquals("+256 772 123 456", CheckoutValidator.formatMsisdnForDisplay("772123456"))
    }

    // ─── Mobile money validation ─────────────────────────────────────────────

    @Test
    fun `accepts a valid mobile money entry`() {
        val result = CheckoutValidator.validateMobileMoney("0772123456", CheckoutValidator.NETWORK_MTN)

        assertEquals(
            CheckoutValidator.MobileMoneyResult.Valid("772123456", CheckoutValidator.NETWORK_MTN),
            result,
        )
    }

    @Test
    fun `rejects an unsupported network`() {
        // Flutterwave supports only MTN and AIRTEL for Uganda; MPESA is Kenya.
        val result = CheckoutValidator.validateMobileMoney("0772123456", "MPESA")

        assertTrue(result is CheckoutValidator.MobileMoneyResult.Invalid)
    }

    @Test
    fun `rejects a bad number with an actionable message`() {
        val result = CheckoutValidator.validateMobileMoney("0412345678", CheckoutValidator.NETWORK_MTN)

        assertTrue(result is CheckoutValidator.MobileMoneyResult.Invalid)
        assertTrue(
            "message should tell the user what a good number looks like",
            (result as CheckoutValidator.MobileMoneyResult.Invalid).message.contains("0772"),
        )
    }
}
