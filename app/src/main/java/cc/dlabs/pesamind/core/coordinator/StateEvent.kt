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

    // ─── Subscription Events ───────────────────────────────────────────

    /**
     * Published by [cc.dlabs.pesamind.features.subscription.SubscriptionViewModel]
     * once an invoice reaches `paid` and the new tier has been written to
     * [cc.dlabs.pesamind.core.storage.AccountManager].
     *
     * Every ViewModel that gates on `isPremium` reads the tier once in `init` and
     * never again, and the only other thing that refreshes it mid-session is a JWT
     * refresh. Without this event a user would pay and watch nothing unlock.
     */
    data object SubscriptionActivated : StateEvent()

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
