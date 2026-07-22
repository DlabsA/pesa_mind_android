package cc.dlabs.pesamind.core.utils

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.StateEvent
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.TransactionDetails
import cc.dlabs.pesamind.core.network.models.TransactionRequest
import cc.dlabs.pesamind.core.storage.TransactionManager
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

class TransactionViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(TransactionState(isLoading = true))
    val state: StateFlow<TransactionState> = _state.asStateFlow()

    init {
        loadTransactions()
    }

    fun refresh() = loadTransactions()

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

    /**
     * Load transactions: first from local cache, then sync with backend
     */
    fun loadTransactions() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val cachedTransactions = TransactionManager.getTransactions()
                if (cachedTransactions.isNotEmpty()) {
                    _state.value = _state.value.copy(transactions = cachedTransactions)

                    // Only sync automatically when cache is older than policy window.
                    if (!TransactionManager.isCacheStale()) {
                        _state.value = _state.value.copy(isLoading = false, error = null)
                        return@launch
                    }
                }
                val response = ApiClient.api.getTransactions()
                if (response.isSuccessful) {
                    val transactions = response.body().orEmpty()
                    TransactionManager.saveTransactions(transactions)
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            transactions = transactions,
                            error = null,
                        )
                } else {
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            error = "Failed to load transactions (${response.code()})",
                        )
                }
            } catch (e: Exception) {
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error = "Error loading transactions: ${e.message}",
                    )
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
            val response =
                ApiClient.api.createTransaction(
                    TransactionRequest(
                        channelId = channelID,
                        amount = amount,
                        type = normalizedType,
                        note = note.trim(),
                    ),
                )
            if (response.isSuccessful) {
                val created = response.body()
                if (created != null) {
                    _state.value = _state.value.copy(transactions = _state.value.transactions + created)
                }
                // 🔥 Publish event so Dashboard and Analytics refresh automatically
                Log.d("TransactionViewModel", "📢 Publishing TransactionCreated event...")
                publishEvent(
                    StateEvent.TransactionCreated(
                        transactionId = created?.id ?: "",
                        amount = amount,
                        channelId = channelID,
                    ),
                )
                TransactionCreationResult.Success(created)
            } else {
                Log.e("TransactionViewModel", "❌ Failed to create transaction: ${response.code()}")
                TransactionCreationResult.Failure("Failed to create transaction (${response.code()})")
            }
        } catch (e: Exception) {
            Log.e("TransactionViewModel", "❌ Exception during transaction creation", e)
            TransactionCreationResult.Failure("Cannot reach server: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Clear error and message states (called when user navigates away)
     */
    fun clearMessages() {
        _state.value = _state.value.copy(error = null, message = null)
    }
}
