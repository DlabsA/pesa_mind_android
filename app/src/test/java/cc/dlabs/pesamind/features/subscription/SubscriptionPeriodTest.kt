package cc.dlabs.pesamind.features.subscription

import cc.dlabs.pesamind.core.network.models.PlanResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Covers [SubscriptionPeriod] — the date the subscription card promises you before
 * you pay.
 *
 * The card states an exact date ("Covers you to 3 Mar 2026"), so this arithmetic has
 * to match what the Go backend will actually compute in `Plan.Extend` /
 * `GrantPaidPremium`. The month-end cases below are the ones where the obvious
 * `plusMonths(1)` silently disagrees with it.
 *
 * Plain JUnit on pure functions — no `runTest`, nothing that can reach `ApiClient`.
 */
class SubscriptionPeriodTest {
    private fun at(iso: String) = OffsetDateTime.parse(iso)

    // ─── extend: Go AddDate compatibility ─────────────────────────────────────

    @Test
    fun `mid-month monthly extension keeps the day of month`() {
        val result = SubscriptionPeriod.extend(at("2026-03-15T10:30:00Z"), "month")
        assertEquals(at("2026-04-15T10:30:00Z"), result)
    }

    @Test
    fun `31 Jan plus a month rolls over to 3 Mar, it does not clamp to 28 Feb`() {
        // Go: 31 Jan + 1 month = "31 Feb" = 3 Mar (Feb 2026 has 28 days).
        // java.time's plusMonths would give 28 Feb — a 3-day disagreement with the
        // date the user's money actually buys.
        val result = SubscriptionPeriod.extend(at("2026-01-31T00:00:00Z"), "month")
        assertEquals(at("2026-03-03T00:00:00Z"), result)
    }

    @Test
    fun `31 May plus a month rolls to 1 Jul`() {
        // June has 30 days, so "31 Jun" is 1 Jul — one day of roll-over.
        val result = SubscriptionPeriod.extend(at("2026-05-31T00:00:00Z"), "month")
        assertEquals(at("2026-07-01T00:00:00Z"), result)
    }

    @Test
    fun `30 Jan plus a month in a leap year rolls to 1 Mar`() {
        // Feb 2024 has 29 days, so "30 Feb" is 1 Mar.
        val result = SubscriptionPeriod.extend(at("2024-01-30T00:00:00Z"), "month")
        assertEquals(at("2024-03-01T00:00:00Z"), result)
    }

    @Test
    fun `29 Feb plus a year rolls to 1 Mar of the non-leap year`() {
        // Go: 29 Feb 2024 + 1 year = "29 Feb 2025" = 1 Mar 2025.
        // plusYears would clamp to 28 Feb 2025.
        val result = SubscriptionPeriod.extend(at("2024-02-29T00:00:00Z"), "year")
        assertEquals(at("2025-03-01T00:00:00Z"), result)
    }

    @Test
    fun `plain yearly extension keeps the same calendar day`() {
        val result = SubscriptionPeriod.extend(at("2026-10-12T09:00:00Z"), "year")
        assertEquals(at("2027-10-12T09:00:00Z"), result)
    }

    @Test
    fun `extension preserves the time of day and the offset`() {
        val result = SubscriptionPeriod.extend(at("2026-03-15T23:45:12+03:00"), "month")
        assertEquals(at("2026-04-15T23:45:12+03:00"), result)
    }

    @Test
    fun `an unknown interval falls back to monthly`() {
        // The backend's own default branch is monthly; a new interval string must
        // never produce a wildly wrong promise.
        val result = SubscriptionPeriod.extend(at("2026-03-15T00:00:00Z"), "fortnight")
        assertEquals(at("2026-04-15T00:00:00Z"), result)
    }

    // ─── coverageEnd: the stacking rule ───────────────────────────────────────

    @Test
    fun `with no current period coverage runs from now`() {
        val now = at("2026-08-12T00:00:00Z")
        val result = SubscriptionPeriod.coverageEnd(currentExpiry = null, now = now, interval = "month")
        assertEquals(at("2026-09-12T00:00:00Z"), result)
    }

    @Test
    fun `paying early stacks on the remaining period instead of resetting`() {
        // The whole point: 61 days left, paying now must not forfeit them.
        val now = at("2026-08-12T00:00:00Z")
        val expiry = at("2026-10-12T00:00:00Z")
        val result = SubscriptionPeriod.coverageEnd(expiry, now, "month")
        assertEquals(at("2026-11-12T00:00:00Z"), result)
        assertTrue("must extend past the existing period end", result.isAfter(expiry))
    }

    @Test
    fun `a lapsed period is ignored and coverage runs from now`() {
        // Mirrors the backend clearing a lapsed grant so a re-purchase computes from
        // now rather than a stale past expiry.
        val now = at("2026-08-12T00:00:00Z")
        val lapsed = at("2026-05-01T00:00:00Z")
        val result = SubscriptionPeriod.coverageEnd(lapsed, now, "month")
        assertEquals(at("2026-09-12T00:00:00Z"), result)
    }

    @Test
    fun `an unexpired trial is stacked on just like a paid period`() {
        val now = at("2026-08-12T00:00:00Z")
        val trialEnd = at("2026-08-30T00:00:00Z")
        val result = SubscriptionPeriod.coverageEnd(trialEnd, now, "year")
        assertEquals(at("2027-08-30T00:00:00Z"), result)
    }

    // ─── daysRemaining / isActive ─────────────────────────────────────────────

    @Test
    fun `days remaining counts whole days and floors at zero`() {
        val now = Instant.parse("2026-08-12T00:00:00Z")
        assertEquals(61, SubscriptionPeriod.daysRemaining(at("2026-10-12T00:00:00Z"), now))
        assertEquals(0, SubscriptionPeriod.daysRemaining(at("2026-08-12T06:00:00Z"), now))
        assertEquals(0, SubscriptionPeriod.daysRemaining(at("2026-01-01T00:00:00Z"), now))
        assertEquals(0, SubscriptionPeriod.daysRemaining(null, now))
    }

    @Test
    fun `isActive tracks whether the period has passed`() {
        val now = Instant.parse("2026-08-12T00:00:00Z")
        assertTrue(SubscriptionPeriod.isActive(at("2026-08-13T00:00:00Z"), now))
        assertFalse(SubscriptionPeriod.isActive(at("2026-08-11T00:00:00Z"), now))
        assertFalse(SubscriptionPeriod.isActive(null, now))
    }

    // ─── parse ────────────────────────────────────────────────────────────────

    @Test
    fun `parse keeps the server offset and rejects junk without throwing`() {
        assertEquals(at("2026-10-12T15:00:00+03:00"), SubscriptionPeriod.parse("2026-10-12T15:00:00+03:00"))
        assertNull(SubscriptionPeriod.parse(null))
        assertNull(SubscriptionPeriod.parse(""))
        assertNull(SubscriptionPeriod.parse("not-a-date"))
    }

    // ─── savingPercent / forDisplay ───────────────────────────────────────────

    private val monthly = PlanResponse(code = "m", name = "Monthly", interval = "month", amount = 15_000.0)
    private val yearly = PlanResponse(code = "y", name = "Yearly", interval = "year", amount = 150_000.0)

    @Test
    fun `saving percent is derived from the catalogue`() {
        // 12 x 15,000 = 180,000 against 150,000 => 16.67% => 16 after truncation.
        assertEquals(16, SubscriptionPeriod.savingPercent(yearly, listOf(monthly, yearly)))
    }

    @Test
    fun `no saving badge when there is nothing to save`() {
        assertNull(SubscriptionPeriod.savingPercent(monthly, listOf(monthly, yearly)))
        assertNull(SubscriptionPeriod.savingPercent(yearly, listOf(yearly)))
        val overpriced = yearly.copy(amount = 200_000.0)
        assertNull(SubscriptionPeriod.savingPercent(overpriced, listOf(monthly, overpriced)))
    }

    @Test
    fun `display order puts monthly before yearly regardless of server order`() {
        val ordered = SubscriptionPeriod.forDisplay(listOf(yearly, monthly))
        assertEquals(listOf("m", "y"), ordered.map { it.code })
    }
}
