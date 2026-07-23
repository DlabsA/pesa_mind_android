package cc.dlabs.pesamind.core.utils

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.TransactionRepository
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.storage.AccountManager
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
    // append to its local list or attach a real id to.
    data class Success(val transaction: TransactionDetails?) : TransactionCreationResult()

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

    /** One-shot re-read, kept for existing pull-to-refresh call sites. Local data is already
     * live via [TransactionRepository.observeTransactions] — this is a Room read, not a
     * network call; there is no sync worker to trigger yet (ADR-0004 Slice A2). */
    fun loadTransactions() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val transactions = TransactionRepository.getAllTransactions()
            _state.value = _state.value.copy(isLoading = false, transactions = transactions)
        }
    }

    fun refresh() = loadTransactions()

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
     */
    suspend fun createTransactionAwaited(
        channelID: String,
        amount: Double,
        type: String,
        note: String,
    ): TransactionCreationResult {
        val validationError = validateTransactionInput(channelID, amount, type, note)
        if (validationError != null) {
            _state.value = _state.value.copy(error = validationError)
            return TransactionCreationResult.Failure(validationError)
        }

        _state.value = _state.value.copy(isSaving = true, error = null)
        val result = performCreateTransaction(channelID, amount, type, note)
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
    ): TransactionCreationResult {
        val normalizedType =
            TransactionTypes.normalizeOrNull(type)
                ?: return TransactionCreationResult.Failure(
                    "Invalid transaction type. Use: ${TransactionTypes.valid.joinToString()}",
                )
        return try {
            val created =
                TransactionRepository.createTransaction(
                    channelId = channelID,
                    amount = amount,
                    type = normalizedType,
                    note = note.trim(),
                    username = currentUsername(),
                )
            // 🔥 Publish event so Dashboard and Analytics refresh automatically
            Log.d("TransactionViewModel", "📢 Publishing TransactionCreated event...")
            publishEvent(
                StateEvent.TransactionCreated(
                    transactionId = created.id,
                    amount = amount,
                    channelId = channelID,
                ),
            )
            TransactionCreationResult.Success(created)
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
