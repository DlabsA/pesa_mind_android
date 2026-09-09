package cc.dlabs.pesamind.features.settings.simslots

import android.content.Context
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.storage.SimSlotManager
import cc.dlabs.pesamind.features.settings.channels.COUNTRY_CODES
import cc.dlabs.pesamind.features.settings.channels.CountryCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SimSlotsState(
    /**
     * The slots the form renders a number field for. Normally the device's live active slots, but
     * falls back to previously-saved slots (and finally to slot 1) so there is always somewhere to
     * type a number — a removed SIM or a denied phone-state permission must not leave the
     * drift-blocking dialog with no way to resolve itself.
     */
    val displaySlots: List<SimSlotManager.SimSlot> = emptyList(),
    val simSlotNumbers: Map<Int, String> = emptyMap(),
    val simSlotCountries: Map<Int, CountryCode> = emptyMap(),
    val driftDetected: Boolean = false,
    val isSaving: Boolean = false,
    val successMessage: String? = null,
)

/**
 * Backs both [SimSlotsScreen] (the standalone Settings entry) and `PesaMindNavGraph`'s
 * drift-blocking overlay — the same state/logic previously embedded in `AccountViewModel`
 * (`activeSimSlots`/`simSlotNumbers`/`simSlotDriftDetected`/`isSavingSimSlots` and
 * `loadSimSlots`/`onSimSlotNumberChange`/`saveSimSlots`), extracted so SIM slots has its own
 * screen instead of only being reachable by scrolling Account Settings. Adds per-slot country
 * tracking (parsed from any previously-saved value) so a number entered here round-trips
 * through [cc.dlabs.pesamind.features.settings.channels.MobileMoneyNumberField] identically to
 * every other phone-number field in the app — same pattern as
 * `ChannelOnboardingViewModel.loadSimSlots`/`saveSimSlots`.
 */
class SimSlotsViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(SimSlotsState())
    val state: StateFlow<SimSlotsState> = _state.asStateFlow()

    fun load(context: Context) {
        viewModelScope.launch {
            val activeSlots = SimSlotManager.getActiveSlots(context)
            val slots =
                activeSlots.ifEmpty {
                    SimSlotManager.getStoredSlotIndices()
                        .ifEmpty { listOf(0) }
                        .map { index ->
                            SimSlotManager.SimSlot(
                                slotIndex = index,
                                // Blank rather than a guessed carrier: the OS isn't telling us
                                // what's in this tray, and the field header renders "SIM n" alone
                                // when there's no name to show.
                                carrierName = SimSlotManager.getCarrierForSlot(index).orEmpty(),
                            )
                        }
                }
            val numbers = mutableMapOf<Int, String>()
            val countries = mutableMapOf<Int, CountryCode>()
            slots.forEach { slot ->
                val stored = SimSlotManager.getNumberForSlot(slot.slotIndex) ?: ""
                val match = COUNTRY_CODES.firstOrNull { stored.startsWith(it.code) }
                if (match != null) {
                    numbers[slot.slotIndex] = stored.removePrefix(match.code).filter { it.isDigit() }
                    countries[slot.slotIndex] = match
                } else {
                    numbers[slot.slotIndex] = stored.filter { it.isDigit() }
                }
            }
            _state.update {
                it.copy(
                    displaySlots = slots,
                    simSlotNumbers = numbers,
                    simSlotCountries = countries,
                    driftDetected = SimSlotManager.isDriftDetected(),
                )
            }
        }
    }

    fun onNumberChange(
        slotIndex: Int,
        value: String,
    ) = _state.update { it.copy(simSlotNumbers = it.simSlotNumbers + (slotIndex to value)) }

    fun onCountryChange(
        slotIndex: Int,
        country: CountryCode,
    ) = _state.update { it.copy(simSlotCountries = it.simSlotCountries + (slotIndex to country)) }

    /** Re-saving always clears [SimSlotsState.driftDetected] (and the app-wide block — see
     * [SimSlotManager.saveSlotNumbers]). */
    fun save(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val current = _state.value
            val composed =
                current.simSlotNumbers.mapValues { (slotIndex, digits) ->
                    if (digits.isBlank()) "" else "${(current.simSlotCountries[slotIndex] ?: COUNTRY_CODES[0]).code}$digits"
                }
            SimSlotManager.saveSlotNumbers(context, composed)
            _state.update {
                it.copy(isSaving = false, driftDetected = false, successMessage = "SIM slot numbers saved")
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(successMessage = null) }
}
