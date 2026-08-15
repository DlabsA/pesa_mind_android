package cc.dlabs.pesamind.features.subscription

import cc.dlabs.pesamind.core.network.models.PlanResponse
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Works out what a payment buys you, so the subscription card can say it before
 * you pay rather than after.
 *
 * Pure, for the same reason as [PaymentStatusResolver] and [CheckoutValidator]: a
 * ViewModel that can reach `ApiClient.api` cannot be constructed in a JVM test
 * without firing a real HTTP request.
 *
 * The backend owns the real calculation — this only *predicts* it, so the two must
 * agree exactly. See [extend] for the one place that is genuinely subtle.
 */
object SubscriptionPeriod {
    const val INTERVAL_MONTH = "month"
    const val INTERVAL_YEAR = "year"

    private val displayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    /** Parses a server ISO-8601 timestamp, keeping its offset. Null on anything unparseable. */
    fun parse(iso: String?): OffsetDateTime? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Adds one plan interval to [from], reproducing Go's `time.AddDate` exactly.
     *
     * This is not `plusMonths(1)`. Go **rolls overflow forward**; `java.time`
     * **clamps** it, and the two disagree at every month end:
     *
     * | from        | Go `AddDate(0,1,0)`     | `plusMonths(1)` |
     * |-------------|-------------------------|-----------------|
     * | 31 Jan 2026 | 31 Feb → **3 Mar**      | 28 Feb          |
     * | 29 Feb 2024 | 29 Feb 2025 → **1 Mar** | 28 Feb 2025     |
     *
     * Going to the first of the month before adding the interval, then adding the
     * day-of-month back as plain days, rolls the same way Go does. Day 1 also can
     * never itself be clamped, which is what makes the two steps safe.
     *
     * The backend's counterpart is `Plan.Extend` in
     * `internal/domain/payment/model.go`.
     */
    fun extend(
        from: OffsetDateTime,
        interval: String,
    ): OffsetDateTime {
        val dayOffset = from.dayOfMonth - 1L
        val firstOfMonth = from.withDayOfMonth(1)
        val advanced =
            when (interval) {
                INTERVAL_YEAR -> firstOfMonth.plusYears(1)
                else -> firstOfMonth.plusMonths(1)
            }
        return advanced.plusDays(dayOffset)
    }

    /**
     * When paying for [interval] right now would leave you covered until.
     *
     * Mirrors `GrantPaidPremium` (`internal/domain/user/service.go`): the new period
     * runs from whichever is later, now or the end of what you already have. That
     * is what makes an early renewal stack instead of truncating, and stating the
     * resulting date per plan is the clearest way to show it.
     */
    fun coverageEnd(
        currentExpiry: OffsetDateTime?,
        now: OffsetDateTime,
        interval: String,
    ): OffsetDateTime {
        val from = if (currentExpiry != null && currentExpiry.isAfter(now)) currentExpiry else now
        return extend(from, interval)
    }

    /**
     * Whole days left on a period, floored at zero. Matches
     * `AccountManager.trialDaysRemaining()` so the two countdowns never disagree.
     */
    fun daysRemaining(
        expiresAt: OffsetDateTime?,
        now: Instant,
    ): Int {
        if (expiresAt == null) return 0
        return ChronoUnit.DAYS
            .between(now, expiresAt.toInstant())
            .coerceAtLeast(0)
            .toInt()
    }

    /** True while [expiresAt] is still in the future. */
    fun isActive(
        expiresAt: OffsetDateTime?,
        now: Instant,
    ): Boolean = expiresAt != null && expiresAt.toInstant().isAfter(now)

    /** `12 Oct 2026`, rendered in the timestamp's own offset. */
    fun formatDate(value: OffsetDateTime): String = value.format(displayFormat)

    /**
     * What the yearly plan saves against paying the monthly one for a year.
     *
     * Derived from the catalogue rather than hardcoded, so editing a price in the
     * database can't leave a stale "Save 8%" badge behind. Lifted from the old
     * `CheckoutScreen.savingLabelFor`.
     */
    fun savingPercent(
        plan: PlanResponse,
        plans: List<PlanResponse>,
    ): Int? {
        if (plan.interval != INTERVAL_YEAR) return null
        val monthly = plans.firstOrNull { it.interval == INTERVAL_MONTH } ?: return null
        val fullYear = monthly.amount * 12
        if (fullYear <= plan.amount) return null
        val percent = ((fullYear - plan.amount) / fullYear * 100).toInt()
        return percent.takeIf { it > 0 }
    }

    /**
     * Orders the catalogue for display: monthly first, then yearly, then anything
     * else the backend adds later, cheapest first within each group.
     */
    fun forDisplay(plans: List<PlanResponse>): List<PlanResponse> =
        plans.sortedWith(
            compareBy(
                {
                    if (it.interval == INTERVAL_MONTH) {
                        0
                    } else if (it.interval == INTERVAL_YEAR) {
                        1
                    } else {
                        2
                    }
                },
                { it.amount },
            ),
        )
}
