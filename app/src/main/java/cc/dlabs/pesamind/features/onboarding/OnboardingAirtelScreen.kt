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
fun OnboardingAirtelScreen(
    navController: NavHostController,
    vm: ChannelOnboardingViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.airtel

    OnboardingStepScaffold(
        title = "Airtel Money",
        subtitle = "Add your Airtel Money number to track transactions on it.",
        stepIndex = 3,
        totalSteps = 4,
        onBack = { navController.popBackStack() },
        onSkip = {
            vm.setAirtelIncluded(false)
            navController.navigate(Routes.OnboardingBank.route)
        },
        onNext = { navController.navigate(Routes.OnboardingBank.route) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Add Airtel Money",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(checked = draft.included, onCheckedChange = { vm.setAirtelIncluded(it) })
        }

        if (draft.included) {
            OutlinedTextField(
                value = draft.accountNumber,
                onValueChange = vm::setAirtelPhone,
                label = { Text("Phone number (optional)") },
                placeholder = { Text("0750000000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
