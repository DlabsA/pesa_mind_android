package cc.dlabs.pesamind.features.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ============================================================================
// Pattern Grid State & Logic
// ============================================================================

/**
 * Reusable pattern grid component for both unlock and setup screens.
 * Handles:
 * - Fixed 3x3 dot positioning
 * - Drag detection and hit testing
 * - Line drawing between selected dots
 * - Consistent visual styling
 */
@Composable
fun PatternGrid(
    modifier: Modifier = Modifier,
    selectedDots: List<Int>,
    currentDragPos: Offset?,
    onDotSelected: (index: Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    lineColor: Color,
    dotColorUnselected: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
    dotColorSelected: Color = MaterialTheme.colorScheme.primary,
    isLoading: Boolean = false,
) {
    val density = LocalDensity.current
    var gridSize by remember { mutableStateOf(IntSize.Zero) }

    val dotContainerSize = 52.dp
    val dotHaloSize = 36.dp
    val dotSize = 16.dp
    val dotContainerSizePx = with(density) { dotContainerSize.toPx() }
    val touchRadiusPx = with(density) { 28.dp.toPx() }

    val dotPositions =
        remember(gridSize) {
            List(9) { index ->
                val row = index / 3
                val col = index % 3
                Offset(
                    x = gridSize.width.toFloat() * (col + 1) / 4f,
                    y = gridSize.height.toFloat() * (row + 1) / 4f,
                )
            }
        }

    fun addDotIfHit(offset: Offset) {
        val hitIndex =
            dotPositions.indexOfFirst { centre ->
                (offset - centre).getDistance() <= touchRadiusPx
            }

        if (hitIndex >= 0 && !selectedDots.contains(hitIndex)) {
            onDotSelected(hitIndex)
        }
    }

    Surface(
        modifier =
            modifier
                .size(300.dp)
                .clip(RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { gridSize = it }
                    .pointerInput(isLoading, dotPositions) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (isLoading) return@detectDragGestures
                                onDragStart()
                                addDotIfHit(offset)
                            },
                            onDrag = { change, _ ->
                                if (isLoading) return@detectDragGestures
                                addDotIfHit(change.position)
                            },
                            onDragEnd = {
                                if (isLoading) return@detectDragGestures
                                onDragEnd()
                            },
                            onDragCancel = {
                                if (isLoading) return@detectDragGestures
                                onDragCancel()
                            },
                        )
                    },
        ) {
            // Lines layer (drawn first, below dots)
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Committed path lines
                for (i in 0 until selectedDots.size - 1) {
                    val a = dotPositions.getOrNull(selectedDots[i]) ?: continue
                    val b = dotPositions.getOrNull(selectedDots[i + 1]) ?: continue
                    drawLine(
                        color = lineColor.copy(alpha = 0.7f),
                        start = a,
                        end = b,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                // Trailing ghost line to finger
                val tail = dotPositions.getOrNull(selectedDots.lastOrNull() ?: -1)
                val drag = currentDragPos
                if (tail != null && drag != null && selectedDots.isNotEmpty()) {
                    drawLine(
                        color = lineColor.copy(alpha = 0.35f),
                        start = tail,
                        end = drag,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Dots layer
            dotPositions.forEachIndexed { index, centre ->
                val isSelected = selectedDots.contains(index)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .offset {
                                IntOffset(
                                    x = (centre.x - dotContainerSizePx / 2f).roundToInt(),
                                    y = (centre.y - dotContainerSizePx / 2f).roundToInt(),
                                )
                            }
                            .size(dotContainerSize),
                ) {
                    // Halo background + border
                    Box(
                        modifier =
                            Modifier
                                .size(dotHaloSize)
                                .background(
                                    color =
                                        if (isSelected) {
                                            lineColor.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                        },
                                    shape = CircleShape,
                                )
                                .border(
                                    width = 1.dp,
                                    color =
                                        if (isSelected) {
                                            lineColor.copy(alpha = 0.55f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                        },
                                    shape = CircleShape,
                                ),
                    )

                    // Inner dot
                    Box(
                        modifier =
                            Modifier
                                .size(dotSize)
                                .background(
                                    if (isSelected) dotColorSelected else dotColorUnselected,
                                    CircleShape,
                                ),
                    )
                }
            }
        }
    }
}
