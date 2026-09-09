package cc.dlabs.pesamind.features.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.features.settings.channels.COUNTRY_CODES
import cc.dlabs.pesamind.features.settings.channels.MobileMoneyNumberField

/**
 * New onboarding step, inserted right after [OnboardingIntroScreen]: prompts the user to map
 * each active physical SIM slot to its phone number. Some carriers never provision a SIM's own
 * MSISDN (`SmsReceiver.SimInfo.NOT_PROVISIONED`), so an incoming mobile-money SMS on those
 * devices only ever reports *which slot* it arrived on — [cc.dlabs.pesamind.core.storage.SimSlotManager]'s
 * fallback mapping is what lets that resolve to a real receiving number, which in turn is what
 * lets two same-provider channels (two MoMo lines) be told apart (see [OnboardingMoMoScreen]).
 *
 * Placed here (one-time device fact, independent of which channels get added) rather than only
 * in Account Settings, where a user previously had to discover it unprompted. Mirrors
 * `AccountSettingsScreen`'s identical section/`AccountViewModel`'s identical state shape —
 * intentionally duplicated onto [ChannelOnboardingViewModel] rather than sharing a ViewModel,
 * since onboarding and Account Settings are separate nav graphs with separate `viewModel()`
 * scopes. Uses [MobileMoneyNumberField] (same as the standalone Add-Channel dialog and
 * [OnboardingMoMoScreen]/[OnboardingAirtelScreen]) so every phone-number field in the app has
 * the same normalized country-code + digits shape.
 *
 * Auto-skippable in spirit: a single-SIM (or no dual-SIM) device reports zero/one active slots,
 * so there's nothing meaningfully ambiguous to map — the step still renders (for `onBack`
 * consistency in the stepper) but shows an explanatory message instead of empty fields, and
 * `Next` proceeds without requiring input either way.
 */
@Composable
fun OnboardingSimSlotsScreen(
    navController: NavHostController,
    vm: ChannelOnboardingViewModel,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadSimSlots(context) }

    OnboardingStepScaffold(
        title = "SIM numbers",
        subtitle =
            "Some phones can't tell us a SIM's own number automatically. Enter it here so " +
                "mobile money SMS always match the right account.",
        stepIndex = 2,
        totalSteps = 7,
        onBack = { navController.popBackStack() },
        onSkip = { navController.navigate(Routes.OnboardingCash.route) },
        onNext = {
            if (state.activeSimSlots.isNotEmpty()) {
                vm.saveSimSlots(context)
            }
            navController.navigate(Routes.OnboardingCash.route)
        },
        nextEnabled = !state.isSavingSimSlots,
    ) {
        if (state.activeSimSlots.isEmpty()) {
            Text(
                text = "We didn't detect more than one active SIM on this device — nothing to set up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.activeSimSlots.forEach { slot ->
                Text(
                    text = "SIM ${slot.slotIndex + 1} — ${slot.carrierName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MobileMoneyNumberField(
                    phoneNumber = state.simSlotNumbers[slot.slotIndex] ?: "",
                    onPhoneNumberChange = { vm.onSimSlotNumberChange(slot.slotIndex, it) },
                    selectedCountry = state.simSlotCountries[slot.slotIndex] ?: COUNTRY_CODES[0],
                    onCountryChange = { vm.onSimSlotCountryChange(slot.slotIndex, it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
