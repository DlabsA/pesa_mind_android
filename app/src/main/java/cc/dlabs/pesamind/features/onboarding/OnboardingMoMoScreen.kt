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
fun OnboardingMoMoScreen(
    navController: NavHostController,
    vm: ChannelOnboardingViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.momo

    OnboardingStepScaffold(
        title = "MTN Mobile Money",
        subtitle = "Add your MTN MoMo number to track transactions on it.",
        stepIndex = 2,
        totalSteps = 4,
        onBack = { navController.popBackStack() },
        onSkip = {
            vm.setMomoIncluded(false)
            navController.navigate(Routes.OnboardingAirtel.route)
        },
        onNext = { navController.navigate(Routes.OnboardingAirtel.route) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Add MTN Mobile Money",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(checked = draft.included, onCheckedChange = { vm.setMomoIncluded(it) })
        }

        if (draft.included) {
            OutlinedTextField(
                value = draft.accountNumber,
                onValueChange = vm::setMomoPhone,
                label = { Text("Phone number (optional)") },
                placeholder = { Text("0770000000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.openingBalanceText,
                onValueChange = vm::setMomoBalance,
                label = { Text("Opening balance (optional)") },
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
