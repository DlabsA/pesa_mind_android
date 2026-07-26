package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing

/**
 * Full-screen "content is loading" skeleton: a column of shimmering blocks, one per
 * [blockHeights] entry. Replaces the near-identical `*SkeletonView` + nested
 * `SkeletonBlock` pairs previously duplicated in Dashboard/Analytics/Budget screens (the
 * Dashboard/Analytics copies computed a shimmer alpha and never applied it, so those two
 * were silently static — this version always animates).
 */
@Composable
fun SkeletonColumn(
    blockHeights: List<Dp>,
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.Space4.dp,
) {
    val alpha = rememberShimmerAlpha()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.Space4.dp, vertical = Spacing.Space2.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        blockHeights.forEach { height ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(Radius.Large.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.92f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
                                ),
                            ),
                        ),
            )
        }
    }
}

/**
 * "Stats card populating" skeleton: [rows] rows of two shimmering values side by side.
 * Replaces the near-identical stats-row skeletons previously duplicated across Budget /
 * Yearly-budget / Set-monthly-budget screens.
 */
@Composable
fun StatsRowsSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.ExtraLarge.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Space5.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
        ) {
            repeat(rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ShimmerBox(Modifier.height(14.dp).fillMaxWidth(0.35f))
                    ShimmerBox(Modifier.height(14.dp).fillMaxWidth(0.6f))
                }
            }
        }
    }
}

/**
 * A card-shaped skeleton shell: same `Card` chrome every row/card skeleton in the app was
 * separately reimplementing, with the actual placeholder layout left to the caller since
 * card content shapes genuinely differ per screen (transaction row vs channel row vs
 * summary card). Pass [accentBrush] for a left accent bar (e.g. the channel list's moving
 * highlight sweep); omit it for a plain card.
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.Large.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: Dp = Spacing.Space4.dp,
    accentBrush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        if (accentBrush != null) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    modifier =
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(accentBrush),
                )
                Column(modifier = Modifier.padding(contentPadding), content = content)
            }
        } else {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}
