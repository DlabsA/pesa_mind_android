package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing

/**
 * "The load failed" state: icon in a soft tinted pill, title, message, retry button. Replaces
 * the near-identical private ErrorState/DashboardErrorView/AnalyticsErrorView composables
 * previously duplicated across Transactions/Dashboard/Analytics screens.
 *
 * Icon treatment mirrors [EmptyState]'s "icon in a pill" pattern (same 80dp/36dp proportions)
 * for a consistent look between the two states, rather than this composable's previous bare
 * floating icon. [iconTint]/[iconBackground] default to the theme's error colors but are
 * overridable — a caller distinguishing "you're offline" from a real server error (see
 * `AnalyticsScreen`) can pass warmer, less alarming tones for the former.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.error,
    iconBackground: Color = MaterialTheme.colorScheme.errorContainer,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.Space6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = iconBackground,
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.size(Spacing.Space5.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.size(Spacing.Space2.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(Spacing.Space5.dp))

        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(Radius.Medium.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("Try Again")
        }
    }
}
