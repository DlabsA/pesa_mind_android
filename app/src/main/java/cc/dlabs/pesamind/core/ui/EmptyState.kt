package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.Spacing

/**
 * "Nothing here yet" state: icon in a soft pill, title, subtitle, optional action below.
 * Replaces the near-identical private EmptyState composables previously duplicated across
 * Transactions/Channels/Budgets/Analytics screens.
 *
 * [iconSize] is the icon's *container* (the pill); pass [iconContentSize] separately for
 * screens that want a bare icon at its own size (e.g. a transparent-background compact
 * empty state) rather than the default "icon at 45% of its pill" proportion.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 80.dp,
    iconContentSize: Dp = iconSize * 0.45f,
    iconShape: Shape = CircleShape,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
    subtitleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    iconSpacing: Dp = Spacing.Space5.dp,
    textSpacing: Dp = Spacing.Space2.dp,
    actionSpacing: Dp = Spacing.Space3.dp,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = iconShape,
            color = iconBackground,
            modifier = Modifier.size(iconSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconContentSize),
                    tint = iconTint,
                )
            }
        }

        Spacer(Modifier.size(iconSpacing))

        Text(
            text = title,
            style = titleStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.size(textSpacing))

        Text(
            text = subtitle,
            style = subtitleStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (action != null) {
            Spacer(Modifier.size(actionSpacing))
            action()
        }
    }
}
