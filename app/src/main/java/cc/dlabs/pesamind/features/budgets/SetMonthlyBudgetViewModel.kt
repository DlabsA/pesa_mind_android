package cc.dlabs.pesamind.features.budgets

import android.util.Log
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.BudgetRepository
import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── Transaction types ────────────────────────────────────────────────────────

object TransactionType {
    const val INCOME = "income"
    const val EXPENSE = "expense"
    const val SAVING = "saving"

    val all = listOf(INCOME, EXPENSE, SAVING)

    fun displayName(type: String) =
        when (type) {
            INCOME -> "Income"
            EXPENSE -> "Expenditure"
            SAVING -> "Savings"
            else -> type.replaceFirstChar { it.uppercase() }
        }
}

// ─── UI State ─────────────────────────────────────────────────────────────────

data class SetMonthlyBudgetUiState(
    // Period
    val month: Int = 0,
    val year: Int = 0,
    // Budget data
    val budget: MonthlyBudgetResponse? = null,
    val yearlyBudgetId: String = "",
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

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * Room-backed (ADR-0004 Slice B) — reads/writes go through [BudgetRepository], never
 * `ApiClient`/`BudgetManager` directly. `state.budget` is kept live by the
 * [BudgetRepository.observeMonthlyBudget] collector below, same "Room write-then-Flow-reemit"
 * pattern as [YearlyBudgetViewModel]/`ChannelViewModel`.
 */
class SetMonthlyBudgetViewModel() : UnifiedViewModel() {
    private val _state = MutableStateFlow(SetMonthlyBudgetUiState())
    val state: StateFlow<SetMonthlyBudgetUiState> = _state.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(
        month: Int,
        year: Int,
    ) {
        _state.update { it.copy(month = month, year = year, isLoading = true) }
        viewModelScope.launch {
            try {
                BudgetRepository.observeMonthlyBudget(month, year.toLong()).collect { budget ->
                    _state.update {
                        it.copy(
                            budget = budget,
                            yearlyBudgetId = budget?.yearlyBudgetId ?: "",
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SetMonthlyBudgetVM", "Failed to observe budget for $month/$year", e)
                _state.update { it.copy(isLoading = false, error = "Error: ${e.message}") }
            }
        }
    }

    // ── Form field updates ────────────────────────────────────────────────────

    fun onNameChange(v: String) =
        _state.update {
            it.copy(formName = v, formNameError = null)
        }

    fun onAmountChange(v: String) {
        // Only allow digits and a single decimal point
        val cleaned = v.filter { c -> c.isDigit() || c == '.' }
        val dotCount = cleaned.count { it == '.' }
        if (dotCount <= 1) {
            _state.update { it.copy(formAmount = cleaned, formAmountError = null) }
        }
    }

    fun onTypeChange(v: String) = _state.update { it.copy(formType = v) }

    // ── Add transaction ───────────────────────────────────────────────────────

    fun addTransaction() {
        val s = _state.value

        // Validate
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
                BudgetRepository.addMonthlyTransaction(s.month, s.year.toLong(), s.formName.trim(), amount!!, s.formType)
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
                Log.e("SetMonthlyBudgetVM", "Failed to add transaction", e)
                _state.update {
                    it.copy(isAddingTransaction = false, error = e.message ?: "Failed to add transaction")
                }
            }
        }
    }

    // ── Delete transaction ────────────────────────────────────────────────────

    fun confirmDeleteTransaction(tx: BudgetTransactionResponse) = _state.update { it.copy(pendingDeleteTx = tx) }

    fun cancelDeleteTransaction() = _state.update { it.copy(pendingDeleteTx = null) }

    fun deleteTransaction() {
        val tx = _state.value.pendingDeleteTx ?: return
        val s = _state.value

        _state.update { it.copy(pendingDeleteTx = null, isDeletingTransactionId = tx.id) }

        viewModelScope.launch {
            try {
                BudgetRepository.deleteMonthlyTransaction(s.month, s.year.toLong(), tx.id)
                _state.update {
                    it.copy(isDeletingTransactionId = null, message = "${tx.name} removed")
                }
            } catch (e: Exception) {
                Log.e("SetMonthlyBudgetVM", "Failed to delete transaction", e)
                _state.update {
                    it.copy(isDeletingTransactionId = null, error = "Failed to delete transaction")
                }
            }
        }
    }

    // ── Feedback reset ────────────────────────────────────────────────────────

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearError() = _state.update { it.copy(error = null) }
}
