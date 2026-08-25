package cc.dlabs.pesamind.features.subscription

import java.util.Calendar

/**
 * Pure validation and normalisation for the checkout form.
 *
 * Extracted from the ViewModel deliberately: `ApiClient.api` is an eagerly-built
 * `val` on a plain `object`, so any test that constructs a ViewModel able to reach
 * it fires a real HTTP request. Keeping the decisions in pure functions is the same
 * pattern `core/database/OutboxCoalescer.kt` uses, and it means these rules can be
 * tested exhaustively with plain JUnit and no Android runtime.
 *
 * The backend re-validates everything here; this exists so the user gets told about
 * a typo before a network round-trip, not as the security boundary.
 */
object CheckoutValidator {
    /** Supported Uganda mobile money networks. Confirmed against the Flutterwave sandbox. */
    const val NETWORK_MTN = "MTN"
    const val NETWORK_AIRTEL = "AIRTEL"

    /**
     * Two-digit prefixes of the 9-digit national number.
     *
     * Used only to preselect the network toggle as a convenience — the user can
     * always override it, because numbers do get ported between networks and a
     * wrong guess must never block a payment.
     */
    private val MTN_PREFIXES = setOf("77", "78", "76", "39")
    private val AIRTEL_PREFIXES = setOf("70", "75", "74", "20")

    /** Flutterwave's upper bound on card expiry year, relative to the current year. */
    private const val MAX_EXPIRY_YEARS_OUT = 10

    /**
     * Converts the forms Ugandan users actually type into the 9 significant digits
     * the API expects alongside country code 256:
     * ```
     * 0772123456    -> 772123456
     * +256772123456 -> 772123456
     * 256772123456  -> 772123456
     * 772123456     -> 772123456
     * ```
     * Returns null when the input can't be a Ugandan mobile number.
     */
    fun normalizeMsisdn(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val national =
            when {
                digits.length == 12 && digits.startsWith("256") -> digits.drop(3)
                digits.length == 10 && digits.startsWith("0") -> digits.drop(1)
                else -> digits
            }
        // Ugandan mobile numbers are 9 digits starting with 7. Landline prefixes
        // (2x, 3x, 4x) can't hold a mobile money wallet.
        if (national.length != 9 || !national.startsWith("7")) return null
        return national
    }

    /**
     * Best-effort network guess from the number's prefix, or null when unknown.
     * Callers should treat this as a default selection, never a validation rule.
     */
    fun inferNetwork(normalizedMsisdn: String): String? =
        when (normalizedMsisdn.take(2)) {
            in MTN_PREFIXES -> NETWORK_MTN
            in AIRTEL_PREFIXES -> NETWORK_AIRTEL
            else -> null
        }

    /** Formats a normalised number back for display, e.g. `772123456` -> `+256 772 123 456`. */
    fun formatMsisdnForDisplay(normalizedMsisdn: String): String =
        if (normalizedMsisdn.length != 9) {
            normalizedMsisdn
        } else {
            "+256 ${normalizedMsisdn.take(3)} ${normalizedMsisdn.substring(3, 6)} ${normalizedMsisdn.substring(6)}"
        }

    /** Result of validating the mobile money branch of the form. */
    sealed class MobileMoneyResult {
        data class Valid(val msisdn: String, val network: String) : MobileMoneyResult()

        data class Invalid(val message: String) : MobileMoneyResult()
    }

    fun validateMobileMoney(
        rawPhone: String,
        network: String,
    ): MobileMoneyResult {
        val msisdn =
            normalizeMsisdn(rawPhone)
                ?: return MobileMoneyResult.Invalid("Enter a valid Ugandan mobile number, e.g. 0772 123 456")
        if (network != NETWORK_MTN && network != NETWORK_AIRTEL) {
            return MobileMoneyResult.Invalid("Choose MTN or Airtel")
        }
        return MobileMoneyResult.Valid(msisdn, network)
    }

    /**
     * Keeps the expiry field to four digits, MMYY, and keeps the month a real month.
     *
     * There are only twelve months, so a month is settled the moment its first digit
     * rules the alternatives out:
     * ```
     * 6    -> 06     a leading digit above 1 can only be a single-digit month
     * 1, 0 ->        still ambiguous (01..09 vs 10..12); wait for the second digit
     * 00   -> 0      not a month; the second keystroke is refused
     * 13   -> 1      not a month; the second keystroke is refused
     * ```
     * Refusing the keystroke rather than correcting it to 12 matters — silently
     * changing which month a customer's card expires is worse than ignoring a typo.
     *
     * [validateCard] still range-checks the month. This is about not letting someone
     * type `65` and only learn at the Pay button that it was never going to work.
     */
    fun normalizeCardExpiryDigits(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return ""

        val first = digits[0]
        // 2..9 can only be February..September, so pad and move on to the year.
        if (first > '1') return ("0$first" + digits.drop(1)).take(4)
        if (digits.length == 1) return digits

        val monthIsPossible =
            when (first) {
                '0' -> digits[1] != '0' // 00
                else -> digits[1] <= '2' // 13..19
            }
        if (!monthIsPossible) return first.toString()

        return digits.take(4)
    }

    /**
     * Renders the digits held in state as `MM/YY` for display.
     *
     * The field is labelled MM/YY and the keyboard is numeric, so a customer types
     * four digits and never a slash. The slash appears only from the third digit —
     * at exactly two there is no year to separate yet.
     */
    fun formatCardExpiryInput(raw: String): String {
        val digits = raw.filter { it.isDigit() }.take(4)
        return if (digits.length <= 2) digits else "${digits.take(2)}/${digits.drop(2)}"
    }

    /**
     * Splits what the expiry field holds into month and year.
     *
     * Accepts both a bare `0932` (what the field stores) and `09/32` (what a paste
     * might carry). Without the bare form, `0932` was read as month "0932" and
     * rejected with "Check the expiry month" — pointing at the wrong thing, since 09
     * is a perfectly good month.
     */
    fun splitCardExpiry(raw: String): Pair<String, String> {
        if (raw.contains('/')) {
            val parts = raw.split("/")
            return parts.getOrElse(0) { "" } to parts.getOrElse(1) { "" }
        }
        val digits = raw.filter { it.isDigit() }
        // Mirrors formatCardExpiryInput: the year begins at the third digit, so a
        // half-typed "063" reports a bad *year* rather than a bad month.
        return if (digits.length >= 3) digits.take(2) to digits.drop(2) else digits to ""
    }

    /** Result of validating the card branch of the form. */
    sealed class CardResult {
        data class Valid(
            val number: String,
            val expiryMonth: String,
            val expiryYear: String,
            val cvv: String,
        ) : CardResult()

        data class Invalid(val message: String) : CardResult()
    }

    /**
     * @param nowYear four-digit current year, and [nowMonth] 1-12. Passed in rather
     *   than read from the clock so expiry-boundary cases are testable.
     */
    fun validateCard(
        rawNumber: String,
        rawExpiryMonth: String,
        rawExpiryYear: String,
        rawCvv: String,
        nowYear: Int = Calendar.getInstance().get(Calendar.YEAR),
        nowMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    ): CardResult {
        val number = rawNumber.filter { it.isDigit() }
        if (number.length !in 13..19 || !passesLuhn(number)) {
            return CardResult.Invalid("Check your card number")
        }

        val month = rawExpiryMonth.filter { it.isDigit() }.toIntOrNull()
        if (month == null || month !in 1..12) {
            return CardResult.Invalid("Check the expiry month")
        }

        val yearDigits = rawExpiryYear.filter { it.isDigit() }
        val year =
            when (yearDigits.length) {
                2 -> 2000 + yearDigits.toInt()
                4 -> yearDigits.toInt()
                else -> return CardResult.Invalid("Check the expiry year")
            }
        // A card is valid through the last day of its expiry month.
        if (year < nowYear || (year == nowYear && month < nowMonth)) {
            return CardResult.Invalid("That card has expired")
        }
        // Flutterwave rejects a charge with "Card expiry year out of range" past this
        // point (confirmed against the sandbox: current year + 10 is accepted, + 11 is
        // not) — catching it here means a typo'd year is a form error, not a 502 after
        // a round trip through payment-method creation and charge creation. Reported as
        // "expired" rather than "out of range": a year this far out is never a real
        // card, so the same message as an actually-expired card is the honest one.
        if (year > nowYear + MAX_EXPIRY_YEARS_OUT) {
            return CardResult.Invalid("That card has expired")
        }

        val cvv = rawCvv.filter { it.isDigit() }
        if (cvv.length !in 3..4) {
            return CardResult.Invalid("Check the security code")
        }

        return CardResult.Valid(
            number = number,
            expiryMonth = month.toString().padStart(2, '0'),
            expiryYear = (year % 100).toString().padStart(2, '0'),
            cvv = cvv,
        )
    }

    /** Standard Luhn checksum — catches mistyped digits before a network call. */
    private fun passesLuhn(number: String): Boolean {
        var sum = 0
        var double = false
        for (i in number.length - 1 downTo 0) {
            var digit = number[i] - '0'
            if (double) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            double = !double
        }
        return sum % 10 == 0
    }
}
