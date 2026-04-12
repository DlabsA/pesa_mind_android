// Updated PatternUnlockScreen.kt
package cc.dlabs.pesamind.features.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes

@Composable
fun PatternUnlockScreen(
    navController: NavHostController,
    isSetup: Boolean = false,
    vm: UnlockViewModel = viewModel()
) {
    val navy = Color(0xFF1E2240)
    val teal = Color(0xFF1A9E8F)
    val selectedDots = remember { mutableStateListOf<Int>() }
    var currentDragPos by remember { mutableStateOf<Offset?>(null) }
    val dotPositions = remember { mutableStateMapOf<Int, Offset>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val state by vm.state.collectAsState()

    fun patternKey(): String = selectedDots.joinToString(",")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(navy),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(280.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            errorMessage = null
                            selectedDots.clear()
                            currentDragPos = offset
                            dotPositions.entries.forEach { (index, pos) ->
                                if ((offset - pos).getDistance() < 40f) {
                                    selectedDots.add(index)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            currentDragPos = change.position
                            dotPositions.entries.forEach { (index, pos) ->
                                if (!selectedDots.contains(index) &&
                                    (change.position - pos).getDistance() < 40f
                                ) {
                                    selectedDots.add(index)
                                }
                            }
                        },
                        onDragEnd = {
                            currentDragPos = null
                            if (selectedDots.size >= 4) {
                                val enteredPattern = patternKey()
                                if (isSetup) {
                                    vm.setupPattern(enteredPattern) {
                                        navController.navigate(Routes.Dashboard.route) {
                                            popUpTo(Routes.LockSetup.route) { inclusive = true }
                                        }
                                    }
                                } else {
                                    vm.unlockWithPattern(
                                        enteredPattern = enteredPattern,
                                        onSuccess = {
                                            navController.navigate(Routes.Dashboard.route) {
                                                popUpTo(Routes.PatternUnlock.route) { inclusive = true }
                                            }
                                        },
                                        onError = { error ->
                                            errorMessage = error
                                            selectedDots.clear()
                                        }
                                    )
                                }
                            } else {
                                selectedDots.clear()
                            }
                        }
                    )
                }
        ) {
            // Draw connecting lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineColor = teal.copy(alpha = 0.6f)
                for (i in 0 until selectedDots.size - 1) {
                    val start = dotPositions[selectedDots[i]] ?: continue
                    val end = dotPositions[selectedDots[i + 1]] ?: continue
                    drawLine(lineColor, start, end, strokeWidth = 4f)
                }
                currentDragPos?.let { drag ->
                    if (selectedDots.isNotEmpty()) {
                        val last = dotPositions[selectedDots.last()] ?: return@let
                        drawLine(lineColor, last, drag, strokeWidth = 4f)
                    }
                }
            }

            // Draw dots in 3x3 grid
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..2) {
                            val index = row * 3 + col
                            val isSelected = selectedDots.contains(index)
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        if (isSelected) teal else Color.White,
                                        CircleShape
                                    )
                                    .onGloballyPositioned { coords ->
                                        dotPositions[index] =
                                            Offset(
                                                coords.positionInParent().x + 8f,
                                                coords.positionInParent().y + 8f
                                            )
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text("Draw your pattern", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)

        // Show error from viewmodel or local state
        val displayError = errorMessage ?: state.errorMessage
        if (displayError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(displayError, color = Color(0xFFE74C3C), fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
