package cc.dlabs.pesamind.features.savinggoals

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.SavingGoalRepository
import cc.dlabs.pesamind.core.network.models.SavingGoalResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SavingGoalState(
    val goals: List<SavingGoalResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val isPremium: Boolean = true,
)

/** Structural clone of [cc.dlabs.pesamind.features.lentborrowed.DebtCreditViewModel] — see
 * that class's doc comment for the full rationale. */
class SavingGoalViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(SavingGoalState(isLoading = true))
    val state: StateFlow<SavingGoalState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPremium = AccountManager.isPremium())
        }
        viewModelScope.launch {
            try {
                SavingGoalRepository.observeAll().collect { goals ->
                    _state.value = _state.value.copy(goals = goals, isLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to load goals: ${e.message}")
            }
        }
    }

    fun refresh() {
        SyncScheduler.triggerSyncNow()
    }

    fun createSavingGoal(
        name: String,
        targetAmount: Double,
        targetAtMillis: Long?,
        note: String,
        reminderOffsets: List<Int>,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val result = SavingGoalRepository.createSavingGoal(name, targetAmount, targetAtMillis, note, reminderOffsets)
                _state.value =
                    if (result != null) {
                        _state.value.copy(isSaving = false, message = "Goal added")
                    } else {
                        _state.value.copy(isSaving = false, error = "This requires a Premium plan")
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = "Could not save goal: ${e.message}")
            }
        }
    }

    fun updateSavingGoal(
        id: String,
        name: String,
        targetAtMillis: Long?,
        note: String,
        reminderOffsets: List<Int>,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                SavingGoalRepository.updateSavingGoal(id, name, targetAtMillis, note, reminderOffsets)
                _state.value = _state.value.copy(isSaving = false, message = "Goal updated")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = "Could not update goal: ${e.message}")
            }
        }
    }

    fun deleteSavingGoal(id: String) {
        viewModelScope.launch {
            try {
                SavingGoalRepository.deleteSavingGoal(id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Could not delete goal: ${e.message}")
            }
        }
    }

    fun linkTransaction(
        savingGoalId: String,
        transactionId: String,
    ) {
        viewModelScope.launch { SavingGoalRepository.linkTransaction(savingGoalId, transactionId) }
    }

    fun unlinkTransaction(
        savingGoalId: String,
        transactionId: String,
    ) {
        viewModelScope.launch { SavingGoalRepository.unlinkTransaction(savingGoalId, transactionId) }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, message = null)
    }
}
