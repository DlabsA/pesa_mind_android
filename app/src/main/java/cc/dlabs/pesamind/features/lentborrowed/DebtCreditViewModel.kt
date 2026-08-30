package cc.dlabs.pesamind.features.lentborrowed

import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.coordinator.UnifiedViewModel
import cc.dlabs.pesamind.core.data.DebtCreditRepository
import cc.dlabs.pesamind.core.network.models.DebtCreditResponse
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DebtCreditState(
    val debts: List<DebtCreditResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    // Defaults true so the list screen doesn't flash the PremiumUpsellCard before the real
    // value loads — mirrors ChannelViewModel/TransactionViewModel's identical pattern.
    val isPremium: Boolean = true,
)

/**
 * Room-backed (Phase 2) — mirrors [cc.dlabs.pesamind.features.settings.channels.ChannelViewModel]'s
 * shape exactly. Reads/writes go through [DebtCreditRepository], which is itself the
 * Premium gate — this ViewModel's [DebtCreditState.isPremium] is UI-only (drives which
 * composable renders), never the authority.
 */
class DebtCreditViewModel : UnifiedViewModel() {
    private val _state = MutableStateFlow(DebtCreditState(isLoading = true))
    val state: StateFlow<DebtCreditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPremium = AccountManager.isPremium())
        }
        viewModelScope.launch {
            try {
                DebtCreditRepository.observeAll().collect { debts ->
                    _state.value = _state.value.copy(debts = debts, isLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to load debts: ${e.message}")
            }
        }
    }

    fun refresh() {
        SyncScheduler.triggerSyncNow()
    }

    fun createDebtCredit(
        direction: String,
        counterpartyName: String,
        counterpartyPhone: String?,
        principalAmount: Double,
        dueAtMillis: Long?,
        note: String,
        reminderOffsets: List<Int>,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val result =
                    DebtCreditRepository.createDebtCredit(
                        direction = direction,
                        counterpartyName = counterpartyName,
                        counterpartyPhone = counterpartyPhone,
                        principalAmount = principalAmount,
                        dueAtMillis = dueAtMillis,
                        note = note,
                        reminderOffsets = reminderOffsets,
                    )
                _state.value =
                    if (result != null) {
                        _state.value.copy(isSaving = false, message = "Debt added")
                    } else {
                        _state.value.copy(isSaving = false, error = "This requires a Premium plan")
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = "Could not save debt: ${e.message}")
            }
        }
    }

    fun updateDebtCredit(
        id: String,
        counterpartyName: String,
        counterpartyPhone: String?,
        dueAtMillis: Long?,
        note: String,
        reminderOffsets: List<Int>,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                DebtCreditRepository.updateDebtCredit(id, counterpartyName, counterpartyPhone, dueAtMillis, note, reminderOffsets)
                _state.value = _state.value.copy(isSaving = false, message = "Debt updated")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSaving = false, error = "Could not update debt: ${e.message}")
            }
        }
    }

    fun deleteDebtCredit(id: String) {
        viewModelScope.launch {
            try {
                DebtCreditRepository.deleteDebtCredit(id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Could not delete debt: ${e.message}")
            }
        }
    }

    fun linkTransaction(
        debtCreditId: String,
        transactionId: String,
    ) {
        viewModelScope.launch { DebtCreditRepository.linkTransaction(debtCreditId, transactionId) }
    }

    fun unlinkTransaction(
        debtCreditId: String,
        transactionId: String,
    ) {
        viewModelScope.launch { DebtCreditRepository.unlinkTransaction(debtCreditId, transactionId) }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, message = null)
    }
}
