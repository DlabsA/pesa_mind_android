package cc.dlabs.pesamind.core.permissions

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
import androidx.compose.material.icons.filled.SmsFailed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * "Automatic tracing is off" banner — the app's standing, re-askable path back to the SMS
 * permission for anyone who skipped or denied it during onboarding.
 *
 * Shown on the dashboard whenever `RECEIVE_SMS` is missing. Without it the app's headline feature
 * silently does nothing and there is no way to discover why: exactly the state the v39 build
 * shipped in for every user, since nothing requested the permission at all. Modelled on
 * [cc.dlabs.pesamind.core.ui.OfflineBanner] (same warning tokens, same shape) so the two read as
 * one family.
 */
@Composable
fun SmsTracingBanner(
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
    isPermanentlyDenied: Boolean = false,
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
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.18f), modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SmsFailed, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Automatic tracing is off",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (isPermanentlyDenied) {
                        "Turn SMS on in Settings and your mobile money and bank alerts become transactions automatically."
                    } else {
                        "Allow SMS access and your mobile money and bank alerts become transactions automatically."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEnable) {
                Text(if (isPermanentlyDenied) "Open settings" else "Turn on", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
