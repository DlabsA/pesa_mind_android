package cc.dlabs.pesamind.features.savinggoals

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.network.models.SavingGoalResponse
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getTertiaryColor
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Locale

// Pieces shared by SavingGoalListScreen and SavingGoalDetailScreen so the two render the
// same progress math, accent colour and date formatting instead of each rolling its own
// (both screens previously duplicated the fraction expression and a bare
// LinearProgressIndicator).

/** Achieved goals switch from the primary accent to the "done" tertiary accent. */
internal const val STATUS_ACHIEVED = "achieved"

/** Progress as a 0f..1f fraction, guarding a zero/absent target. */
internal val SavingGoalResponse.progressFraction: Float
    get() = if (targetAmount > 0) (progress / targetAmount).coerceIn(0.0, 1.0).toFloat() else 0f

/** Amount still to save, never negative. */
internal val SavingGoalResponse.remainingAmount: Double
    get() = (targetAmount - progress).coerceAtLeast(0.0)

internal val SavingGoalResponse.isAchieved: Boolean
    get() = status == STATUS_ACHIEVED

/** `"Aug 14, 2026"` for an ISO-8601 target date, or null when unset/unparseable. */
@Composable
internal fun rememberFormattedDate(isoDate: String?): String? {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    return remember(isoDate) {
        isoDate?.let {
            try {
                formatter.format(OffsetDateTime.parse(it).toInstant().toEpochMilli())
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * Rounded track + animated fill. Rolled by hand rather than using
 * [androidx.compose.material3.LinearProgressIndicator] so both ends stay fully rounded at
 * any height and the fill can animate from 0 on first composition.
 */
@Composable
internal fun GoalProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "goal_progress",
    )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(Radius.Full.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
    ) {
        // fillMaxWidth(0f) still draws a hairline on some densities, so skip the fill entirely
        // at zero progress.
        if (animated > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(animated)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Radius.Full.dp))
                        .background(color),
            )
        }
    }
}

/** Small pill used for "Achieved" / percentage chips. */
@Composable
internal fun GoalPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.Full.dp),
        color = color.copy(alpha = 0.16f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.Space3.dp, vertical = Spacing.Space1.dp + 1.dp),
        )
    }
}

/** Rounded icon tile: piggy bank while saving, check mark once the target is reached. */
@Composable
internal fun GoalIconTile(
    achieved: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val accent = if (achieved) getTertiaryColor() else MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(Radius.Medium.dp),
        color = accent.copy(alpha = 0.14f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (achieved) Icons.Filled.CheckCircle else Icons.Filled.Savings,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}
