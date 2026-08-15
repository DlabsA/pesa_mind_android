package cc.dlabs.pesamind.core.network

import android.util.Log
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Scrubs secrets out of OkHttp's body logging before they reach logcat.
 *
 * The logging interceptor is already debug-only (see [ApiClient]), but debug
 * builds run on real devices with real accounts, and logcat is readable by
 * anyone with adb. Two classes of value must never appear there:
 *
 *  - **Card data.** Subscription checkout posts a raw PAN, CVV and expiry to the
 *    backend, which encrypts them for the payment provider. Logging a PAN would
 *    put this app squarely in scope for a cardholder-data breach.
 *  - **Credentials.** JWTs, refresh tokens, passwords, and the app-lock PIN and
 *    pattern all cross this client.
 *
 * [redactSensitiveValues] is a pure function so the redaction rules can be tested
 * directly, without an HTTP stack — the same reason the payment decision logic
 * lives in [cc.dlabs.pesamind.features.subscription.PaymentStatusResolver].
 */
class RedactingLogger : HttpLoggingInterceptor.Logger {
    override fun log(message: String) {
        Log.d(TAG, redactSensitiveValues(message))
    }

    companion object {
        private const val TAG = "ApiClient"
    }
}

private const val MASK = "\"***\""

/**
 * JSON keys whose values are secrets. Matched case-insensitively against both
 * snake_case and camelCase spellings, since the API uses snake_case on the wire
 * while Kotlin models use camelCase.
 */
private val SENSITIVE_KEYS =
    listOf(
        "card_number", "cardNumber",
        "card_cvv", "cardCvv", "cvv",
        "card_expiry_month", "cardExpiryMonth",
        "card_expiry_year", "cardExpiryYear",
        "password", "current_password", "currentPassword", "new_password", "newPassword",
        "token", "access_token", "accessToken", "refresh_token", "refreshToken",
        "jwt", "id_token", "idToken", "authorization",
        "pin", "pattern",
    )

/**
 * Matches `"key": <value>` for any sensitive key, where the value is either a
 * quoted string or a bare literal (number/boolean/null).
 */
private val SENSITIVE_FIELD_REGEX =
    Regex(
        "\"(${SENSITIVE_KEYS.joinToString("|")})\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|[^,}\\s]+)",
        RegexOption.IGNORE_CASE,
    )

/**
 * Any bare 13–19 digit run — the length range of a payment card number.
 *
 * A backstop for card data arriving under a key this file doesn't know about.
 * Nothing else in this app's traffic is a bare digit run that long: amounts are
 * far shorter and IDs are hyphenated UUIDs.
 */
private val PAN_SHAPED_REGEX = Regex("(?<!\\d)\\d{13,19}(?!\\d)")

/** Replaces the value of every sensitive JSON field, and any PAN-shaped digit run. */
fun redactSensitiveValues(message: String): String =
    message
        .replace(SENSITIVE_FIELD_REGEX) { match -> "\"${match.groupValues[1]}\":$MASK" }
        .replace(PAN_SHAPED_REGEX, "***")
