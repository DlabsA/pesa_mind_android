package cc.dlabs.pesamind.features.blog

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.BlogRepository
import cc.dlabs.pesamind.core.database.entity.BlogPostEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlogUiState(
    val posts: List<BlogPostEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
)

/**
 * Backs [BlogScreen] — Room-first read of admin-authored posts (see
 * [cc.dlabs.pesamind.core.database.entity.BlogPostEntity]'s doc comment), refreshed from the
 * backend on load/pull-to-refresh and whenever [BlogMessagingService] signals new content via
 * [StateEvent.BlogPostsRefreshed] (that event doesn't need its own handling here beyond the
 * no-op below — [BlogRepository.refreshPosts] already wrote to Room by the time it's
 * published, and the live [BlogRepository.observePosts] collection below picks it up
 * automatically). [BlogRepository.refreshPosts] swallows its own network failures (same
 * fail-soft convention as [cc.dlabs.pesamind.core.data.ChannelRepository]/
 * [cc.dlabs.pesamind.core.data.TransactionRepository]'s background refreshes) — there is no
 * separate error state to surface here, only however many posts are already cached locally.
 */
@HiltViewModel
class BlogViewModel
    @Inject
    constructor() : UnifiedViewModel() {
        private val _state = MutableStateFlow(BlogUiState())
        val state: StateFlow<BlogUiState> = _state.asStateFlow()

        private var started = false

        fun load() {
            if (started) return
            started = true
            _state.value = _state.value.copy(isLoading = true)
            viewModelScope.launch {
                BlogRepository.observePosts().collect { posts ->
                    _state.value = _state.value.copy(posts = posts, isLoading = false)
                }
            }
            refresh()
        }

        fun refresh() {
            if (_state.value.isRefreshing) return
            viewModelScope.launch {
                _state.value = _state.value.copy(isRefreshing = true)
                BlogRepository.refreshPosts()
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }

        fun markRead(id: String) {
            viewModelScope.launch { BlogRepository.markRead(id) }
        }

        override fun onStateEvent(event: StateEvent) {
            // No-op: BlogPostsRefreshed exists so BlogMessagingService (not a ViewModel, so it
            // has no publishEvent() helper of its own) has a StateEvent to publish through
            // UnifiedStateCoordinator directly — the live Room collection in load() above
            // already reflects new rows without needing to react to this event specifically.
        }
    }
