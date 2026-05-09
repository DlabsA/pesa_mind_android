package cc.dlabs.pesamind.features.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.analytics.BudgetVsActualResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BudgetVsActualState(
    val data: BudgetVsActualResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val month: Int = LocalDate.now().monthValue,
    val year: Long = LocalDate.now().year.toLong()
)

class BudgetVsActualViewModel : ViewModel() {
    private val _state = MutableStateFlow(BudgetVsActualState())
    val state: StateFlow<BudgetVsActualState> = _state.asStateFlow()

    init {
        loadBudgetVsActual()
    }

    fun loadBudgetVsActual() {
        val currentState = _state.value
        viewModelScope.launch {
            _state.value = currentState.copy(isLoading = true, error = null)
            try {
                val response = ApiClient.api.getBudgetVsActual(
                    year = currentState.year.toInt(),
                    month = currentState.month
                )
                if (response.isSuccessful) {
                    _state.value = currentState.copy(
                        isLoading = false,
                        data = response.body()
                    )
                } else {
                    _state.value = currentState.copy(
                        isLoading = false,
                        error = "Failed to load budget vs actual (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _state.value = currentState.copy(
                    isLoading = false,
                    error = "Error: ${e.message}"
                )
            }
        }
    }

    fun setMonth(month: Int, year: Long) {
        _state.value = _state.value.copy(month = month, year = year)
        loadBudgetVsActual()
    }
}