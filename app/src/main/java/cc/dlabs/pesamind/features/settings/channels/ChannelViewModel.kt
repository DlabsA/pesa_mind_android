package cc.dlabs.pesamind.features.settings.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.CreateChannelRequest
import cc.dlabs.pesamind.core.network.models.UpdateChannelRequest
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
    val message: String? = null
)

class ChannelViewModel : ViewModel() {

    private val _state = MutableStateFlow(ChannelState(isLoading = true))
    val state: StateFlow<ChannelState> = _state.asStateFlow()

    init {
        loadChannels()
    }

    fun loadChannels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = ApiClient.api.getChannels()
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        channels = response.body().orEmpty(),
                        error = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load channels (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun loadChannelsByType(channelType: String) {
        val normalizedType = ChannelTypes.normalizeOrNull(channelType)
        if (normalizedType == null) {
            _state.value = _state.value.copy(
                error = "Invalid channel type. Use: ${ChannelTypes.valid.joinToString()}"
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = ApiClient.api.getChannelsByType(normalizedType)
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        channels = response.body().orEmpty()
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to filter channels (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun loadChannelsByStatus(status: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = ApiClient.api.getChannelsByStatus(status)
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        channels = response.body().orEmpty()
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to filter channels (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun createChannel(
        name: String,
        description: String,
        channelType: String,
        status: Boolean = true
    ) {
        val normalizedType = ChannelTypes.normalizeOrNull(channelType)
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
                _state.value = _state.value.copy(
                    error = "Invalid channel type. Use: ${ChannelTypes.valid.joinToString()}"
                )
                return
            }
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val response = ApiClient.api.createChannel(
                    CreateChannelRequest(
                        name = name.trim(),
                        description = description.trim(),
                        channelType = normalizedType,
                        status = status
                    )
                )

                if (response.isSuccessful) {
                    val created = response.body()
                    _state.value = _state.value.copy(
                        isSaving = false,
                        message = "Channel created successfully",
                        channels = if (created != null) _state.value.channels + created else _state.value.channels
                    )
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = "Failed to create channel (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun updateChannel(
        id: String,
        name: String,
        description: String,
        status: Boolean
    ) {
        if (id.isBlank()) {
            _state.value = _state.value.copy(error = "Invalid channel id")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val response = ApiClient.api.updateChannel(
                    id = id,
                    body = UpdateChannelRequest(
                        name = name.trim(),
                        description = description.trim(),
                        status = status
                    )
                )

                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        message = response.body()?.message ?: "Channel updated successfully"
                    )
                    loadChannels()
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = "Failed to update channel (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun deleteChannel(id: String) {
        if (id.isBlank()) {
            _state.value = _state.value.copy(error = "Invalid channel id")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isDeleting = true, error = null)
            try {
                val response = ApiClient.api.deleteChannel(id)
                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        isDeleting = false,
                        message = response.body()?.message ?: "Channel deleted successfully",
                        channels = _state.value.channels.filterNot { it.id == id }
                    )
                } else {
                    _state.value = _state.value.copy(
                        isDeleting = false,
                        error = "Failed to delete channel (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isDeleting = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
