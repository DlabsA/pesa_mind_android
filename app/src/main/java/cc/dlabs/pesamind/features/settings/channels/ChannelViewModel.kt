package cc.dlabs.pesamind.features.settings.channels

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.CreateChannelRequest
import cc.dlabs.pesamind.core.network.models.UpdateChannelRequest
import cc.dlabs.pesamind.core.storage.ChannelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelState(
    val channels: List<ChannelDetails> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class ChannelViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(ChannelState(isLoading = true))
    val state: StateFlow<ChannelState> = _state.asStateFlow()

    init {
        loadChannels()
    }

    fun refresh() = loadChannels()

    /**
     * Load channels: first from local cache, then sync with backend
     */
    fun loadChannels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // Step 1: Load from local cache first
                val cachedChannels = ChannelManager.getChannels()
                if (cachedChannels.isNotEmpty()) {
                    _state.value = _state.value.copy(channels = cachedChannels)

                    // Keep first open offline-first: only auto-refresh when cache is stale.
                    if (!ChannelManager.isCacheStale()) {
                        _state.value = _state.value.copy(isLoading = false, error = null)
                        return@launch
                    }
                }

                // Step 2: Sync with backend
                val response = ApiClient.api.getChannels()
                if (response.isSuccessful) {
                    val channels = response.body().orEmpty()

                    // Restore SMS notification flags from local storage before saving
                    val channelsWithFlags =
                        channels.map { channel ->
                            if (channel.channelType != "CASH") {
                                val localFlag = ChannelManager.isSmsNotificationEnabled(channel.id)
                                channel.copy(smsNotificationEnabled = localFlag)
                            } else {
                                channel
                            }
                        }

                    // Save to local storage
                    ChannelManager.saveChannels(channelsWithFlags)

                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            channels = channelsWithFlags,
                            error = null,
                        )
                } else {
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            error = "Failed to load channels (${response.code()})",
                        )
                }
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = "Cannot reach server: ${e.message ?: "Unknown error"}",
                    )
            }
        }
    }

    fun loadChannelsByType(channelType: String) {
        val normalizedType = ChannelTypes.normalizeOrNull(channelType)
        if (normalizedType == null) {
            _state.value =
                _state.value.copy(
                    error = "Invalid channel type. Use: ${ChannelTypes.valid.joinToString()}",
                )
            return
        }

        _state.launchWithState(
            call = { ApiClient.api.getChannelsByType(normalizedType) },
            setLoading = { s, loading -> s.copy(isLoading = loading) },
            setError = { s, err -> s.copy(error = err) },
            onSuccess = { s, body -> s.copy(channels = body.orEmpty()) },
            mapHttpError = { "Failed to filter channels (${it.code()})" },
        )
    }

    fun loadChannelsByStatus(status: Boolean) {
        _state.launchWithState(
            call = { ApiClient.api.getChannelsByStatus(status) },
            setLoading = { s, loading -> s.copy(isLoading = loading) },
            setError = { s, err -> s.copy(error = err) },
            onSuccess = { s, body -> s.copy(channels = body.orEmpty()) },
            mapHttpError = { "Failed to filter channels (${it.code()})" },
        )
    }

    fun createChannel(
        name: String,
        description: String,
        channelType: String,
        channelDescription: String,
        status: Boolean = true,
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

        _state.launchWithState(
            call = {
                ApiClient.api.createChannel(
                    body =
                        CreateChannelRequest(
                            name = name.trim(),
                            description = description.trim(),
                            channelType = normalizedType,
                            channelDesc = normalizedChannelDesc ?: "Cash",
                            status = status,
                        ),
                )
            },
            setLoading = { s, saving -> s.copy(isSaving = saving) },
            setError = { s, err -> s.copy(error = err) },
            onSuccess = { s, created ->
                if (created != null) {
                    publishEvent(StateEvent.ChannelCreated(channelId = created.id, channelName = created.name))
                }
                s.copy(
                    message = "Channel created successfully",
                    channels = if (created != null) s.channels + created else s.channels,
                )
            },
            mapHttpError = { "Failed to create channel (${it.code()})" },
        )
    }

    fun updateChannel(
        id: String,
        name: String,
        description: String,
        status: Boolean,
    ) {
        if (id.isBlank()) {
            _state.value = _state.value.copy(error = "Invalid channel id")
            return
        }

        _state.launchWithState(
            call = {
                ApiClient.api.updateChannel(
                    id = id,
                    body =
                        UpdateChannelRequest(
                            name = name.trim(),
                            description = description.trim(),
                            status = status,
                        ),
                )
            },
            setLoading = { s, saving -> s.copy(isSaving = saving) },
            setError = { s, err -> s.copy(error = err) },
            onSuccess = { s, body ->
                publishEvent(StateEvent.ChannelUpdated(channelId = id, channelName = name))
                loadChannels()
                s.copy(message = body?.message ?: "Channel updated successfully")
            },
            mapHttpError = { "Failed to update channel (${it.code()})" },
        )
    }

    fun deleteChannel(id: String) {
        if (id.isBlank()) {
            _state.value = _state.value.copy(error = "Invalid channel id")
            return
        }

        _state.launchWithState(
            call = { ApiClient.api.deleteChannel(id) },
            setLoading = { s, deleting -> s.copy(isDeleting = deleting) },
            setError = { s, err -> s.copy(error = err) },
            onSuccess = { s, body ->
                publishEvent(StateEvent.ChannelDeleted(channelId = id))
                s.copy(
                    message = body?.message ?: "Channel deleted successfully",
                    channels = s.channels.filterNot { it.id == id },
                )
            },
            mapHttpError = { "Failed to delete channel (${it.code()})" },
        )
    }

    /**
     * Toggle SMS notification flag for a channel (non-CASH only)
     */
    fun toggleSmsNotification(channelId: String) {
        viewModelScope.launch {
            try {
                val currentChannels = _state.value.channels
                val targetChannel = currentChannels.find { it.id == channelId } ?: return@launch

                if (targetChannel.channelType == "CASH") {
                    _state.value = _state.value.copy(error = "SMS notifications not available for CASH channels")
                    return@launch
                }

                val newFlag = !targetChannel.smsNotificationEnabled

                // Update local storage
                ChannelManager.updateChannelSmsNotification(channelId, newFlag)

                // Update state
                val updatedChannels =
                    currentChannels.map { channel ->
                        if (channel.id == channelId) {
                            channel.copy(smsNotificationEnabled = newFlag)
                        } else {
                            channel
                        }
                    }

                _state.value =
                    _state.value.copy(
                        channels = updatedChannels,
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
