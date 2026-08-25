package cc.dlabs.pesamind.features.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ─── Card validation ─────────────────────────────────────────────────────

    @Test
    fun `accepts a Luhn-valid card and normalises the expiry`() {
        val result =
            CheckoutValidator.validateCard(
                rawNumber = "5531 8866 5214 2950",
                rawExpiryMonth = "9",
                rawExpiryYear = "2032",
                rawCvv = "564",
                nowYear = 2026,
                nowMonth = 8,
            )

        assertEquals(
            CheckoutValidator.CardResult.Valid("5531886652142950", "09", "32", "564"),
            result,
        )
    }

    @Test
    fun `rejects a card that fails the Luhn check`() {
        // One digit changed from a valid number — the classic mistyped-digit case.
        val result =
            CheckoutValidator.validateCard("5531886652142951", "09", "32", "564", 2026, 8)

        assertTrue(result is CheckoutValidator.CardResult.Invalid)
    }

    @Test
    fun `a card is valid through the last day of its expiry month`() {
        // Same month as expiry: still good.
        val sameMonth = CheckoutValidator.validateCard("5531886652142950", "08", "26", "564", 2026, 8)
        assertTrue(sameMonth is CheckoutValidator.CardResult.Valid)

        // One month past: expired.
        val lastMonth = CheckoutValidator.validateCard("5531886652142950", "07", "26", "564", 2026, 8)
        assertTrue(lastMonth is CheckoutValidator.CardResult.Invalid)
    }

    @Test
    fun `rejects a card expiry year past Flutterwave's accepted range`() {
        // Confirmed against the sandbox: current year + 10 is accepted, + 11 is
        // rejected with "Card expiry year out of range" — after the payment method
        // and charge have already been created upstream.
        val atBound = CheckoutValidator.validateCard("5531886652142950", "09", "36", "564", 2026, 8)
        assertTrue(atBound is CheckoutValidator.CardResult.Valid)

        val pastBound = CheckoutValidator.validateCard("5531886652142950", "09", "37", "564", 2026, 8)
        assertTrue(pastBound is CheckoutValidator.CardResult.Invalid)
        assertEquals(
            "That card has expired",
            (pastBound as CheckoutValidator.CardResult.Invalid).message,
        )
    }

    @Test
    fun `rejects an out-of-range month and a malformed year`() {
        assertTrue(
            CheckoutValidator.validateCard("5531886652142950", "13", "32", "564", 2026, 8)
                is CheckoutValidator.CardResult.Invalid,
        )
        assertTrue(
            CheckoutValidator.validateCard("5531886652142950", "09", "3", "564", 2026, 8)
                is CheckoutValidator.CardResult.Invalid,
        )
    }

    @Test
    fun `rejects a CVV of the wrong length`() {
        assertTrue(
            CheckoutValidator.validateCard("5531886652142950", "09", "32", "56", 2026, 8)
                is CheckoutValidator.CardResult.Invalid,
        )
        // Amex-length CVVs are fine.
        assertTrue(
            CheckoutValidator.validateCard("5531886652142950", "09", "32", "5641", 2026, 8)
                is CheckoutValidator.CardResult.Valid,
        )
    }

    // ─── Expiry field formatting ─────────────────────────────────────────────

    @Test
    fun `draws the digits the customer types as MM slash YY`() {
        // The customer taps four digits on a numeric keypad and never a slash. This
        // is what the field's VisualTransformation renders at each keystroke — the
        // state itself stays bare digits, which is what keeps the caret still.
        assertEquals("", CheckoutValidator.formatCardExpiryInput(""))
        assertEquals("0", CheckoutValidator.formatCardExpiryInput("0"))
        assertEquals("09", CheckoutValidator.formatCardExpiryInput("09"))
        assertEquals("09/3", CheckoutValidator.formatCardExpiryInput("093"))
        assertEquals("09/32", CheckoutValidator.formatCardExpiryInput("0932"))
    }

    @Test
    fun `an impossible month cannot be typed at all`() {
        // There are twelve months. 65 used to be enterable and only rejected at the
        // Pay button — the customer typed it, moved on, and found out at the end.
        assertEquals("06", CheckoutValidator.normalizeCardExpiryDigits("6"))
        assertEquals("06/5", CheckoutValidator.formatCardExpiryInput(CheckoutValidator.normalizeCardExpiryDigits("65")))

        // Every unambiguous leading digit settles the month immediately.
        listOf('2', '3', '4', '5', '6', '7', '8', '9').forEach { d ->
            assertEquals("0$d", CheckoutValidator.normalizeCardExpiryDigits(d.toString()))
        }
    }

    @Test
    fun `refuses the keystroke that would make a bad month, rather than correcting it`() {
        // 13..19 and 00 are not months. Dropping the offending digit leaves the
        // customer where they were; silently rewriting 13 to 12 would change which
        // month their card expires without telling them.
        assertEquals("1", CheckoutValidator.normalizeCardExpiryDigits("13"))
        assertEquals("1", CheckoutValidator.normalizeCardExpiryDigits("19"))
        assertEquals("0", CheckoutValidator.normalizeCardExpiryDigits("00"))

        // The genuinely ambiguous leading digits still wait for a second.
        assertEquals("0", CheckoutValidator.normalizeCardExpiryDigits("0"))
        assertEquals("1", CheckoutValidator.normalizeCardExpiryDigits("1"))
        // ...and both two-digit months either side of the boundary are accepted.
        assertEquals("01", CheckoutValidator.normalizeCardExpiryDigits("01"))
        assertEquals("09", CheckoutValidator.normalizeCardExpiryDigits("09"))
        assertEquals("10", CheckoutValidator.normalizeCardExpiryDigits("10"))
        assertEquals("12", CheckoutValidator.normalizeCardExpiryDigits("12"))
    }

    @Test
    fun `normalising is stable when fed its own output`() {
        // The field re-runs this on every keystroke over the value it already holds,
        // so a value that changes on a second pass would fight the customer's typing.
        listOf("", "0", "1", "06", "12", "0932", "1226").forEach { value ->
            assertEquals(
                "unstable for $value",
                value,
                CheckoutValidator.normalizeCardExpiryDigits(value),
            )
        }
    }

    @Test
    fun `keeps a four digit expiry to four digits`() {
        assertEquals("0932", CheckoutValidator.normalizeCardExpiryDigits("09329999"))
        assertEquals("0653", CheckoutValidator.normalizeCardExpiryDigits("6532"))
        assertEquals("", CheckoutValidator.normalizeCardExpiryDigits("abc"))
    }

    @Test
    fun `the slash appears only from the third digit`() {
        // The offset mapping in PaymentSheet shifts by one exactly when a slash is
        // present, so "two digits means no slash" is load-bearing, not cosmetic.
        assertFalse(CheckoutValidator.formatCardExpiryInput("09").contains("/"))
        assertTrue(CheckoutValidator.formatCardExpiryInput("093").contains("/"))
    }

    @Test
    fun `ignores non-digits and stops at four digits`() {
        // Pasted from a password manager, or typed on a keyboard that does have "/".
        assertEquals("09/32", CheckoutValidator.formatCardExpiryInput("09/32"))
        assertEquals("09/32", CheckoutValidator.formatCardExpiryInput("09 / 32"))
        assertEquals("09/32", CheckoutValidator.formatCardExpiryInput("0932999"))
        assertEquals("", CheckoutValidator.formatCardExpiryInput("abc"))
    }

    @Test
    fun `splits both slashed and bare expiry values`() {
        assertEquals("09" to "32", CheckoutValidator.splitCardExpiry("09/32"))
        // The regression: a bare four-digit value must not be read as the month.
        assertEquals("09" to "32", CheckoutValidator.splitCardExpiry("0932"))
        assertEquals("09" to "", CheckoutValidator.splitCardExpiry("09"))
        assertEquals("" to "", CheckoutValidator.splitCardExpiry(""))
        // Half-typed: the year is what's missing, so that's what must be reported.
        assertEquals("06" to "3", CheckoutValidator.splitCardExpiry("063"))
        assertTrue(
            CheckoutValidator.validateCard("5531886652142950", "06", "3", "564", 2026, 8)
                is CheckoutValidator.CardResult.Invalid,
        )
    }

    @Test
    fun `a four-digit expiry now validates the same as a slashed one`() {
        // The end-to-end shape of the bug: typing 0932 used to yield "Check the
        // expiry month" even though 09 is valid.
        val (month, year) = CheckoutValidator.splitCardExpiry("0932")
        val result = CheckoutValidator.validateCard("5531886652142950", month, year, "564", 2026, 8)

        assertEquals(
            CheckoutValidator.CardResult.Valid("5531886652142950", "09", "32", "564"),
            result,
        )
    }

    // ─── Flutterwave sandbox card sweep ──────────────────────────────────────

    /**
     * Every card from developer.flutterwave.com/v3.0/docs/testing must survive this
     * validator, or the sandbox becomes untestable through the real UI: a client-side
     * rejection means the request never reaches the backend, and nobody can tell that
     * apart from a broken integration.
     *
     * The decline cards are here for the same reason — they must be *accepted* locally
     * so the provider can decline them. Testing how the app handles "insufficient
     * funds" requires the request to actually be sent.
     */
    @Test
    fun `accepts every Flutterwave sandbox test card`() {
        // Card number to expiry MM/YY and CVV, as published. Labels name the
        // scenario each card triggers at the provider.
        val sandboxCards =
            listOf(
                // Successful-payment cards.
                SandboxCard("mastercard pin-auth", "5531886652142950", "09", "32", "564"),
                SandboxCard("mastercard 3ds", "5438898014560229", "10", "31", "564"),
                SandboxCard("visa 3ds", "4187427415564246", "09", "32", "828"),
                SandboxCard("verve pin-auth", "5061460410120223210", "10", "31", "780"),
                SandboxCard("verve no-auth", "5061460166976054667", "10", "29", "564"),
                SandboxCard("visa avs", "4556052704172643", "09", "32", "899"),
                SandboxCard("mastercard pre-auth", "5377283645077450", "09", "31", "789"),
                // Decline cards — accepted here, declined by the provider.
                SandboxCard("do not honour", "5143010522339965", "08", "32", "276"),
                SandboxCard("fraudulent", "5590131743294314", "11", "32", "887"),
                SandboxCard("insufficient funds", "5258585922666506", "09", "31", "883"),
                SandboxCard("incorrect pin", "5399834697894723", "09", "31", "883"),
            )

        sandboxCards.forEach { card ->
            val result =
                CheckoutValidator.validateCard(
                    rawNumber = card.number,
                    rawExpiryMonth = card.expiryMonth,
                    rawExpiryYear = card.expiryYear,
                    rawCvv = card.cvv,
                    nowYear = 2026,
                    nowMonth = 8,
                )

            assertTrue(
                "sandbox card ${card.label} (${card.number}) was rejected: " +
                    (result as? CheckoutValidator.CardResult.Invalid)?.message,
                result is CheckoutValidator.CardResult.Valid,
            )
        }
    }

    private data class SandboxCard(
        val label: String,
        val number: String,
        val expiryMonth: String,
        val expiryYear: String,
        val cvv: String,
    )

    @Test
    fun `19-digit Verve cards sit exactly on the upper length bound`() {
        // The published Verve test cards are 19 digits — the maximum a PAN can be, and
        // the exact value of this validator's upper bound. Tightening the range to 16
        // "because cards are 16 digits" would silently break Verve, so pin it.
        val verve = "5061460410120223210"
        assertEquals(19, verve.length)
        assertTrue(
            CheckoutValidator.validateCard(verve, "10", "31", "780", 2026, 8)
                is CheckoutValidator.CardResult.Valid,
        )
    }

    @Test
    fun `the published Afrigo test card is genuinely expired`() {
        // Flutterwave still lists Afrigo 5640003941605320 with expiry 05/26, which is in
        // the past. The rejection is correct, not a validator bug — documented so the
        // next person to hit it in the sandbox doesn't go looking for one.
        val result = CheckoutValidator.validateCard("5640003941605320", "05", "26", "044", 2026, 8)

        assertTrue(result is CheckoutValidator.CardResult.Invalid)
        assertEquals(
            "That card has expired",
            (result as CheckoutValidator.CardResult.Invalid).message,
        )
    }
}
