package cc.dlabs.pesamind.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.theme.getPrimaryColor

/**
 * Shared step header/progress/nav-buttons chrome for the channel-onboarding flow's screens
 * (Cash/MoMo/Airtel/Bank/Review). No stepper composable existed anywhere in the app before this
 * flow — `LockSetupScreen` is the closest precedent but is a single fork, not a sequence — so
 * this is new, local to `features/onboarding/` rather than `core/ui/`, since nothing else in the
 * app needs a multi-step wizard chrome yet.
 */
@Composable
fun OnboardingStepScaffold(
    title: String,
    subtitle: String,
    stepIndex: Int,
    totalSteps: Int,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    onNext: () -> Unit,
    nextLabel: String = "Next",
    nextEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .padding(Spacing.Space6.dp),
    ) {
        LinearProgressIndicator(
            progress = { stepIndex / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = getPrimaryColor(),
        )
        Spacer(Modifier.height(Spacing.Space2.dp))
        Text(
            text = "Step $stepIndex of $totalSteps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.Space6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.Space2.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(Spacing.Space6.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space4.dp),
            content = content,
        )

        Spacer(Modifier.height(Spacing.Space4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("Back") }
            } else {
                Spacer(Modifier.height(1.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Space2.dp)) {
                if (onSkip != null) {
                    TextButton(onClick = onSkip) { Text("Skip") }
                }
                Button(
                    onClick = onNext,
                    enabled = nextEnabled,
                    shape = RoundedCornerShape(12.dp),
                ) { Text(nextLabel) }
            }
        }
    }
}
