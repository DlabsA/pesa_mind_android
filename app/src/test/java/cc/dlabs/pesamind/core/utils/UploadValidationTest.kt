package cc.dlabs.pesamind.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Client-side gate for statement uploads. Every rejection here is one the backend would also
 * make — the point is to make it instantly and in plain language instead of spending the user's
 * data on a round trip that ends in a 413 or a 422.
 */
class UploadValidationTest {
    private val pdfOnly = setOf("pdf")
    private val pdfOrCsv = setOf("pdf", "csv")

    @Test
    fun `a normal pdf passes`() {
        assertNull(validateUpload("MoMo_Statement.pdf", sizeBytes = 240_000, allowedExtensions = pdfOnly))
    }

    @Test
    fun `an empty file is rejected before anything else`() {
        assertEquals(
            "That file is empty.",
            validateUpload("statement.pdf", sizeBytes = 0, allowedExtensions = pdfOnly),
        )
    }

    @Test
    fun `the size cap matches the backend's 10 MiB, inclusive`() {
        assertNull(validateUpload("big.pdf", sizeBytes = MAX_UPLOAD_BYTES, allowedExtensions = pdfOnly))
        assertEquals(
            "That file is too large (max 10 MB).",
            validateUpload("big.pdf", sizeBytes = MAX_UPLOAD_BYTES + 1, allowedExtensions = pdfOnly),
        )
    }

    @Test
    fun `csv is refused for a pdf-only provider but accepted for stanbic`() {
        assertNotNull(validateUpload("account_statement.csv", sizeBytes = 5_000, allowedExtensions = pdfOnly))
        assertNull(validateUpload("account_statement.csv", sizeBytes = 5_000, allowedExtensions = pdfOrCsv))
    }

    @Test
    fun `extension matching is case-insensitive`() {
        assertNull(validateUpload("STATEMENT.PDF", sizeBytes = 5_000, allowedExtensions = pdfOnly))
    }

    @Test
    fun `a name with no extension is called out separately from a wrong one`() {
        val message = validateUpload("statement", sizeBytes = 5_000, allowedExtensions = pdfOrCsv)
        assertNotNull(message)
        assertTrue(message!!.contains("Couldn't tell"))
    }

    @Test
    fun `only the final dot counts as the extension separator`() {
        assertEquals("pdf", "2026.06.statement.pdf".fileExtension())
        assertEquals("", "no-dots-here".fileExtension())
    }
}
