package cc.dlabs.pesamind.features.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes

@Composable
fun OnboardingCashScreen(
    navController: NavHostController,
    vm: ChannelOnboardingViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.cash

    OnboardingStepScaffold(
        title = "Cash",
        subtitle = "Track cash you spend or receive outside mobile money and bank transfers.",
        stepIndex = 1,
        totalSteps = 4,
        onBack = { navController.popBackStack() },
        onSkip = {
            vm.setCashIncluded(false)
            navController.navigate(Routes.OnboardingMoMo.route)
        },
        onNext = { navController.navigate(Routes.OnboardingMoMo.route) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Add a Cash wallet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Switch(checked = draft.included, onCheckedChange = { vm.setCashIncluded(it) })
        }

        if (draft.included) {
            OutlinedTextField(
                value = draft.openingBalanceText,
                onValueChange = vm::setCashBalance,
                label = { Text("Opening balance (optional)") },
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
