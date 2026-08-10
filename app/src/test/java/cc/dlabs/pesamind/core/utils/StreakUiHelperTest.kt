package cc.dlabs.pesamind.core.utils

import cc.dlabs.pesamind.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone

/**
 * [StreakUiHelper.isActiveToday] must parse the backend's full ISO-8601 instant and convert
 * it to the DEVICE's local calendar date before comparing to "today" — not read a raw
 * yyyy-MM-dd prefix (the old `SimpleDateFormat("yyyy-MM-dd")` bug: a UTC instant representing
 * the backend's local midnight, e.g. "2026-08-09T21:00:00Z", is actually *today* in a UTC+3
 * zone, but a prefix-only parse misreads it as "yesterday", flipping an active streak to
 * render as inactive). Pure function, no Android runtime needed — plain JUnit, not
 * `RobolectricTestRunner`.
 *
 * `ZoneId.systemDefault()` reads the JVM-wide `TimeZone.getDefault()`, so tests that need a
 * specific device zone pin it via `TimeZone.setDefault(...)` and restore the original
 * afterward — otherwise these tests would be flaky/order-dependent on the ambient CI zone.
 */
class StreakUiHelperTest {
    private lateinit var originalDefaultZone: TimeZone

    @Before
    fun setUp() {
        originalDefaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Kampala"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalDefaultZone)
    }

    @Test
    fun returnsTrueForAUtcInstantThatIsLocalTodayInAPositiveOffsetZone() {
        // Kampala-local midnight of "today", expressed in UTC — exactly the shape the backend
        // sends, and exactly the reported bug: under the old prefix-only SimpleDateFormat
        // parse, this instant's raw UTC calendar date is "yesterday" whenever device local
        // time is between 00:00-03:00, misreporting an active streak as inactive.
        val kampala = ZoneId.of("Africa/Kampala")
        val kampalaLocalMidnightToday = LocalDate.now(kampala).atStartOfDay(kampala).toInstant()

        assertTrue(StreakUiHelper.isActiveToday(kampalaLocalMidnightToday.toString()))
    }

    @Test
    fun returnsFalseForAGenuinelyPriorDayInstant() {
        val twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS)

        assertFalse(StreakUiHelper.isActiveToday(twoDaysAgo.toString()))
    }

    @Test
    fun returnsTrueAtDeviceLocalStartOfTodayInclusiveBoundary() {
        val zone = ZoneId.systemDefault()
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant()

        assertTrue(StreakUiHelper.isActiveToday(startOfToday.toString()))
    }

    @Test
    fun returnsFalseOneSecondBeforeDeviceLocalMidnight() {
        val zone = ZoneId.systemDefault()
        val oneSecondBeforeToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().minusSeconds(1)

        assertFalse(StreakUiHelper.isActiveToday(oneSecondBeforeToday.toString()))
    }

    @Test
    fun returnsTrueForANegativeOffsetZoneWhereUtcDateIsAheadOfLocalDate() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val losAngeles = ZoneId.of("America/Los_Angeles")
        // Late-evening LA-local "today" is already the next UTC calendar day — proves the fix
        // isn't accidentally correct only for positive-offset zones like Kampala's.
        val laLocalNineThirtyPmToday = LocalDate.now(losAngeles).atTime(21, 30).atZone(losAngeles).toInstant()

        assertTrue(StreakUiHelper.isActiveToday(laLocalNineThirtyPmToday.toString()))
    }

    @Test
    fun returnsFalseForNullInput() {
        assertFalse(StreakUiHelper.isActiveToday(null))
    }

    @Test
    fun returnsFalseForBlankInput() {
        assertFalse(StreakUiHelper.isActiveToday("   "))
    }

    @Test
    fun returnsFalseForGarbageInput() {
        assertFalse(StreakUiHelper.isActiveToday("not a date"))
    }

    @Test
    fun returnsFalseForABareDateOnlyStringWithNoTimeComponent() {
        // Deliberate contract narrowing vs. the old SimpleDateFormat-based implementation:
        // the backend never sends this shape for last_active_date, so this isn't a real-world
        // regression — Instant.parse requires a time/offset component.
        assertFalse(StreakUiHelper.isActiveToday("2026-08-09"))
    }

    @Test
    fun drawableReturnsNullWhenStreakCountIsZero() {
        assertNull(StreakUiHelper.drawable(0, Instant.now().toString()))
    }

    @Test
    fun drawableReturnsActiveDrawableWhenCountPositiveAndActiveToday() {
        assertEquals(R.drawable.active_streak, StreakUiHelper.drawable(2, Instant.now().toString()))
    }

    @Test
    fun drawableReturnsInactiveDrawableWhenCountPositiveAndNotActiveToday() {
        val twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS).toString()

        assertEquals(R.drawable.inactive_streak, StreakUiHelper.drawable(2, twoDaysAgo))
    }
}
