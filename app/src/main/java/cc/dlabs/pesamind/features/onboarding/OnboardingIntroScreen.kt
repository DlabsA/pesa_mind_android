package cc.dlabs.pesamind.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.theme.Spacing

@Composable
fun OnboardingIntroScreen(
    navController: NavHostController,
    vm: ChannelOnboardingViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // "Skip for now" routes through the same vm.finish() as Review's Finish button — with
    // nothing included, it's a no-op batch call whose only job is to flip the onboarded flag.
    LaunchedEffect(state.finished) {
        if (state.finished) {
            navController.navigate(Routes.Dashboard.route) {
                popUpTo(Routes.ChannelOnboardingIntro.route) { inclusive = true }
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.Space6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Let's set up your money sources",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.Space2.dp))
        Text(
            text =
                "Add cash, mobile money, or a bank account so you can start tracking transactions. " +
                    "You can add up to 4 — or skip this entirely and add them later from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.Space8.dp))

        Button(
            onClick = { navController.navigate(Routes.OnboardingSmsAccess.route) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !state.isSaving,
        ) { Text("Get Started") }

        Spacer(Modifier.height(Spacing.Space2.dp))

        TextButton(onClick = { vm.finish() }, enabled = !state.isSaving) { Text("Skip for now") }
    }
}
