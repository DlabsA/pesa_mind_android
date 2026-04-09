package cc.dlabs.pesamind.features.settings.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPatternScreen(
    navController: NavHostController,
    vm: SetPatternViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val teal = Color(0xFF1A9E8F)
    val navy = Color(0xFF1E2240)
    val dotPositions = remember { mutableStateMapOf<Int, Offset>() }
    var currentDragPos by remember { mutableStateOf<Offset?>(null) }

    // Navigate back on success
    LaunchedEffect(state.success) {
        if (state.success) navController.popBackStack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(navy),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top bar ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = if (state.step == PatternStep.DRAW) "Set Pattern" else "Confirm Pattern",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // ── Hint text ────────────────────────────────────────
        Text(
            text = state.hint,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )

        Spacer(Modifier.height(8.dp))

        // ── Error text ───────────────────────────────────────
        if (state.error != null) {
            Text(
                text = state.error!!,
                color = Color(0xFFE74C3C),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(32.dp))

        // ── Pattern grid ─────────────────────────────────────
        Box(
            modifier = Modifier
                .size(300.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            vm.onDragStart()
                            currentDragPos = offset
                            // Check if drag started on a dot
                            dotPositions.entries.forEach { (index, pos) ->
                                if ((offset - pos).getDistance() < 50f) {
                                    vm.onDotSelected(index)
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            currentDragPos = change.position
                            // Activate dots as finger passes over them
                            dotPositions.entries.forEach { (index, pos) ->
                                if ((change.position - pos).getDistance() < 50f) {
                                    vm.onDotSelected(index)
                                }
                            }
                        },
                        onDragEnd = {
                            currentDragPos = null
                            vm.onDragEnd()
                        },
                        onDragCancel = {
                            currentDragPos = null
                            vm.onDragStart() // reset
                        }
                    )
                }
        ) {
            // Draw lines between selected dots
            Canvas(modifier = Modifier.fillMaxSize()) {
                val selectedDots = state.selectedDots
                val lineColor = teal.copy(alpha = 0.8f)

                // Lines between connected dots
                for (i in 0 until selectedDots.size - 1) {
                    val start = dotPositions[selectedDots[i]] ?: continue
                    val end = dotPositions[selectedDots[i + 1]] ?: continue
                    drawLine(
                        color = lineColor,
                        start = start,
                        end = end,
                        strokeWidth = 3f
                    )
                }

                // Line from last dot to current finger position
                currentDragPos?.let { drag ->
                    if (selectedDots.isNotEmpty()) {
                        val last = dotPositions[selectedDots.last()] ?: return@let
                        drawLine(
                            color = lineColor.copy(alpha = 0.4f),
                            start = last,
                            end = drag,
                            strokeWidth = 3f
                        )
                    }
                }
            }

            // Dot grid (3x3)
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
                            val isSelected = state.selectedDots.contains(index)

                            Box(contentAlignment = Alignment.Center) {
                                // Outer ring when selected
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                teal.copy(alpha = 0.2f),
                                                CircleShape
                                            )
                                    )
                                }
                                // The dot itself
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(
                                            if (isSelected) teal else Color.White,
                                            CircleShape
                                        )
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInParent()
                                            dotPositions[index] = Offset(
                                                pos.x + coords.size.width / 2f,
                                                pos.y + coords.size.height / 2f
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Step indicator ───────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) { index ->
                val isActive = when (state.step) {
                    PatternStep.DRAW -> index == 0
                    PatternStep.CONFIRM -> index == 1
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isActive) teal else Color.White.copy(alpha = 0.3f),
                            CircleShape
                        )
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Face ID / biometrics hint ────────────────────────
        Text(
            "🆔  You can also use Face ID after setting this",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}

// Extension to calculate distance from Offset origin
private fun Offset.getDistance(): Float =
    kotlin.math.sqrt(x * x + y * y)