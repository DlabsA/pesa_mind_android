package cc.dlabs.pesamind.features.settings.channels

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.ChannelCreateOutcome
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelState(
    val channels: List<ChannelDetails> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    // Read once at init (matches TransactionViewModel's identical pattern) — gates whether
    // per-channel SMS auto-capture can be toggled at all. Defaults true so the toggle doesn't
    // flash as locked before the real value loads; toggleSmsNotification re-checks the real
    // value server-side-cached value before acting regardless of what the UI shows.
    val isPremium: Boolean = true,
)

/**
 * Room-backed (ADR-0004 Slice A1) — reads/writes go through [ChannelRepository], never
 * `ApiClient`/`ChannelManager` directly. [state]'s `channels` list is kept live by the
 * [ChannelRepository.observeChannels] collector below; create/update/delete/toggle methods
 * don't need to splice their own result into `channels` by hand (the old network-backed
 * version did, and that manual splicing is exactly what caused `updateChannel`'s
 * false-success bug the data-path-tracer found — a "Channel updated successfully" message
 * with the list still showing pre-edit values). Room's write-then-Flow-reemit does that
 * correctly by construction.
 *
 * [activeTypeFilter]/[activeStatusFilter] exist because the live collector runs for this
 * ViewModel's whole lifetime: `compose-perf` and `android-reviewer` both independently caught
 * that a one-shot filtered write into `_state.channels` (the first version of this file) gets
 * silently clobbered back to the full list the instant *any* write hits the channels table —
 * which happens concurrently and often, since SMS auto-creation (`ChannelManager.
 * isSmsAllowedForSender` -> `ChannelRepository.reconcileFromServer`) writes to this same table
 * from a background service. Tracking the active filter and having the collector re-apply it
 * on every emission (instead of a one-shot DAO query racing the collector) fixes this by
 * construction rather than by timing.
 */
class ChannelViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(ChannelState(isLoading = true))
    val state: StateFlow<ChannelState> = _state.asStateFlow()

    private var activeTypeFilter: String? = null
    private var activeStatusFilter: Boolean? = null

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPremium = AccountManager.isPremium())
        }
        viewModelScope.launch {
            try {
                ChannelRepository.observeChannels().collect { channels ->
                    _state.value = _state.value.copy(channels = applyActiveFilter(channels), isLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to load channels: ${e.message}")
            }
        }
    }

    private fun applyActiveFilter(channels: List<ChannelDetails>): List<ChannelDetails> =
        when {
            activeTypeFilter != null -> channels.filter { it.channelType == activeTypeFilter }
            activeStatusFilter != null -> channels.filter { it.status == activeStatusFilter }
            else -> channels
        }

    /** Resets out of a `loadChannelsByType`/`loadChannelsByStatus` filtered view back to the
     * full list, and satisfies the existing "refresh" icon/pull-to-refresh call sites. The
     * `getAllChannels()` call itself is a one-shot Room read, not a network call — freshness
     * against the server comes from [SyncScheduler.triggerSyncNow] below, whose pull writes
     * back into Room and reaches this screen via [ChannelRepository.observeChannels] once it
     * completes. */
    fun loadChannels() {
        activeTypeFilter = null
        activeStatusFilter = null
        SyncScheduler.triggerSyncNow()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val channels = ChannelRepository.getAllChannels()
            _state.value = _state.value.copy(isLoading = false, channels = channels)
        }
    }

    fun refresh() = loadChannels()

    fun loadChannelsByType(channelType: String) {
        val normalizedType = ChannelTypes.normalizeOrNull(channelType)
        if (normalizedType == null) {
            _state.value =
                _state.value.copy(
                    error = "Invalid channel type. Use: ${ChannelTypes.valid.joinToString()}",
                )
            return
        }

        activeTypeFilter = normalizedType
        activeStatusFilter = null
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val filtered = ChannelRepository.getByChannelType(normalizedType)
            _state.value = _state.value.copy(isLoading = false, channels = filtered)
        }
    }

    fun loadChannelsByStatus(status: Boolean) {
        activeStatusFilter = status
        activeTypeFilter = null
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val filtered = ChannelRepository.getByActiveStatus(status)
            _state.value = _state.value.copy(isLoading = false, channels = filtered)
        }
    }

    fun createChannel(
        name: String,
        description: String,
        channelType: String,
        channelDescription: String,
        status: Boolean = true,
        openingBalance: Double = 0.0,
    ) {
        val normalizedType = ChannelTypes.normalizeOrNull(channelType)
        val normalizedChannelDesc =
            ChannelDescMobileMoney.normalizeOrNull(channelDescription)
                ?: ChannelDescBank.normalizeOrNull(channelDescription)
        when {
            name.isBlank() -> {
                _state.value = _state.value.copy(error = "Name is required")
                return
            }
            description.isBlank() -> {
                _state.value = _state.value.copy(error = "Description is required")
                return
            }
            normalizedType == null -> {
                _state.value =
                    _state.value.copy(
                        error = "Invalid channel type. Use: ${ChannelTypes.valid.joinToString()}",
                    )
                return
            }
            normalizedType != ChannelTypes.CASH && (channelDescription.isBlank() || normalizedChannelDesc == null) -> {
                _state.value = _state.value.copy(error = "Valid channel description is required for this type")
                return
            }
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            when (
                val outcome =
                    ChannelRepository.createChannel(
                        name = name.trim(),
                        description = description.trim(),
                        channelType = normalizedType,
                        channelDesc = normalizedChannelDesc ?: "Cash",
                        status = status,
                        openingBalance = openingBalance,
                    )
            ) {
                is ChannelCreateOutcome.Created -> {
                    publishEvent(
                        StateEvent.ChannelCreated(channelId = outcome.channel.id, channelName = outcome.channel.name),
                    )
                    _state.value = _state.value.copy(isSaving = false, message = "Channel created successfully")
                }
                is ChannelCreateOutcome.AlreadyExists -> {
                    // Deduped against an existing channel for this same provider — nothing was
                    // inserted, so this must not be reported as a fresh success (it previously
                    // was, silently discarding the user's custom name/description).
                    _state.value =
                        _state.value.copy(
                            isSaving = false,
                            message = "A channel for this provider already exists: ${outcome.existing.name}",
                        )
                }
            }
        }
    }

    fun updateChannel(
        id: String,
        name: String,
        description: String,
        channelDescription: String,
        status: Boolean,
    ) {
        if (id.isBlank()) {
            _state.value = _state.value.copy(error = "Invalid channel id")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val updated =
                    ChannelRepository.updateChannel(id, name.trim(), description.trim(), channelDescription.trim(), status)
                _state.value =
                    if (updated != null) {
                        publishEvent(StateEvent.ChannelUpdated(channelId = id, channelName = updated.name))
                        _state.value.copy(isSaving = false, message = "Channel updated successfully")
                    } else {
                        _state.value.copy(isSaving = false, error = "Channel not found")
                    }
            } catch (e: Exception) {
                // New failure mode since normalizedSenderKey is unique-indexed: picking a
                // provider that collides with another existing channel's dedup key throws.
                _state.value =
                    _state.value.copy(isSaving = false, error = "Couldn't save changes: ${e.message ?: "unknown error"}")
            }
        }
    }

    /**
     * Toggle SMS auto-capture for a channel (non-CASH only). This is the real per-channel
     * control [cc.dlabs.pesamind.core.storage.ChannelManager.isSmsAllowedForSender] reads —
     * gated on Premium/active-trial here too, not just in the UI, since a stored `true` on a
     * channel from before a trial lapsed must never let a Free account re-enable capture by
     * calling this directly.
     */
    fun toggleSmsNotification(channelId: String) {
        viewModelScope.launch {
            try {
                val targetChannel = _state.value.channels.find { it.id == channelId } ?: return@launch

                if (targetChannel.channelType == "CASH") {
                    _state.value = _state.value.copy(error = "SMS notifications not available for CASH channels")
                    return@launch
                }

                if (!AccountManager.isPremium()) {
                    _state.value =
                        _state.value.copy(
                            isPremium = false,
                            error = "Upgrade to Premium to control SMS auto-capture per channel",
                        )
                    return@launch
                }

                val newFlag = !targetChannel.smsNotificationEnabled
                ChannelRepository.setSmsNotificationEnabled(channelId, newFlag)

                _state.value =
                    _state.value.copy(
                        message = "SMS notifications ${if (newFlag) "enabled" else "disabled"} for this channel",
                    )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to update notification setting: ${e.message}")
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
