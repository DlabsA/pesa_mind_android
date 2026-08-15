package cc.dlabs.pesamind.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [redactSensitiveValues], the scrubber applied to OkHttp body logging.
 *
 * Why this matters: before this existed, `ApiClient` logged every request body at
 * `Level.BODY` on *release* builds, so JWTs were written to logcat on real user
 * devices. The subscription checkout screen makes it worse — it posts a raw card
 * number and CVV, which must never be written anywhere.
 *
 * Deliberately not covered: `RedactingLogger` itself, since it only wraps this
 * function in an `android.util.Log` call that isn't mockable in a JVM test.
 */
class RedactingLoggerTest {
    @Test
    fun `redacts card number and cvv from a checkout body`() {
        val body =
            """{"plan_code":"premium_monthly","method":"card","card_number":"5531886652142950",""" +
                """"card_cvv":"564","card_expiry_month":"09","card_expiry_year":"32"}"""

        val redacted = redactSensitiveValues(body)

        assertFalse("PAN must not survive redaction", redacted.contains("5531886652142950"))
        assertFalse("CVV must not survive redaction", redacted.contains("564"))
        assertTrue(redacted.contains("\"card_number\":\"***\""))
        assertTrue(redacted.contains("\"card_cvv\":\"***\""))
        // Non-sensitive fields are left readable, or the logging is useless.
        assertTrue(redacted.contains("\"plan_code\":\"premium_monthly\""))
    }

    @Test
    fun `redacts auth tokens in both snake_case and camelCase`() {
        val body = """{"access_token":"eyJhbGciOi.J9.abc","refreshToken":"rt_secret_value"}"""

        val redacted = redactSensitiveValues(body)

        assertFalse(redacted.contains("eyJhbGciOi.J9.abc"))
        assertFalse(redacted.contains("rt_secret_value"))
    }

    @Test
    fun `redacts app lock pin and pattern`() {
        val redacted = redactSensitiveValues("""{"pin":"1234","pattern":"0-1-2-5-8"}""")

        assertFalse(redacted.contains("1234"))
        assertFalse(redacted.contains("0-1-2-5-8"))
    }

    @Test
    fun `redacts a bare PAN-shaped digit run even under an unknown key`() {
        // Backstop: card data arriving under a key this file doesn't know about
        // still must not reach logcat.
        val redacted = redactSensitiveValues("""{"some_new_field":"4187427415564246"}""")

        assertFalse(redacted.contains("4187427415564246"))
    }

    @Test
    fun `leaves ordinary transaction bodies untouched`() {
        // Amounts, UUIDs and dates are all shorter or hyphenated, so the PAN
        // backstop must not mangle them.
        val body =
            """{"id":"3f2504e0-4f89-11d3-9a0c-0305e82c3301","amount":4000.0,""" +
                """"channel_id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","created_at":"2026-08-11"}"""

        assertEquals(body, redactSensitiveValues(body))
    }

    @Test
    fun `handles numeric secret values`() {
        // A PIN sent unquoted is still a PIN.
        val redacted = redactSensitiveValues("""{"pin":1234,"amount":4000}""")

        assertTrue(redacted.contains("\"pin\":\"***\""))
        assertTrue("unrelated numbers survive", redacted.contains("\"amount\":4000"))
    }
}
