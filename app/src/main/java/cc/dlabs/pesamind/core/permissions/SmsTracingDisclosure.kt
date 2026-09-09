package cc.dlabs.pesamind.core.permissions

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing

/**
 * Google Play's required *prominent disclosure* for the SMS permission, shown before the system
 * prompt is ever launched.
 *
 * The wording is deliberate and load-bearing for the store review, not decoration. Play's
 * Permissions and Sensitive Information policy asks the disclosure to state, in the app's own UI
 * and before the runtime request: what data is accessed, what it is used for, and that it is not
 * sold or shared. Each bullet below maps to one of those, and to what the pipeline actually does
 * (`SmsReceiver` -> `SMSMessageProcessor`, on-device parsing of the message body only). If the
 * pipeline's behavior changes, this text has to change with it — a disclosure that overstates or
 * understates the access is itself the violation.
 *
 * Rendered by [SmsTracingPermissionHost]; do not show it directly.
 */
@Composable
fun SmsTracingDisclosureDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.ExtraLarge.dp),
        icon = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Sms,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        title = {
            Text(
                text = "Allow Pesa Mind to read your transaction SMS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Space3.dp)) {
                Text(
                    text =
                        "Pesa Mind records your money automatically. To do that it needs SMS access, " +
                            "so it can read the mobile money and bank alerts your provider sends you — " +
                            "MTN MoMo, Airtel Money, Stanbic, Centenary and the other channels you set up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DisclosurePoint(
                    icon = Icons.Default.Bolt,
                    text =
                        "Each alert is turned into a transaction — amount, date, balance and reference — " +
                            "and added to your ledger without you typing anything.",
                )
                DisclosurePoint(
                    icon = Icons.Default.VisibilityOff,
                    text =
                        "Only messages from the financial senders you've added are used. Every other SMS " +
                            "is ignored, and your existing inbox is never read.",
                )
                DisclosurePoint(
                    icon = Icons.Default.Lock,
                    text =
                        "Messages are read on this device. Message text is never sold, and never shared " +
                            "with anyone for advertising.",
                )
                Text(
                    text = "Without SMS access you can still use Pesa Mind, but every transaction has to be entered by hand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text("Allow SMS access", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

@Composable
private fun DisclosurePoint(
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Drop this next to any UI that calls [SmsTracingPermissionState.request] — it is what actually
 * puts the disclosure on screen when the state asks for it. Keeping it a separate host (rather
 * than having `request()` show a dialog itself) is what lets the same state object be driven from
 * an onboarding step, a settings row, or a dashboard banner without each re-implementing the
 * disclosure text.
 */
@Composable
fun SmsTracingPermissionHost(state: SmsTracingPermissionState) {
    if (state.isDisclosureVisible) {
        SmsTracingDisclosureDialog(
            onAllow = state::onDisclosureAccepted,
            onDismiss = state::onDisclosureDismissed,
        )
    }
}
