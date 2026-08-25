package cc.dlabs.pesamind.features.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.features.settings.channels.MobileMoneyNumberField

@Composable
fun OnboardingMoMoScreen(
    navController: NavHostController,
    vm: ChannelOnboardingViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    OnboardingStepScaffold(
        title = "MTN Mobile Money",
        subtitle = "Add your MTN MoMo number(s) to track transactions on them.",
        stepIndex = 3,
        totalSteps = 6,
        onBack = { navController.popBackStack() },
        onSkip = {
            state.momo.indices.forEach { vm.setMomoIncluded(it, false) }
            navController.navigate(Routes.OnboardingAirtel.route)
        },
        onNext = { navController.navigate(Routes.OnboardingAirtel.route) },
    ) {
        state.momo.forEachIndexed { index, draft ->
            if (index > 0) HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (index == 0) "Add MTN Mobile Money" else "Another MTN Mobile Money account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Switch(checked = draft.included, onCheckedChange = { vm.setMomoIncluded(index, it) })
            }

            if (draft.included) {
                // Same field the standalone Add-Channel dialog uses (ChannelFormDialog) — same
                // country dropdown + digit-count validation + normalized shape, so a number
                // entered here and one entered post-onboarding via Settings look identical.
                MobileMoneyNumberField(
                    phoneNumber = draft.accountNumber,
                    onPhoneNumberChange = { vm.setMomoPhone(index, it) },
                    selectedCountry = draft.selectedCountry,
                    onCountryChange = { vm.setMomoCountry(index, it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.openingBalanceText,
                    onValueChange = { vm.setMomoBalance(index, it) },
                    label = { Text("Opening balance (optional)") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (index > 0) {
                TextButton(onClick = { vm.removeMomoDraft(index) }) { Text("Remove this account") }
            }
        }

        TextButton(onClick = { vm.addMomoDraft() }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add another MTN Mobile Money account")
        }
    }
}
