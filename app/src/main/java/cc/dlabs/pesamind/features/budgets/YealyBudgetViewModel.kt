package cc.dlabs.pesamind.features.budgets

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.YearlyBudgetRepository
import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class YearlyBudgetUiState(
    // Period
    val month: Int = 0,
    val year: Int = 0,
    // Budget data
    val budget: YearlyBudgetResponse? = null,
    // Loading / saving
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isAddingTransaction: Boolean = false,
    val isDeletingTransactionId: String? = null,
    // Add-transaction form
    val formName: String = "",
    val formAmount: String = "",
    val formType: String = TransactionType.INCOME,
    val formNameError: String? = null,
    val formAmountError: String? = null,
    // UI feedback
    val message: String? = null,
    val error: String? = null,
    // Confirmation delete
    val pendingDeleteTx: BudgetTransactionResponse? = null,
) {
    val transactions: List<BudgetTransactionResponse>
        get() = budget?.transactions ?: emptyList()

    val totalIncome: Long
        get() = budget?.totalIncome ?: 0L

    val totalExpenditures: Long
        get() = budget?.totalExpenditures ?: 0L

    val totalSavings: Long
        get() = budget?.totalSavings ?: 0L

    val balance: Long get() = totalIncome - totalExpenditures
    val isDeficit: Boolean get() = balance < 0L

    val isFormValid: Boolean
        get() =
            formName.isNotBlank() &&
                formAmount.isNotBlank() &&
                formAmount.toDoubleOrNull() != null &&
                (formAmount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                formType.isNotBlank()
}

/**
 * Room-backed (ADR-0006) — reads/writes go through [YearlyBudgetRepository], never
 * `ApiClient`/`BudgetManager` directly. See [SetMonthlyBudgetViewModel]'s doc comment for the
 * shared design — `budget` in [state] is kept live by
 * [YearlyBudgetRepository.observeYearlyBudget], not spliced in by hand after a mutation.
 */
class YearlyBudgetViewModel() : UnifiedViewModel() {
    private val _state = MutableStateFlow(YearlyBudgetUiState())
    val state: StateFlow<YearlyBudgetUiState> = _state.asStateFlow()

    fun init(year: Int) {
        _state.update { it.copy(year = year, isLoading = true) }
        observeBudget(year)
    }

    private fun observeBudget(year: Int) {
        viewModelScope.launch {
            try {
                YearlyBudgetRepository.observeYearlyBudgetSnapshot(year.toLong()).collect { snapshot ->
                    _state.update { it.copy(budget = snapshot?.details, isLoading = false, error = null) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = if (it.budget == null) "Error: ${e.message}" else null) }
            }
        }
    }

    // Refresh budget data — one-shot Room re-read, kept for the pull-to-refresh UI action; the
    // live Flow in observeBudget already keeps state current, same role as
    // ChannelViewModel/TransactionViewModel.refresh().
    fun refresh() {
        val year = _state.value.year
        if (year <= 0) return
        viewModelScope.launch {
            val budget = YearlyBudgetRepository.getYearlyBudget(year.toLong())
            _state.update { it.copy(budget = budget) }
        }
    }

    fun onNameChange(v: String) =
        _state.update {
            it.copy(formName = v, formNameError = null)
        }

    fun onAmountChange(v: String) {
        val cleaned = v.filter { c -> c.isDigit() || c == '.' }
        val dotCount = cleaned.count { it == '.' }
        if (dotCount <= 1) {
            _state.update { it.copy(formAmount = cleaned, formAmountError = null) }
        }
    }

    fun onTypeChange(v: String) = _state.update { it.copy(formType = v) }

    fun addTransaction() {
        val s = _state.value

        var nameErr: String? = null
        var amountErr: String? = null
        if (s.formName.isBlank()) nameErr = "Name is required"
        val amount = s.formAmount.toDoubleOrNull()
        if (amount == null || amount <= 0.0) amountErr = "Enter a valid amount"

        if (nameErr != null || amountErr != null) {
            _state.update { it.copy(formNameError = nameErr, formAmountError = amountErr) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isAddingTransaction = true) }
            try {
                YearlyBudgetRepository.addLineItem(s.year.toLong(), s.formName.trim(), amount!!, s.formType)
                _state.update {
                    it.copy(
                        isAddingTransaction = false,
                        message = "Transaction added",
                        formName = "",
                        formAmount = "",
                        formType = TransactionType.INCOME,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isAddingTransaction = false,
                        error = "Failed to add transaction: ${e.message}",
                    )
                }
            }
        }
    }

    fun confirmDeleteTransaction(tx: BudgetTransactionResponse) = _state.update { it.copy(pendingDeleteTx = tx) }

    fun cancelDeleteTransaction() = _state.update { it.copy(pendingDeleteTx = null) }

    fun deleteTransaction() {
        val tx = _state.value.pendingDeleteTx ?: return
        val year = _state.value.year

        _state.update { it.copy(pendingDeleteTx = null, isDeletingTransactionId = tx.id) }

        viewModelScope.launch {
            try {
                YearlyBudgetRepository.deleteLineItem(year.toLong(), tx.id)
                _state.update {
                    it.copy(
                        isDeletingTransactionId = null,
                        message = "${tx.name} removed",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isDeletingTransactionId = null, error = "Failed to delete transaction")
                }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearError() = _state.update { it.copy(error = null) }
}
