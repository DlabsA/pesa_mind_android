package cc.dlabs.pesamind.features.onboarding

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.ChannelCreateOutcome
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.database.entity.ChannelEntity
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.BatchCreateChannelItem
import cc.dlabs.pesamind.core.network.models.BatchCreateChannelsRequest
import cc.dlabs.pesamind.core.storage.SimSlotManager
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.utils.PhoneNumberNormalizer
import cc.dlabs.pesamind.features.settings.channels.COUNTRY_CODES
import cc.dlabs.pesamind.features.settings.channels.ChannelDescMobileMoney
import cc.dlabs.pesamind.features.settings.channels.ChannelTypes
import cc.dlabs.pesamind.features.settings.channels.CountryCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { INTRO, SIM_SLOTS, CASH, MOMO, AIRTEL, BANK, REVIEW }

private const val TAG = "ChannelOnboardingViewModel"

/** Per-channel-type draft state, written to as the user steps through the flow — no network
 * call happens until [ChannelOnboardingViewModel.finish]. [included] is the add/skip toggle. */
data class ChannelDraft(
    val included: Boolean = false,
    val name: String = "",
    /** Provider label sent as `channel_desc` — free text for Bank (user picks from
     * [cc.dlabs.pesamind.features.settings.channels.ChannelDescBank]), fixed for MoMo/Airtel. */
    val provider: String = "",
    /** Phone number (MoMo/Airtel, digits only, no country code — see [selectedCountry]) or bank
     * account number (Bank, free text) — maps to the backend's shared, optional `account_number`
     * field. */
    val accountNumber: String = "",
    /** MoMo/Airtel only, ignored for Bank/Cash — the country whose code prefixes
     * [accountNumber] in [effectiveMobileNumber], same [CountryCode]/[COUNTRY_CODES] the
     * standalone Add-Channel dialog (`ChannelFormDialog`'s `MobileMoneyNumberField`) uses, so
     * onboarding and post-onboarding entry produce the same normalized shape. */
    val selectedCountry: CountryCode = COUNTRY_CODES[0],
    /** Raw text input for opening balance; parsed to Double at submit time. */
    val openingBalanceText: String = "",
) {
    /** `+<country code><digits>`, or blank if no number was entered — mirrors
     * `ChannelFormDialog`'s `effectiveDescription` for `MOBILE_MONEY`. Meaningless for Bank/Cash
     * drafts (never called for them). */
    fun effectiveMobileNumber(): String = if (accountNumber.isBlank()) "" else "${selectedCountry.code}$accountNumber"
}

data class ChannelOnboardingUiState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    // SIM-tied provider types are lists — a user can have more than one line for the same
    // provider (e.g. two MTN MoMo SIMs); Bank/Cash aren't SIM-tied and stay singular. Default
    // to a single-item list so the existing single-draft UI/behavior is unchanged for anyone
    // who never adds a second one.
    val momo: List<ChannelDraft> =
        listOf(ChannelDraft(name = "MTN Mobile Money", provider = ChannelDescMobileMoney.MTNMOBILEMONEY)),
    val airtel: List<ChannelDraft> =
        listOf(ChannelDraft(name = "Airtel Money", provider = ChannelDescMobileMoney.AIRTELMONEY)),
    val cash: ChannelDraft = ChannelDraft(name = "Cash"),
    val bank: ChannelDraft = ChannelDraft(name = "Bank Account"),
    // ── SIM Slots (local-only, never synced — see SimSlotManager) ───────────────────────────
    val activeSimSlots: List<SimSlotManager.SimSlot> = emptyList(),
    val simSlotNumbers: Map<Int, String> = emptyMap(),
    // Defaults per-slot to COUNTRY_CODES[0] where absent — see loadSimSlots's parsing.
    val simSlotCountries: Map<Int, CountryCode> = emptyMap(),
    val isSavingSimSlots: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
)

/**
 * Drives the one-shot, skippable post-signup channel-onboarding flow (intro -> Cash -> MoMo ->
 * Airtel -> Bank -> review -> finish). Plain `UnifiedViewModel` reached via `viewModel()`,
 * mirroring `AuthViewModel`/`ChannelViewModel`'s pattern rather than Hilt — this flow has no
 * dependency beyond the existing `ChannelRepository`/`TokenManager` singletons.
 *
 * Each screen writes into local draft state only; [finish] is the single point that touches the
 * network, by looping [ChannelRepository.createChannel] once per non-skipped channel (offline-
 * safe via the existing outbox — see that call site's comment for why this is preferred over
 * calling the batch endpoint directly from here). Finishing with zero included channels is valid.
 */
class ChannelOnboardingViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(ChannelOnboardingUiState())
    val state: StateFlow<ChannelOnboardingUiState> = _state.asStateFlow()

    // ── Navigation within the flow ──────────────────────────────────────────
    fun goTo(step: OnboardingStep) {
        _state.update { it.copy(step = step, error = null) }
    }

    // ── Cash ─────────────────────────────────────────────────────────────────
    fun setCashIncluded(included: Boolean) = _state.update { it.copy(cash = it.cash.copy(included = included)) }

    fun setCashBalance(text: String) = _state.update { it.copy(cash = it.cash.copy(openingBalanceText = text)) }

    // ── MoMo (list — see ChannelOnboardingUiState's doc comment) ────────────────────────────
    private fun updateMomoAt(
        index: Int,
        transform: (ChannelDraft) -> ChannelDraft,
    ) = _state.update { it.copy(momo = it.momo.mapIndexed { i, d -> if (i == index) transform(d) else d }) }

    fun setMomoIncluded(
        index: Int,
        included: Boolean,
    ) = updateMomoAt(index) { it.copy(included = included) }

    fun setMomoPhone(
        index: Int,
        text: String,
    ) = updateMomoAt(index) { it.copy(accountNumber = text) }

    fun setMomoBalance(
        index: Int,
        text: String,
    ) = updateMomoAt(index) { it.copy(openingBalanceText = text) }

    fun setMomoCountry(
        index: Int,
        country: CountryCode,
    ) = updateMomoAt(index) { it.copy(selectedCountry = country) }

    /** `included = true` so the new draft's fields are visible immediately — a bare "add
     * another" tap with no visible follow-up (an off switch the user must then also flip) reads
     * as broken, not as "row added, go turn it on." */
    fun addMomoDraft() =
        _state.update {
            it.copy(
                momo =
                    it.momo +
                        ChannelDraft(
                            included = true,
                            name = "MTN Mobile Money",
                            provider = ChannelDescMobileMoney.MTNMOBILEMONEY,
                        ),
            )
        }

    /** No-op if [index] is the only draft — always keep at least one row so the UI has
     * something to show. */
    fun removeMomoDraft(index: Int) =
        _state.update {
            if (it.momo.size <= 1) it else it.copy(momo = it.momo.filterIndexed { i, _ -> i != index })
        }

    // ── Airtel (list — see ChannelOnboardingUiState's doc comment) ──────────────────────────
    private fun updateAirtelAt(
        index: Int,
        transform: (ChannelDraft) -> ChannelDraft,
    ) = _state.update { it.copy(airtel = it.airtel.mapIndexed { i, d -> if (i == index) transform(d) else d }) }

    fun setAirtelIncluded(
        index: Int,
        included: Boolean,
    ) = updateAirtelAt(index) { it.copy(included = included) }

    fun setAirtelPhone(
        index: Int,
        text: String,
    ) = updateAirtelAt(index) { it.copy(accountNumber = text) }

    fun setAirtelBalance(
        index: Int,
        text: String,
    ) = updateAirtelAt(index) { it.copy(openingBalanceText = text) }

    fun setAirtelCountry(
        index: Int,
        country: CountryCode,
    ) = updateAirtelAt(index) { it.copy(selectedCountry = country) }

    /** See [addMomoDraft]'s doc comment for why this defaults to `included = true`. */
    fun addAirtelDraft() =
        _state.update {
            it.copy(
                airtel =
                    it.airtel +
                        ChannelDraft(
                            included = true,
                            name = "Airtel Money",
                            provider = ChannelDescMobileMoney.AIRTELMONEY,
                        ),
            )
        }

    fun removeAirtelDraft(index: Int) =
        _state.update {
            if (it.airtel.size <= 1) it else it.copy(airtel = it.airtel.filterIndexed { i, _ -> i != index })
        }

    // ── SIM Slots ────────────────────────────────────────────────────────────────────────
    // Mirrors AccountViewModel's identical SIM-slot section — placed here too so a user fills
    // in the slot→number mapping once, up front, as part of onboarding rather than only
    // discovering the settings screen later. See SimSlotManager's doc comment for why this is
    // local-only and never synced.
    fun loadSimSlots(context: Context) {
        viewModelScope.launch {
            val activeSlots = SimSlotManager.getActiveSlots(context)
            // A previously-saved slot number came from AccountSettingsScreen's plain text field
            // (no country selection) or an earlier version of this screen — parse out any
            // country-code prefix the same way ChannelFormDialog parses initialDescription, so
            // a re-visit shows the right country + just the local digits instead of them all
            // getting swept into the digits field.
            val numbers = mutableMapOf<Int, String>()
            val countries = mutableMapOf<Int, CountryCode>()
            activeSlots.forEach { slot ->
                val stored = SimSlotManager.getNumberForSlot(slot.slotIndex) ?: ""
                val match = COUNTRY_CODES.firstOrNull { stored.startsWith(it.code) }
                if (match != null) {
                    numbers[slot.slotIndex] = stored.removePrefix(match.code).filter { it.isDigit() }
                    countries[slot.slotIndex] = match
                } else {
                    numbers[slot.slotIndex] = stored.filter { it.isDigit() }
                }
            }
            _state.update { it.copy(activeSimSlots = activeSlots, simSlotNumbers = numbers, simSlotCountries = countries) }
        }
    }

    fun onSimSlotNumberChange(
        slotIndex: Int,
        value: String,
    ) = _state.update { it.copy(simSlotNumbers = it.simSlotNumbers + (slotIndex to value)) }

    fun onSimSlotCountryChange(
        slotIndex: Int,
        country: CountryCode,
    ) = _state.update { it.copy(simSlotCountries = it.simSlotCountries + (slotIndex to country)) }

    fun saveSimSlots(context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isSavingSimSlots = true) }
            val current = _state.value
            val composed =
                current.simSlotNumbers.mapValues { (slotIndex, digits) ->
                    if (digits.isBlank()) "" else "${(current.simSlotCountries[slotIndex] ?: COUNTRY_CODES[0]).code}$digits"
                }
            SimSlotManager.saveSlotNumbers(context, composed)
            _state.update { it.copy(isSavingSimSlots = false) }
        }
    }

    // ── Bank ─────────────────────────────────────────────────────────────────
    fun setBankIncluded(included: Boolean) = _state.update { it.copy(bank = it.bank.copy(included = included)) }

    fun setBankProvider(provider: String) = _state.update { it.copy(bank = it.bank.copy(provider = provider, name = provider)) }

    fun setBankAccountNumber(text: String) = _state.update { it.copy(bank = it.bank.copy(accountNumber = text)) }

    fun setBankBalance(text: String) = _state.update { it.copy(bank = it.bank.copy(openingBalanceText = text)) }

    /**
     * Loops [ChannelRepository.createChannel] for every included draft (skipped drafts
     * contribute nothing), then optimistically mirrors the resulting local channel count into
     * the onboarding flag before the batch call below completes — offline-safe, so a re-login
     * before sync still sees the right value. The server now computes `channels_onboarded`
     * live from actual channel count (not a stored flag), so finishing with zero included
     * channels correctly leaves the flag `false`: the prompt reappears on the next login rather
     * than being permanently suppressed by a "completed onboarding" state. The batch call
     * itself is still sent (even empty) — items are idempotent via client-id — purely so the
     * server's audit timestamp gets set; its failure doesn't block finishing, since the flag
     * itself no longer depends on it.
     */
    fun finish() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            val batchItems = mutableListOf<BatchCreateChannelItem>()

            suspend fun createIfIncluded(
                draft: ChannelDraft,
                channelType: String,
            ) {
                if (!draft.included) return
                val balance = draft.openingBalanceText.toDoubleOrNull()
                val accountNumber = draft.accountNumber.trim().ifBlank { null }
                val outcome =
                    ChannelRepository.createChannel(
                        name = draft.name,
                        // The single-create endpoint (used by the outbox's eager/retry push)
                        // still requires a non-blank description, unlike the batch endpoint's
                        // DTO below — this flow doesn't ask the user for one, so mirror the
                        // name rather than sending "" and permanently failing that push.
                        description = draft.name,
                        channelType = channelType,
                        channelDesc = draft.provider,
                        status = true,
                        accountNumber = accountNumber,
                        openingBalance = balance ?: 0.0,
                        // Same user-entered number drives SMS-matching disambiguation — see
                        // ChannelEntity's doc comment. This is what actually lets two drafts for
                        // the same provider (two MoMo lines) coexist instead of the second
                        // deduping against the first.
                        receivingNumber =
                            PhoneNumberNormalizer.normalize(accountNumber) ?: ChannelEntity.UNSPECIFIED_RECEIVING_NUMBER,
                    )
                val channel =
                    when (outcome) {
                        is ChannelCreateOutcome.Created -> outcome.channel
                        is ChannelCreateOutcome.AlreadyExists -> outcome.existing
                        is ChannelCreateOutcome.LimitReached -> {
                            // Onboarding's fixed set (1 Cash, 2 MobileMoney, 1 Bank) never
                            // exceeds today's Free caps, so this only fires for a returning
                            // user re-entering onboarding with channels from a prior session —
                            // skip the draft rather than block the rest of the flow.
                            Log.w(
                                TAG,
                                "Onboarding skipped $channelType: Free-tier limit (${outcome.limit}) reached",
                            )
                            return
                        }
                    }
                batchItems.add(
                    BatchCreateChannelItem(
                        id = channel.id,
                        name = channel.name,
                        channelType = channelType,
                        channelDesc = draft.provider,
                        status = true,
                        accountNumber = accountNumber,
                        openingBalance = balance,
                    ),
                )
            }

            try {
                createIfIncluded(current.cash, ChannelTypes.CASH)
                // MoMo/Airtel accountNumber is composed (country code + digits) here — see
                // ChannelDraft.effectiveMobileNumber's doc comment; Bank/Cash keep their raw
                // free-text account number as-is.
                current.momo.forEach {
                    createIfIncluded(it.copy(accountNumber = it.effectiveMobileNumber()), ChannelTypes.MOBILE_MONEY)
                }
                current.airtel.forEach {
                    createIfIncluded(it.copy(accountNumber = it.effectiveMobileNumber()), ChannelTypes.MOBILE_MONEY)
                }
                createIfIncluded(current.bank, ChannelTypes.BANK)

                if (batchItems.isNotEmpty()) {
                    publishEvent(StateEvent.ChannelsRefreshed)
                }

                // Offline-first: set the local flag before the network call below, so the flow
                // is considered complete even if the device is offline right now. Mirrors
                // whether any channel was actually included, not an unconditional true.
                TokenManager.setChannelsOnboarded(batchItems.isNotEmpty())

                try {
                    ApiClient.api.batchCreateChannels(BatchCreateChannelsRequest(batchItems))
                } catch (e: Exception) {
                    Log.w(TAG, "Onboarding batch flag-sync call failed; will retry on next login", e)
                }

                _state.update { it.copy(isSaving = false, finished = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Onboarding finish failed", e)
                _state.update { it.copy(isSaving = false, error = e.message ?: "Failed to save channels") }
            }
        }
    }

    private companion object {
        const val TAG = "ChannelOnboardingVM"
    }
}
