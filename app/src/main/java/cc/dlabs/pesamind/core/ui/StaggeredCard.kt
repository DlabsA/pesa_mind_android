package cc.dlabs.pesamind.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fades and slides a card in on entry, staggered by [index]. Replaces the near-identical
 * `StaggeredCard` private composables previously duplicated across Dashboard/Analytics/Budget
 * screens.
 */
@Composable
fun StaggeredCard(
    index: Int,
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val delayMs = (index * 70).coerceAtMost(350)
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(380, delayMs, FastOutSlowInEasing),
        label = "stagger_alpha_$index",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "stagger_offset_$index",
    )
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        },
    ) { content() }
}
