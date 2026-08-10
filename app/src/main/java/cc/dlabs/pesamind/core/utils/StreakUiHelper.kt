package cc.dlabs.pesamind.core.utils

import cc.dlabs.pesamind.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StreakUiHelper {
    fun label(streakCount: Int): String =
        when (streakCount) {
            0 -> "Start your streak"
            1 -> "1 day streak"
            else -> "$streakCount days"
        }

    fun drawable(
        streakCount: Int,
        lastActiveDate: String?,
    ): Int? {
        if (streakCount == 0) return null
        return if (isActiveToday(lastActiveDate)) {
            R.drawable.active_streak
        } else {
            R.drawable.inactive_streak
        }
    }

    /**
     * [lastActiveDate] is the backend's full ISO-8601 instant (e.g. "2026-08-09T21:00:00Z"),
     * representing the backend's local midnight (Africa/Kampala) — not a bare calendar date
     * already in the device's zone. Parse as a real instant and convert to the DEVICE's local
     * calendar date before comparing to "today": the user cares about their own calendar day.
     */
    fun isActiveToday(lastActiveDate: String?): Boolean {
        if (lastActiveDate.isNullOrBlank()) return false
        return try {
            val lastLocalDate = Instant.parse(lastActiveDate).atZone(ZoneId.systemDefault()).toLocalDate()
            lastLocalDate == LocalDate.now(ZoneId.systemDefault())
        } catch (_: Exception) {
            false
        }
    }
}
