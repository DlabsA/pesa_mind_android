package cc.dlabs.pesamind.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [parseServerTimestampMillis] parses the backend's Go `time.Time.String()` format — not
 * ISO-8601, and easy to get subtly wrong (variable-length fractional seconds, a redundant
 * trailing zone abbreviation). This is the fix for the bug where every re-synced transaction
 * (e.g. after a logout/Room-wipe) got its local `createdAt` stamped with "now" instead of its
 * real date, silently pulling the user's whole history into whichever month they resynced in.
 * Pure function, no Room needed — plain JUnit, not [org.robolectric.RobolectricTestRunner].
 */
class ServerTimestampParsingTest {
    @Test
    fun parsesFractionalSecondsAndUtcOffset() {
        assertEquals(1783885893525L, parseServerTimestampMillis("2026-07-12 19:51:33.525482 +0000 UTC"))
    }

    @Test
    fun parsesWithNoFractionalSeconds() {
        assertEquals(1767225600000L, parseServerTimestampMillis("2026-01-01 00:00:00 +0000 UTC"))
    }

    @Test
    fun returnsNullForNullInput() {
        assertNull(parseServerTimestampMillis(null))
    }

    @Test
    fun returnsNullForBlankInput() {
        assertNull(parseServerTimestampMillis("   "))
    }

    @Test
    fun returnsNullForGarbageInputRatherThanThrowing() {
        assertNull(parseServerTimestampMillis("not a date"))
    }

    @Test
    fun preservesChronologicalOrdering() {
        val earlier = parseServerTimestampMillis("2026-07-01 00:00:00 +0000 UTC")
        val later = parseServerTimestampMillis("2026-08-01 00:00:00 +0000 UTC")
        assertTrue(requireNotNull(earlier) < requireNotNull(later))
    }
}
