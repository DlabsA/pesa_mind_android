package cc.dlabs.pesamind.core.utils

import cc.dlabs.pesamind.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object StreakUiHelper {
    private const val STREAK_DATE_PATTERN = "yyyy-MM-dd"

    fun label(streakCount: Int): String = when (streakCount) {
        0 -> "Start your streak"
        1 -> "1 day streak"
        else -> "$streakCount days"
    }

    fun drawable(streakCount: Int, lastActiveDate: String?): Int? {
        if (streakCount == 0) return null
        return if (isActiveToday(lastActiveDate)) {
            R.drawable.active_streak
        } else {
            R.drawable.inactive_streak
        }
    }

    fun isActiveToday(lastActiveDate: String?): Boolean {
        if (lastActiveDate.isNullOrBlank()) return false
        return try {
            val sdf = SimpleDateFormat(STREAK_DATE_PATTERN, Locale.getDefault())
            val lastDate = sdf.parse(lastActiveDate) ?: return false
            val lastCal = Calendar.getInstance().apply { time = lastDate }
            val todayCal = Calendar.getInstance()
            lastCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                lastCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        } catch (_: Exception) {
            false
        }
    }
}

