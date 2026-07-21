package cc.dlabs.pesamind.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.Radius

/**
 * The pulsing alpha every skeleton in this app should animate on. Was previously
 * reimplemented with slightly different numbers (0.25-0.3 -> 0.65-0.8, 900-1200ms) in six
 * different screen files; standardized here on the most common values.
 */
@Composable
fun rememberShimmerAlpha(
    initialValue: Float = 0.3f,
    targetValue: Float = 0.7f,
    durationMillis: Int = 900,
): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "shimmer_alpha",
    )
    return alpha
}

/** A single pulsing placeholder block — the building block for row/card skeletons. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Radius.Small.dp,
) {
    val alpha = rememberShimmerAlpha()
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.10f)),
    )
}
