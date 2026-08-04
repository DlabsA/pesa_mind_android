package cc.dlabs.pesamind.core.utils

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.TransactionInsertOutcome
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.data.details
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TransactionState(
    val transactions: List<TransactionDetails> = emptyList(),
    val isLoading: Boolean = false,
    val isRefresh: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

sealed class TransactionCreationResult {
    // transaction is nullable because a 2xx response with an empty/unparseable body still
    // counts as success (matches pre-refactor behavior) — the caller just has nothing to
    // append to its local list or attach a real id to. wasDuplicate is true when the atomic
    // dedup check (smsSourceKey / providerTransactionId) discarded this as a repeat of an
    // already-created row rather than inserting a new one — SMSMessageProcessor uses this to
    // log a discarded duplicate instead of treating it as a fresh transaction.
    data class Success(val transaction: TransactionDetails?, val wasDuplicate: Boolean = false) : TransactionCreationResult()

    data class Failure(val message: String) : TransactionCreationResult()
}

/**
 * Room-backed (ADR-0004 Slice A1) — reads/writes go through [TransactionRepository], never
 * `ApiClient`/`TransactionManager` directly. `TransactionManager`'s DataStore cache was never
 * actually live in production (see ADR-0004's Step 0 verification: its `init()` is never
 * called from `PesaMindApp.onCreate()`), so this isn't just a rewire — it's the first time
 * this cache has ever functioned. [state]'s `transactions` list is kept live by the
 * [TransactionRepository.observeTransactions] collector below; [performCreateTransaction]
 * doesn't need to splice its own result in by hand.
 */
class TransactionViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(TransactionState(isLoading = true))
    val state: StateFlow<TransactionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                TransactionRepository.observeTransactions().collect { transactions ->
                    _state.value = _state.value.copy(transactions = transactions, isLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to load transactions: ${e.message}")
            }
        }
    }

    override fun onStateEvent(event: StateEvent) {
        when (event) {
            is StateEvent.UserLoggedOut -> {
                _state.value =
                    _state.value.copy(
                        transactions = emptyList(),
                        error = null,
                        isLoading = false,
                    )
            }
            else -> {}
        }
    }

    /** One-shot re-read, kept for existing pull-to-refresh call sites. The `getAllTransactions()`
     * call itself is a Room read, not a network call — freshness against the server comes from
     * [SyncScheduler.triggerSyncNow] below, whose pull writes back into Room and reaches this
     * screen via [TransactionRepository.observeTransactions] once it completes. */
    fun loadTransactions() {
        SyncScheduler.triggerSyncNow()
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val transactions = TransactionRepository.getAllTransactions()
            _state.value = _state.value.copy(isLoading = false, transactions = transactions)
        }
    }

    fun refresh() = loadTransactions()

    /**
     * Best-effort background pull scoped to a date range (`yyyy-MM-dd`), fired when the
     * transaction list's date-range filter is applied — a network gap-filler, not a loading
     * gate: the screen's local filter over [state]'s already-live [TransactionState.transactions]
     * (via [TransactionRepository.observeTransactions]) renders immediately regardless of this
     * call's outcome, so a failure here (e.g. offline) is only logged, never surfaced as [error].
     */
    fun refreshDateRange(
        startDate: String,
        endDate: String,
    ) {
        viewModelScope.launch {
            try {
                TransactionRepository.refreshByDateRange(startDate, endDate)
            } catch (e: Exception) {
                Log.w("TransactionViewModel", "refreshByDateRange failed for $startDate..$endDate", e)
            }
        }
    }

    fun createTransaction(
        channelID: String,
        amount: Double,
        type: String,
        note: String,
    ) {
        viewModelScope.launch { createTransactionAwaited(channelID, amount, type, note) }
    }

    /**
     * Suspending, awaited variant for non-UI callers (e.g. [SMSMessageProcessor]) that must
     * know the real outcome before acting — the fire-and-forget [createTransaction] above
     * delegates here on its own coroutine and discards the result, since it only needs the
     * `_state` side effect. This is the single place that owns the isSaving/message/error
     * transitions for a transaction creation.
     *
     * [smsSourceKey]/[providerTransactionId] are null for every UI-driven call
     * ([AddTransactionScreen] has no SMS to derive them from) and populated only by
     * [SMSMessageProcessor] — see [TransactionRepository.createTransaction]'s doc comment for
     * what they dedup against.
     */
    suspend fun createTransactionAwaited(
        channelID: String,
        amount: Double,
        type: String,
        note: String,
        smsSourceKey: String? = null,
        providerTransactionId: String? = null,
    ): TransactionCreationResult {
        val validationError = validateTransactionInput(channelID, amount, type, note)
        if (validationError != null) {
            _state.value = _state.value.copy(error = validationError)
            return TransactionCreationResult.Failure(validationError)
        }

        _state.value = _state.value.copy(isSaving = true, error = null)
        val result = performCreateTransaction(channelID, amount, type, note, smsSourceKey, providerTransactionId)
        _state.value =
            when (result) {
                is TransactionCreationResult.Success ->
                    _state.value.copy(isSaving = false, message = "Transaction created successfully")
                is TransactionCreationResult.Failure ->
                    _state.value.copy(isSaving = false, error = result.message)
            }
        return result
    }

    private fun validateTransactionInput(
        channelID: String,
        amount: Double,
        type: String,
        note: String,
    ): String? =
        when {
            channelID.isBlank() -> "Channel ID is required"
            amount <= 0 -> "Amount must be greater than zero"
            TransactionTypes.normalizeOrNull(type) == null ->
                "Invalid transaction type. Use: ${TransactionTypes.valid.joinToString()}"
            note.isBlank() -> "Note is required"
            else -> null
        }

    private suspend fun performCreateTransaction(
        channelID: String,
        amount: Double,
        type: String,
        note: String,
        smsSourceKey: String? = null,
        providerTransactionId: String? = null,
    ): TransactionCreationResult {
        val normalizedType =
            TransactionTypes.normalizeOrNull(type)
                ?: return TransactionCreationResult.Failure(
                    "Invalid transaction type. Use: ${TransactionTypes.valid.joinToString()}",
                )
        return try {
            val outcome =
                TransactionRepository.createTransaction(
                    channelId = channelID,
                    amount = amount,
                    type = normalizedType,
                    note = note.trim(),
                    username = currentUsername(),
                    smsSourceKey = smsSourceKey,
                    providerTransactionId = providerTransactionId,
                )
            if (outcome is TransactionInsertOutcome.Inserted) {
                // Trigger a real SyncWorker run (push-then-pull) so StateEvent.SyncCompleted
                // is genuinely published as a consequence of this create — Dashboard/Analytics
                // ViewModels treat SyncCompleted as the accurate correction after this event's
                // own (possibly-stale) refresh below. Safe to call unconditionally: the
                // WorkRequest carries a NetworkType.CONNECTED constraint, so it's a no-op until
                // connectivity exists, and repeated calls coalesce via ExistingWorkPolicy.KEEP.
                SyncScheduler.triggerSyncNow()

                // 🔥 Publish event so Dashboard and Analytics refresh automatically — a
                // discarded duplicate must not re-publish a create event for a row nothing new
                // actually happened to.
                Log.d("TransactionViewModel", "📢 Publishing TransactionCreated event...")
                publishEvent(
                    StateEvent.TransactionCreated(
                        transactionId = outcome.transaction.id,
                        amount = amount,
                        channelId = channelID,
                    ),
                )
            }
            TransactionCreationResult.Success(outcome.details(), wasDuplicate = outcome is TransactionInsertOutcome.DuplicateDiscarded)
        } catch (e: Exception) {
            Log.e("TransactionViewModel", "❌ Exception saving transaction locally", e)
            TransactionCreationResult.Failure("Could not save transaction: ${e.message ?: "Unknown error"}")
        }
    }

    private suspend fun currentUsername(): String =
        try {
            AccountManager.getAccount().username
        } catch (e: Exception) {
            ""
        }

    /**
     * Clear error and message states (called when user navigates away)
     */
    fun clearMessages() {
        _state.value = _state.value.copy(error = null, message = null)
    }
}
