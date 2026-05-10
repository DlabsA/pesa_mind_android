package cc.dlabs.pesamind.features.tools

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient.api
import cc.dlabs.pesamind.core.network.models.BudgetTransactionOperation
import cc.dlabs.pesamind.core.network.models.BudgetTransactionRequest
import cc.dlabs.pesamind.core.network.models.BudgetTransactionResponse
import cc.dlabs.pesamind.core.network.models.CreateYearlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.UpdateYearlyBudgetRequest
import cc.dlabs.pesamind.core.network.models.YearlyBudgetResponse
import cc.dlabs.pesamind.core.storage.BudgetManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class YearlyBudgetUiState(
    // Period
    val month: Int = 0,
    val year: Int = 0,

    // Budget data
    val budget: YearlyBudgetResponse? = null,
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

class YearlyBudgetViewModel() : ViewModel() {

    private val _state = MutableStateFlow(YearlyBudgetUiState())
    val state: StateFlow<YearlyBudgetUiState> = _state.asStateFlow()

    fun init(year: Int) {
        _state.update { it.copy(year = year, isLoading = true) }
        loadBudget(year)
    }

    private fun loadBudget(year: Int) {
        viewModelScope.launch {
            Log.d("YearlyBudgetVM", "Loading budget for year: $year")

            // First, try to get from cache
            val cached = BudgetManager.getYearlyBudgetByYear(year.toLong())
            if (cached != null) {
                _state.update {
                    it.copy(
                        budget = cached,
                        yearlyBudgetId = cached.id,
                        isLoading = false
                    )
                }
            } else {
                Log.d("YearlyBudgetVM", "No cached budget found")
                _state.update { it.copy(isLoading = true) }
            }

            // Always try to fetch from network to get latest data
            try {
                val response = api.getYearlyBudgetsByYear(year.toLong())

                if (response.isSuccessful) {
                    val budget = response.body()
                    if (budget != null) {
                        // Save to cache
                        BudgetManager.saveYearlyBudgets(listOf(budget))
                        BudgetManager.saveCurrentYearlyBudget(budget)

                        _state.update {
                            it.copy(
                                budget = budget,
                                yearlyBudgetId = budget.id,
                                isLoading = false,
                                error = null
                            )
                        }
                    } else {
                        Log.d("YearlyBudgetVM", "No budget exists for year $year on server")
                        _state.update {
                            it.copy(
                                budget = null,
                                yearlyBudgetId = "",
                                isLoading = false,
                                error = null  // No error, just no budget yet
                            )
                        }
                    }
                } else {
                    // Handle specific HTTP errors
                    val errorMessage = when (response.code()) {
                        404 -> "No budget found for $year"
                        401 -> "Authentication error. Please login again."
                        500 -> "Server error. Please try again later."
                        else -> "Failed to load budget: ${response.code()}"
                    }
                    Log.e("YearlyBudgetVM", "Network error: $errorMessage")

                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = if (it.budget == null) errorMessage else null
                        )
                    }
                }
            } catch (e: HttpException) {
                Log.e("YearlyBudgetVM", "HTTP Exception: ${e.code()} - ${e.message}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (it.budget == null) "Network error: ${e.message}" else null
                    )
                }
            } catch (e: Exception) {
                Log.e("YearlyBudgetVM", "Exception loading budget", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (it.budget == null) "Error: ${e.message}" else null
                    )
                }
            }
        }
    }

    // Refresh budget data
    fun refresh() {
        val year = _state.value.year
        if (year > 0) {
            loadBudget(year)
        }
    }

    fun onNameChange(v: String) = _state.update {
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

            val tx = BudgetTransactionRequest(
                name = s.formName.trim(),
                amount = amount!!,
                type = s.formType
            )

            try {
                if (s.budget == null) {
                    Log.d("YearlyBudgetVM", "Creating new yearly budget with transaction")
                    createYearlyBudgetWithTransaction(tx)
                } else {
                    Log.d("YearlyBudgetVM", "Adding transaction to existing budget: ${s.budget.id}")
                    patchBudgetAddTransaction(s.budget.id, tx)
                }
            } catch (e: Exception) {
                Log.e("YearlyBudgetVM", "Failed to add transaction", e)
                _state.update {
                    it.copy(
                        isAddingTransaction = false,
                        error = "Failed to add transaction: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun createYearlyBudgetWithTransaction(tx: BudgetTransactionRequest) {
        val s = _state.value
        val body = CreateYearlyBudgetRequest(
            year = s.year.toLong(),
            transactions = listOf(tx)
        )

        Log.d("YearlyBudgetVM", "Creating yearly budget: year=${body.year}, tx=${tx.name}")

        try {
            val response = api.createYearlyBudget(body)
            if (response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                if (errorBody?.contains("duplicate key value violates unique constraint") == true) {
                    // Budget already exists – try to refresh and then patch
                    refresh()
                    // Wait a moment and retry as update
                    delay(500)
                    patchBudgetAddTransaction(s.yearlyBudgetId, tx)
                    return
                }
                val created = response.body()!!
                Log.d("YearlyBudgetVM", "Successfully created budget: ${created.id}")

                BudgetManager.saveYearlyBudgets(listOf(created))
                BudgetManager.saveCurrentYearlyBudget(created)

                _state.update {
                    it.copy(
                        budget = created,
                        yearlyBudgetId = created.id,
                        isAddingTransaction = false,
                        message = "Budget created and transaction added",
                        formName = "",
                        formAmount = "",
                        formType = TransactionType.INCOME
                    )
                }
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e("YearlyBudgetVM", "Failed to create budget: $errorBody")
                _state.update {
                    it.copy(
                        isAddingTransaction = false,
                        error = "Failed to create budget: ${response.code()}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("YearlyBudgetVM", "Network error creating budget", e)
            _state.update {
                it.copy(
                    isAddingTransaction = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

    private suspend fun patchBudgetAddTransaction(budgetId: String, tx: BudgetTransactionRequest) {
        val body = UpdateYearlyBudgetRequest(
            transactionOps = listOf(
                BudgetTransactionOperation(
                    name = tx.name,
                    amount = tx.amount,
                    type = tx.type,
                    action = "add"
                )
            )
        )

        try {
            val response = api.updateYearlyBudget(budgetId, body)
            if (response.isSuccessful) {
                val updated = response.body()!!
                BudgetManager.saveCurrentYearlyBudget(updated)

                val allBudgets = BudgetManager.getYearlyBudgets().toMutableList()
                val index = allBudgets.indexOfFirst { it.id == updated.id }
                if (index != -1) {
                    allBudgets[index] = updated
                    BudgetManager.saveYearlyBudgets(allBudgets)
                }

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
                Log.e("YearlyBudgetVM", "Failed to add transaction: ${response.code()}")
                _state.update {
                    it.copy(
                        isAddingTransaction = false,
                        error = "Failed to add transaction: ${response.code()}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("YearlyBudgetVM", "Network error adding transaction", e)
            _state.update {
                it.copy(
                    isAddingTransaction = false,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }

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
                val body = UpdateYearlyBudgetRequest(
                    transactionOps = listOf(
                        BudgetTransactionOperation(id = tx.id, action = "delete")
                    )
                )
                val response = api.updateYearlyBudget(budgetId, body)
                if (response.isSuccessful) {
                    val updated = response.body()!!
                    BudgetManager.saveCurrentYearlyBudget(updated)

                    val allBudgets = BudgetManager.getYearlyBudgets().toMutableList()
                    val index = allBudgets.indexOfFirst { it.id == updated.id }
                    if (index != -1) {
                        allBudgets[index] = updated
                        BudgetManager.saveYearlyBudgets(allBudgets)
                    }

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
                Log.e("YearlyBudgetVM", "Failed to delete transaction", e)
                _state.update {
                    it.copy(isDeletingTransactionId = null, error = "Failed to delete transaction")
                }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun clearError()   = _state.update { it.copy(error = null) }
}