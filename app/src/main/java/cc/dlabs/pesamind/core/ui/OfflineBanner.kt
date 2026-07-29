package cc.dlabs.pesamind.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.DarkColors
import cc.dlabs.pesamind.core.theme.LightColors
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing

/**
 * "You're offline, showing cached data" banner — shown above already-loaded content when
 * connectivity drops. Replaces the near-identical private `DashboardOfflineBanner`/
 * `AnalyticsOfflineBanner` composables previously duplicated per-screen; both also used
 * hardcoded hex colors (`Color(0xFFFF9500)`/`Color(0xFFFF6B00)`), a direct violation of
 * `.claude/CLAUDE.md`'s "no manual color literals — use theme colors" rule. Uses the existing
 * `LightColors.Warning`/`DarkColors.Warning` semantic tokens instead.
 */
@Composable
fun OfflineBanner(
    caption: String,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val tint = if (isDark) DarkColors.Warning else LightColors.Warning
    val tintBg = if (isDark) DarkColors.WarningBg else LightColors.WarningBg

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.Large.dp),
        color = tintBg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Space4.dp, vertical = Spacing.Space3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.18f),
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
            Column {
                Text(
                    "You're offline",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Showing cached data · $caption",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
