package cc.dlabs.pesamind.features.onboarding

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.ChannelCreateOutcome
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.BatchCreateChannelItem
import cc.dlabs.pesamind.core.network.models.BatchCreateChannelsRequest
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.features.settings.channels.ChannelDescMobileMoney
import cc.dlabs.pesamind.features.settings.channels.ChannelTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { INTRO, CASH, MOMO, AIRTEL, BANK, REVIEW }

private const val TAG = "ChannelOnboardingViewModel"

/** Per-channel-type draft state, written to as the user steps through the flow — no network
 * call happens until [ChannelOnboardingViewModel.finish]. [included] is the add/skip toggle. */
data class ChannelDraft(
    val included: Boolean = false,
    val name: String = "",
    /** Provider label sent as `channel_desc` — free text for Bank (user picks from
     * [cc.dlabs.pesamind.features.settings.channels.ChannelDescBank]), fixed for MoMo/Airtel. */
    val provider: String = "",
    /** Phone number (MoMo/Airtel) or bank account number — maps to the backend's shared,
     * optional `account_number` field. */
    val accountNumber: String = "",
    /** Raw text input for opening balance; parsed to Double at submit time. */
    val openingBalanceText: String = "",
)

data class ChannelOnboardingUiState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    val cash: ChannelDraft = ChannelDraft(name = "Cash"),
    val momo: ChannelDraft = ChannelDraft(name = "MTN Mobile Money", provider = ChannelDescMobileMoney.MTNMOBILEMONEY),
    val airtel: ChannelDraft = ChannelDraft(name = "Airtel Money", provider = ChannelDescMobileMoney.AIRTELMONEY),
    val bank: ChannelDraft = ChannelDraft(name = "Bank Account"),
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

    // ── MoMo ─────────────────────────────────────────────────────────────────
    fun setMomoIncluded(included: Boolean) = _state.update { it.copy(momo = it.momo.copy(included = included)) }

    fun setMomoPhone(text: String) = _state.update { it.copy(momo = it.momo.copy(accountNumber = text)) }

    fun setMomoBalance(text: String) = _state.update { it.copy(momo = it.momo.copy(openingBalanceText = text)) }

    // ── Airtel ───────────────────────────────────────────────────────────────
    fun setAirtelIncluded(included: Boolean) = _state.update { it.copy(airtel = it.airtel.copy(included = included)) }

    fun setAirtelPhone(text: String) = _state.update { it.copy(airtel = it.airtel.copy(accountNumber = text)) }

    fun setAirtelBalance(text: String) = _state.update { it.copy(airtel = it.airtel.copy(openingBalanceText = text)) }

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
                createIfIncluded(current.momo, ChannelTypes.MOBILE_MONEY)
                createIfIncluded(current.airtel, ChannelTypes.MOBILE_MONEY)
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
