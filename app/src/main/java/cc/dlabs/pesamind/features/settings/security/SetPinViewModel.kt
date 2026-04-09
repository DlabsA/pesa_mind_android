package cc.dlabs.pesamind.features.settings.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PinStep { ENTER, CONFIRM }

data class SetPinState(
    val step: PinStep = PinStep.ENTER,
    val pin: String = "",
    val firstPin: String = "",
    val error: String? = null,
    val success: Boolean = false
)

class SetPinViewModel : ViewModel() {

    private val _state = MutableStateFlow(SetPinState())
    val state: StateFlow<SetPinState> = _state.asStateFlow()

    fun onKeyPress(key: String) {
        val current = _state.value
        if (current.pin.length >= 4) return

        val newPin = current.pin + key
        _state.value = current.copy(pin = newPin, error = null)

        if (newPin.length == 4) {
            onPinComplete(newPin)
        }
    }

    fun onDelete() {
        val current = _state.value
        if (current.pin.isEmpty()) return
        _state.value = current.copy(pin = current.pin.dropLast(1))
    }

    private fun onPinComplete(pin: String) {
        val current = _state.value
        when (current.step) {
            PinStep.ENTER -> {
                // Move to confirm step
                _state.value = current.copy(
                    step = PinStep.CONFIRM,
                    firstPin = pin,
                    pin = ""
                )
            }
            PinStep.CONFIRM -> {
                if (pin == current.firstPin) {
                    // PINs match — save it
                    viewModelScope.launch {
                        TokenManager.savePin(pin)
                        _state.value = current.copy(success = true)
                    }
                } else {
                    // No match — restart
                    _state.value = current.copy(
                        step = PinStep.ENTER,
                        pin = "",
                        firstPin = "",
                        error = "PINs did not match, try again"
                    )
                }
            }
        }
    }
}