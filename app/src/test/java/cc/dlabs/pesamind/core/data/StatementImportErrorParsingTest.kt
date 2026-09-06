package cc.dlabs.pesamind.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers every row of the statement-import error contract in
 * `internal/interfaces/http/handlers/statementimport_handler.go:writeStatementImportError`,
 * plus the degenerate bodies a proxy or a crash can put in front of it.
 *
 * Pure function under test — no ViewModel, no `runTest`, so nothing here can reach the network.
 */
class StatementImportErrorParsingTest {
    @Test
    fun `empty file maps to its own message`() {
        val (message, code) =
            parseStatementImportError(400, """{"error":"the uploaded file is empty","code":"EMPTY_FILE"}""")
        assertEquals("That file is empty.", message)
        assertEquals("EMPTY_FILE", code)
    }

    @Test
    fun `too large is driven by the http code, since the body carries no code field`() {
        val (message, code) = parseStatementImportError(413, """{"error":"statement file is too large"}""")
        assertEquals("That file is too large (max 10 MB).", message)
        assertNull(code)
    }

    @Test
    fun `not found and forbidden speak in the app's own vocabulary`() {
        assertEquals(
            "Account not found.",
            parseStatementImportError(404, """{"error":"channel not found","code":"NOT_FOUND"}""").first,
        )
        assertEquals(
            "You don't have access to this account.",
            parseStatementImportError(403, """{"error":"nope","code":"FORBIDDEN"}""").first,
        )
    }

    @Test
    fun `unsupported channel is the odd shape - the code lives in error, provider in channel_desc`() {
        val (message, code) =
            parseStatementImportError(422, """{"error":"statement_import_unsupported","channel_desc":"DFCU Bank"}""")
        assertEquals("Statement import isn't supported for DFCU Bank yet.", message)
        assertEquals("statement_import_unsupported", code)
    }

    @Test
    fun `unsupported channel without a channel_desc still reads as a sentence`() {
        val (message, _) = parseStatementImportError(422, """{"error":"statement_import_unsupported"}""")
        assertEquals("Statement import isn't supported for this account yet.", message)
    }

    @Test
    fun `the four parse-stage 422 codes each get their own message`() {
        val cases =
            mapOf(
                "CHANNEL_DESC_NOT_SET" to "This account has no provider set — edit it and choose one.",
                "STATEMENT_FORMAT_NOT_ACCEPTED" to "This account only accepts PDF statements.",
                "STATEMENT_CONTENT_MISMATCH" to "This file doesn't look like a statement for this account.",
                "STATEMENT_PARSE_FAILED" to "Couldn't read that statement file.",
                "STATEMENT_EMPTY" to "No transactions were found in that statement.",
            )
        cases.forEach { (code, expected) ->
            val (message, parsedCode) = parseStatementImportError(422, """{"error":"detail","code":"$code"}""")
            assertEquals(expected, message)
            assertEquals(code, parsedCode)
        }
    }

    @Test
    fun `an unrecognised code falls back to the server's own error text`() {
        val (message, code) = parseStatementImportError(422, """{"error":"something new broke","code":"BRAND_NEW"}""")
        assertEquals("something new broke", message)
        assertEquals("BRAND_NEW", code)
    }

    @Test
    fun `a 5xx gets a retry-flavoured message rather than the raw server text`() {
        val (message, _) = parseStatementImportError(500, """{"error":"sql: connection refused"}""")
        assertTrue(message.contains("Try again"))
    }

    @Test
    fun `non-json, empty and null bodies degrade to the http-code fallback instead of throwing`() {
        listOf("<html>502 Bad Gateway</html>", "", null).forEach { body ->
            val (message, code) = parseStatementImportError(418, body)
            assertEquals("Import failed (HTTP 418).", message)
            assertNull(code)
        }
    }

    @Test
    fun `a body whose fields are not strings does not crash the parser`() {
        val (message, code) = parseStatementImportError(422, """{"error":{"nested":true},"code":42}""")
        assertEquals("Import failed (HTTP 422).", message)
        assertNull(code)
    }
}
