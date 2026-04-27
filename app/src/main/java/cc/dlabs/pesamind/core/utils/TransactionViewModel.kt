package cc.dlabs.pesamind.core.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null
)


class TransactionViewModel : ViewModel() {
    private val _state = MutableStateFlow(TransactionState(isLoading = true))
    val state: StateFlow<TransactionState> = _state.asStateFlow()

    init {
        loadTransactions()
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
                }
                val response = ApiClient.api.getTransactions()
                if (response.isSuccessful) {
                    val transactions = response.body().orEmpty()
                    TransactionManager.saveTransactions(transactions)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        transactions = transactions,
                        error = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Failed to load transactions (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Error loading transactions: ${e.message}"
                )
            }
        }
    }

    public fun CreateTransaction(
        channelID: String,
        amount: Double,
        type: String,
        note: String,
    ){
        val normalizedType = TransactionTypes.normalizeOrNull(type)

        when{
            channelID.isBlank() -> {
                _state.value = _state.value.copy(error = "Channel ID is required")
                return
            }
            amount <= 0 -> {
                _state.value = _state.value.copy(error = "Amount must be greater than zero")
                return
            }
            normalizedType == null -> {
                _state.value = _state.value.copy(
                    error = "Invalid transaction type. Use: ${TransactionTypes.valid.joinToString()}"
                )
                return
            }
            note.isBlank() -> {
                _state.value = _state.value.copy(error = "Note is required")
                return
            }
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val response = ApiClient.api.createTransaction(
                    TransactionRequest(
                        channelId = channelID,
                        amount = amount,
                        type = normalizedType,
                        note = note.trim(),
                    )
                )
                if (response.isSuccessful) {
                    val created = response.body()
                    _state.value = _state.value.copy(
                        isSaving = false,
                        message = "Transaction created successfully",
                        transactions = if (created != null) _state.value.transactions + created else _state.value.transactions
                    )
                } else {
                    _state.value = _state.value.copy(
                        isSaving = false,
                        error = "Failed to create transaction (${response.code()})"
                    )
                }
            }
            catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = "Cannot reach server: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }
}
