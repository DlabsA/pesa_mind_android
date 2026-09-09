package cc.dlabs.pesamind.features.onboarding

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.permissions.SmsTracingPermissionHost
import cc.dlabs.pesamind.core.permissions.rememberSmsTracingPermissionState
import cc.dlabs.pesamind.core.theme.Radius
import cc.dlabs.pesamind.core.theme.Spacing

/**
 * The step that turns automatic tracing on — and the one the app was missing entirely.
 *
 * Placed first in the flow, right after the intro, rather than in `MainActivity.onCreate`: the ask
 * arrives inside a screen that explains it, which is what Play's Permissions policy means by an
 * incremental, in-context request, and it is early enough that the SIM-slot step immediately after
 * actually has `READ_PHONE_STATE` to read carrier names with. Nothing here launches a system prompt
 * on its own — tapping the button opens `SmsTracingDisclosureDialog` first, and only "Allow" there
 * reaches the OS.
 *
 * Skippable like every other step: a user who declines still gets a working manual-entry app, and
 * `SmsTracingBanner` on the dashboard is the standing way back.
 */
@Composable
fun OnboardingSmsAccessScreen(navController: NavHostController) {
    val permissions = rememberSmsTracingPermissionState()

    SmsTracingPermissionHost(permissions)

    OnboardingStepScaffold(
        title = if (permissions.isGranted) "Automatic tracing is on" else "Turn on automatic tracing",
        subtitle =
            if (permissions.isGranted) {
                "Pesa Mind will record transactions from these accounts as their alerts arrive."
            } else {
                "Pesa Mind reads the transaction alerts your provider sends you by SMS and turns them " +
                    "into transactions for you. Nothing else in your messages is used."
            },
        stepIndex = 1,
        totalSteps = 7,
        onBack = { navController.popBackStack() },
        onSkip = if (permissions.isGranted) null else ({ navController.navigate(Routes.OnboardingSimSlots.route) }),
        onNext = { navController.navigate(Routes.OnboardingSimSlots.route) },
        nextLabel = if (permissions.isGranted) "Next" else "Continue without it",
    ) {
        Text(
            text = "What Pesa Mind does with SMS access:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TRACING_POINTS.forEach { point ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Spacing.Space5.dp),
                )
                Text(point, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }

        Surface(
            shape = RoundedCornerShape(Radius.Large.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.Space4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Space3.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (permissions.isGranted) Icons.Default.CheckCircle else Icons.Default.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (permissions.isGranted) "SMS access granted" else "SMS access needed",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (permissions.isGranted) {
                            "You're all set — new alerts become transactions automatically."
                        } else {
                            "We'll explain exactly what's read before Android asks you."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!permissions.isGranted) {
            Button(
                onClick = permissions::request,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.Medium.dp),
            ) {
                Text(
                    if (permissions.isPermanentlyDenied) "Open app settings" else "Turn on automatic tracing",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (permissions.isPermanentlyDenied) {
                Text(
                    text = "Android won't show the prompt again — grant \"SMS\" under Permissions, then come back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The three claims the [cc.dlabs.pesamind.core.permissions.SmsTracingDisclosureDialog] then repeats
 * verbatim. Kept in step with what `SMSMessageProcessor` actually does — this screen and that
 * dialog are the app's disclosure to the user, so an inaccurate bullet here is a policy problem,
 * not a copy problem.
 */
private val TRACING_POINTS =
    listOf(
        "Reads mobile money and bank alerts as they arrive",
        "Turns each one into a transaction on this device",
        "Ignores every other message, and never reads your inbox",
    )
