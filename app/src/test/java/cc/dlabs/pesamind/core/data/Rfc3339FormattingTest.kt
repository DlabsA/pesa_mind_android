package cc.dlabs.pesamind.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [formatRfc3339] must emit a *seconds field* unconditionally. It previously used
 * `OffsetDateTime.toString()`, which emits the shortest valid ISO-8601 form and drops seconds
 * when they are zero — producing `2026-09-30T00:00Z`, which Go's `time.Parse(time.RFC3339, ...)`
 * rejects ("cannot parse \"Z\" as \":\"").
 *
 * That failure was invisible for most fields and total for two: `occurred_at`/`created_at` come
 * from `System.currentTimeMillis()` and practically never land on an exact minute, while
 * `due_date`/`target_date` come from a *date picker* and therefore always do. The result was
 * every debt/credit and saving-goal create failing 400 (`due_date must be RFC3339`), which in
 * turn stranded every purpose-linked transaction behind `OutboxPusher`'s
 * "wait for the parent's serverId" guard, and left outstanding balances never recomputed
 * server-side.
 *
 * Pure function, no Room or Android runtime needed — plain JUnit, matching
 * [ServerTimestampParsingTest].
 */
class Rfc3339FormattingTest {
    @Test
    fun `emits the seconds field for an exact-midnight date-picker value`() {
        // The literal value taken off the affected device: a "lent" debt due 2026-09-30.
        assertEquals("2026-09-30T00:00:00Z", formatRfc3339(1790726400000L))
    }

    @Test
    fun `emits the seconds field for any exact-minute instant`() {
        assertEquals("2026-01-01T00:00:00Z", formatRfc3339(1767225600000L))
    }

    @Test
    fun `preserves sub-second precision when present`() {
        assertEquals("2026-09-01T12:14:40.269Z", formatRfc3339(1788264880269L))
    }

    @Test
    fun `round-trips through the parser this codebase uses for server timestamps`() {
        val millis = 1790726400000L
        assertEquals(millis, parseRfc3339Millis(formatRfc3339(millis)))
    }

    @Test
    fun `never emits a value whose time part lacks seconds`() {
        // Guards the whole class of regression rather than the two literals above: walk a day
        // in one-minute steps, every one of which has zero seconds.
        val startOfDay = 1790726400000L
        for (minute in 0 until 1440) {
            val formatted = formatRfc3339(startOfDay + minute * 60_000L)
            assertTrue(
                "missing seconds field: $formatted",
                Regex("""T\d{2}:\d{2}:\d{2}""").containsMatchIn(formatted),
            )
        }
    }
}
