package cc.dlabs.pesamind.features.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.asUgx

@Composable
fun OnboardingReviewScreen(
    navController: NavHostController,
    vm: ChannelOnboardingViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) {
            navController.navigate(Routes.Dashboard.route) {
                popUpTo(Routes.ChannelOnboardingIntro.route) { inclusive = true }
            }
        }
    }

    // MoMo/Airtel accountNumber is composed (country code + digits) for display here — see
    // ChannelDraft.effectiveMobileNumber's doc comment; Bank/Cash keep their raw account number
    // as-is (a bank account number isn't a phone number, so it must never go through this).
    val included =
        listOfNotNull(state.cash.takeIf { it.included }, state.bank.takeIf { it.included }) +
            state.momo.filter { it.included }.map { it.copy(accountNumber = it.effectiveMobileNumber()) } +
            state.airtel.filter { it.included }.map { it.copy(accountNumber = it.effectiveMobileNumber()) }

    OnboardingStepScaffold(
        title = "Review",
        subtitle =
            if (included.isEmpty()) {
                "You haven't added any channels yet — that's fine, you can add them later from Settings."
            } else {
                "These channels will be created:"
            },
        stepIndex = 6,
        totalSteps = 6,
        onBack = { navController.popBackStack() },
        onSkip = null,
        onNext = vm::finish,
        nextLabel = if (state.isSaving) "Saving…" else "Finish",
        nextEnabled = !state.isSaving,
    ) {
        included.forEach { draft ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.Space4.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Space1.dp),
                ) {
                    Text(text = draft.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (draft.accountNumber.isNotBlank()) {
                        Text(text = draft.accountNumber, style = MaterialTheme.typography.bodySmall)
                    }
                    val balance = draft.openingBalanceText.toDoubleOrNull()
                    if (balance != null && balance > 0.0) {
                        Text(text = balance.asUgx(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        state.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
