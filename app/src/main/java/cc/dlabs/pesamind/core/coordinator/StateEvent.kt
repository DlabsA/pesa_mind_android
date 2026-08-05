package cc.dlabs.pesamind.core.coordinator

/**
 * Sealed hierarchy for all state change events across the application.
 * This enables reactive synchronization between ViewModels.
 */
sealed class StateEvent {
    // ─── Transaction Events ───────────────────────────────────────────────

    data class TransactionCreated(
        val transactionId: String,
        val amount: Double,
        val channelId: String,
    ) : StateEvent()

    data object TransactionsRefreshed : StateEvent()

    data object TransactionsLoaded : StateEvent()

    // ─── Channel Events ───────────────────────────────────────────────────

    data class ChannelCreated(
        val channelId: String,
        val channelName: String,
    ) : StateEvent()

    data class ChannelUpdated(
        val channelId: String,
        val channelName: String,
    ) : StateEvent()

    data class ChannelDeleted(
        val channelId: String,
    ) : StateEvent()

    data object ChannelsRefreshed : StateEvent()

    data object ChannelsLoaded : StateEvent()

    // ─── Analytics Events ────────────────────────────────────────────────

    data object AnalyticsRefreshed : StateEvent()

    data object AnalyticsLoaded : StateEvent()

    // ─── Dashboard Events ───────────────────────────────────────────────

    data object DashboardRefreshed : StateEvent()

    data object DashboardLoaded : StateEvent()

    // ─── Authentication Events ──────────────────────────────────────────

    data object UserLoggedIn : StateEvent()

    data object UserLoggedOut : StateEvent()

    // ─── Account Events ─────────────────────────────────────────────────

    data object AccountUpdated : StateEvent()

    // ─── Budget Events ──────────────────────────────────────────────────

    data object BudgetRefreshed : StateEvent()

    data object BudgetUpdated : StateEvent()

    // ─── Blog Events ────────────────────────────────────────────────────

    /** Published after a successful [cc.dlabs.pesamind.core.data.BlogRepository.refreshPosts]
     * — fired both from a normal screen load/refresh and from
     * [cc.dlabs.pesamind.features.blog.BlogMessagingService] after a push-triggered fetch, so
     * any open [cc.dlabs.pesamind.features.blog.BlogViewModel] picks up new content either way. */
    data object BlogPostsRefreshed : StateEvent()

    // ─── Generic Events ────────────────────────────────────────────────

    data class ErrorOccurred(
        val source: String,
        val message: String,
    ) : StateEvent()

    data object SyncRequested : StateEvent()

    /**
     * Published by [cc.dlabs.pesamind.core.sync.SyncWorker] after a push+pull cycle
     * finishes — success or partial (some rows may have transiently failed and will
     * retry on a later run, but whatever DID sync in this run is real). This is the
     * completion signal [SyncRequested] was meant to be but that nothing ever publishes.
     */
    data object SyncCompleted : StateEvent()
}
