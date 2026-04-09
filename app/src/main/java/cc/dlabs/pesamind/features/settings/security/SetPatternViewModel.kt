package cc.dlabs.pesamind.features.settings.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PatternStep { DRAW, CONFIRM }

data class SetPatternState(
    val step: PatternStep = PatternStep.DRAW,
    val selectedDots: List<Int> = emptyList(),
    val firstPattern: List<Int> = emptyList(),
    val error: String? = null,
    val success: Boolean = false,
    val hint: String = "Draw a pattern"
)

class SetPatternViewModel : ViewModel() {

    private val _state = MutableStateFlow(SetPatternState())
    val state: StateFlow<SetPatternState> = _state.asStateFlow()

    fun onDotSelected(dot: Int) {
        val current = _state.value
        if (current.selectedDots.contains(dot)) return
        _state.value = current.copy(
            selectedDots = current.selectedDots + dot,
            error = null
        )
    }

    fun onDragEnd() {
        val current = _state.value
        val dots = current.selectedDots

        if (dots.size < 4) {
            _state.value = current.copy(
                selectedDots = emptyList(),
                error = "Connect at least 4 dots"
            )
            return
        }

        when (current.step) {
            PatternStep.DRAW -> {
                _state.value = current.copy(
                    step = PatternStep.CONFIRM,
                    firstPattern = dots,
                    selectedDots = emptyList(),
                    hint = "Draw pattern again to confirm"
                )
            }
            PatternStep.CONFIRM -> {
                if (dots == current.firstPattern) {
                    viewModelScope.launch {
                        TokenManager.savePattern(dots.joinToString(","))
                        _state.value = current.copy(success = true)
                    }
                } else {
                    _state.value = current.copy(
                        step = PatternStep.DRAW,
                        selectedDots = emptyList(),
                        firstPattern = emptyList(),
                        error = "Patterns did not match, draw again",
                        hint = "Draw a pattern"
                    )
                }
            }
        }
    }

    fun onDragStart() {
        _state.value = _state.value.copy(selectedDots = emptyList(), error = null)
    }
}