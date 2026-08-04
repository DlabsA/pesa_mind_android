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
import cc.dlabs.pesamind.core.ui.ProviderDropdown
import cc.dlabs.pesamind.features.settings.channels.ChannelDescBank

@Composable
fun OnboardingBankScreen(
    navController: NavHostController,
    vm: ChannelOnboardingViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.bank

    OnboardingStepScaffold(
        title = "Bank account",
        subtitle = "Add a bank account to track transfers and payments through it.",
        stepIndex = 4,
        totalSteps = 4,
        onBack = { navController.popBackStack() },
        onSkip = {
            vm.setBankIncluded(false)
            navController.navigate(Routes.OnboardingReview.route)
        },
        onNext = { navController.navigate(Routes.OnboardingReview.route) },
        // account_number is optional for every channel type (matches
        // category.ValidateChannelFields backend-side) — only the provider is required here,
        // since channel_desc is required for Bank.
        nextEnabled = !draft.included || draft.provider.isNotBlank(),
        nextLabel = "Review",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Add a bank account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(checked = draft.included, onCheckedChange = { vm.setBankIncluded(it) })
        }

        if (draft.included) {
            ProviderDropdown(
                options = ChannelDescBank.valid,
                selected = draft.provider,
                onSelect = vm::setBankProvider,
            )
            OutlinedTextField(
                value = draft.accountNumber,
                onValueChange = vm::setBankAccountNumber,
                label = { Text("Account number (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.openingBalanceText,
                onValueChange = vm::setBankBalance,
                label = { Text("Opening balance (optional)") },
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
