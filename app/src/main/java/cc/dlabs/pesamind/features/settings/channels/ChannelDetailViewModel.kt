package cc.dlabs.pesamind.features.settings.channels

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.ChannelRepository
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.network.models.ChannelDetails
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelDetailState(
    val channel: ChannelDetails? = null,
    val transactions: List<TransactionDetails> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val error: String? = null,
    val channelDeleted: Boolean = false,
)

/**
 * Backs `ChannelDetailScreen` — one channel's card plus its transaction history
 * (`GET /transactions/by-channel`). Room-first, same as [ChannelViewModel]/`TransactionViewModel`:
 * [TransactionRepository.observeByChannel] renders instantly from the local, already-synced
 * cache, while [TransactionRepository.refreshByChannel] reconciles fresh server rows into Room
 * in the background — there's no direct network-to-UI path here.
 */
class ChannelDetailViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(ChannelDetailState())
    val state: StateFlow<ChannelDetailState> = _state.asStateFlow()

    private var channelId: String? = null

    fun load(channelId: String) {
        this.channelId = channelId
        _state.value = _state.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            _state.value = _state.value.copy(channel = ChannelRepository.getById(channelId))
        }

        viewModelScope.launch {
            TransactionRepository.observeByChannel(channelId).collect { transactions ->
                _state.value = _state.value.copy(transactions = transactions, isLoading = false)
            }
        }

        viewModelScope.launch {
            try {
                TransactionRepository.refreshByChannel(channelId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Couldn't refresh from server: ${e.message}")
            }
        }
    }

    fun deleteChannel() {
        val id = channelId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isDeleting = true, error = null)
            val deleted = ChannelRepository.deleteChannel(id)
            _state.value =
                if (deleted) {
                    publishEvent(StateEvent.ChannelDeleted(channelId = id))
                    _state.value.copy(isDeleting = false, channelDeleted = true)
                } else {
                    _state.value.copy(isDeleting = false, error = "Channel not found")
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onStateEvent(event: StateEvent) {
        val id = channelId ?: return
        when {
            event is StateEvent.ChannelUpdated && event.channelId == id ->
                viewModelScope.launch {
                    _state.value = _state.value.copy(channel = ChannelRepository.getById(id))
                }
            else -> Unit
        }
    }
}
