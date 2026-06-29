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
    
    // ─── Generic Events ────────────────────────────────────────────────
    
    data class ErrorOccurred(
        val source: String,
        val message: String,
    ) : StateEvent()
    
    data object SyncRequested : StateEvent()
}

