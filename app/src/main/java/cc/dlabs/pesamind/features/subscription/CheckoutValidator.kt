package cc.dlabs.pesamind.features.subscription

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
 *
 * Card validation lived here until Google Play Billing replaced the card checkout
 * option — Play Billing Library owns its own purchase UI, so there is no card form
 * left on this client to validate.
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
}
