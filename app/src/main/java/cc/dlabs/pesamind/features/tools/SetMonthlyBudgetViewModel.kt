package cc.dlabs.pesamind.features.tools

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient.api
import cc.dlabs.pesamind.core.network.models.BudgetTransactionOperation
import cc.dlabs.pesamind.core.network.models.BudgetTransactionRequest
import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import cc.dlabs.pesamind.core.network.models.CreateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.MonthlyBudgetResponse
import cc.dlabs.pesamind.core.network.models.UpdateMonthlyBudgetRequest
import cc.dlabs.pesamind.core.storage.BudgetManager
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

    fun displayName(type: String) = when (type) {
        INCOME  -> "Income"
        EXPENSE -> "Expenditure"
        SAVING  -> "Savings"
        else    -> type.replaceFirstChar { it.uppercase() }
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
    val pendingDeleteTx: BudgetTransactionResponse? = null
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
        get() = formName.isNotBlank() &&
                formAmount.isNotBlank() &&
                formAmount.toDoubleOrNull() != null &&
                (formAmount.toDoubleOrNull() ?: 0.0) > 0.0 &&
                formType.isNotBlank()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class SetMonthlyBudgetViewModel() : ViewModel() {

    private val _state = MutableStateFlow(SetMonthlyBudgetUiState())
    val state: StateFlow<SetMonthlyBudgetUiState> = _state.asStateFlow()

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(month: Int, year: Int) {
        _state.update { it.copy(month = month, year = year) }
        loadBudget(month, year)
    }

    // ── Load existing budget (or prepare for creation) ────────────────────────

    private fun loadBudget(month: Int, year: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Try cache first
            val cached = BudgetManager.getMonthlyBudgetByMonthYear(month, year.toLong())
            if (cached != null) {
                _state.update { it.copy(budget = cached, yearlyBudgetId = cached.yearlyBudgetId) }
            }

            // Fetch from network
            try {
                val response = api.getMonthlyBudgetByMonthYear(month, year.toLong())
                if (response.isSuccessful) {
                    val budget = response.body()
                    if (budget != null) {
                        BudgetManager.saveCurrentMonthlyBudget(budget)
                        _state.update {
                            it.copy(
                                budget = budget,
                                yearlyBudgetId = budget.yearlyBudgetId,
                                isLoading = false
                            )
                        }
                    } else {
                        // Budget doesn't exist yet — resolve yearly budget id
                        resolveYearlyBudgetId(year)
                        _state.update { it.copy(isLoading = false) }
                    }
                } else {
                    resolveYearlyBudgetId(year)
                    _state.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (cached == null) "Couldn't load budget data" else null
                    )
                }
            }
        }
    }

    private suspend fun resolveYearlyBudgetId(year: Int) {
        // Look up cached yearly budget
        val cached = BudgetManager.getYearlyBudgetByYear(year.toLong())
        if (cached != null) {
            _state.update { it.copy(yearlyBudgetId = cached.id) }
            return
        }
        // Fallback: fetch all yearly budgets
        try {
            val response = api.getYearlyBudgets()
            if (response.isSuccessful) {
                val match = response.body()?.find { it.year == year.toLong() }
                if (match != null) {
                    BudgetManager.saveYearlyBudgets(response.body()!!)
                    _state.update { it.copy(yearlyBudgetId = match.id) }
                }
            }
        } catch (_: Exception) {}
    }
    // ── Form field updates ────────────────────────────────────────────────────

    fun onNameChange(v: String) = _state.update {
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
            val tx = BudgetTransactionRequest(
                name = s.formName.trim(),
                amount = amount!!,
                type = s.formType
            )

            try {
                if (s.budget == null) {
                    // No budget exists yet — create one with this first transaction
                    createBudgetWithTransaction(tx)
                } else {
                    // Budget exists — patch with transaction_ops
                    patchBudgetAddTransaction(s.budget.id, tx)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isAddingTransaction = false,
                        error = "Failed to add transaction"
                    )
                }
            }
        }
    }

    private suspend fun createBudgetWithTransaction(tx: BudgetTransactionRequest) {
        val s = _state.value
        if (s.yearlyBudgetId.isBlank()) {
            _state.update {
                it.copy(
                    isAddingTransaction = false,
                    error = "No yearly budget found for ${s.year}. Create one first."
                )
            }
            return
        }

        val body = CreateMonthlyBudgetRequest(
            yearlyBudgetId = s.yearlyBudgetId,
            month = s.month,
            year = s.year.toLong(),
            transactions = listOf(tx)
        )
        val response = api.createMonthlyBudget(body)
        if (response.isSuccessful) {
            val created = response.body()!!
            BudgetManager.saveCurrentMonthlyBudget(created)
            _state.update {
                it.copy(
                    budget = created,
                    yearlyBudgetId = created.yearlyBudgetId,
                    isAddingTransaction = false,
                    message = "Transaction added",
                    formName = "",
                    formAmount = "",
                    formType = TransactionType.INCOME
                )
            }
        } else {
            _state.update {
                it.copy(isAddingTransaction = false, error = "Failed to create budget")
            }
        }
    }

    private suspend fun patchBudgetAddTransaction(budgetId: String, tx: BudgetTransactionRequest) {
        val s = _state.value

        val body = UpdateMonthlyBudgetRequest(
            yearlyBudgetId = s.yearlyBudgetId,
            transactionOps = listOf(
                BudgetTransactionOperation(
                    name = tx.name,
                    amount = tx.amount,
                    type = tx.type,
                    action = "add"
                )
            )
        )
        val response = api.updateMonthlyBudget(budgetId, body)
        if (response.isSuccessful) {
            val updated = response.body()!!
            Log.d("Budget", "Updated: $body")
            Log.d("Budget", "Updated: ${response.body()}")
            BudgetManager.saveCurrentMonthlyBudget(updated)
            _state.update {
                it.copy(
                    budget = updated,
                    isAddingTransaction = false,
                    message = "Transaction added",
                    formName = "",
                    formAmount = "",
                    formType = TransactionType.INCOME
                )
            }
        } else {
            _state.update {
                it.copy(isAddingTransaction = false, error = "Failed to add transaction")
            }
        }
    }

    // ── Delete transaction ────────────────────────────────────────────────────

    fun confirmDeleteTransaction(tx: BudgetTransactionResponse) =
        _state.update { it.copy(pendingDeleteTx = tx) }

    fun cancelDeleteTransaction() =
        _state.update { it.copy(pendingDeleteTx = null) }

    fun deleteTransaction() {
        val tx = _state.value.pendingDeleteTx ?: return
        val budgetId = _state.value.budget?.id ?: return

        _state.update { it.copy(pendingDeleteTx = null, isDeletingTransactionId = tx.id) }

        viewModelScope.launch {
            try {
                val body = UpdateMonthlyBudgetRequest(
                    transactionOps = listOf(
                        BudgetTransactionOperation(id = tx.id, action = "delete")
                    )
                )
                val response = api.updateMonthlyBudget(budgetId, body)
                if (response.isSuccessful) {
                    val updated = response.body()!!
                    BudgetManager.saveCurrentMonthlyBudget(updated)
                    _state.update {
                        it.copy(
                            budget = updated,
                            isDeletingTransactionId = null,
                            message = "${tx.name} removed"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(isDeletingTransactionId = null, error = "Failed to delete")
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isDeletingTransactionId = null, error = "Failed to delete transaction")
                }
            }
        }
    }

    // ── Feedback reset ────────────────────────────────────────────────────────

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun clearError()   = _state.update { it.copy(error = null) }
}