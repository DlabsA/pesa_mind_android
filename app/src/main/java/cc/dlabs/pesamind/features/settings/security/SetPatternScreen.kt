package cc.dlabs.pesamind.features.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.ui.BackStyleHeader
import cc.dlabs.pesamind.features.common.PatternGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPatternScreen(
    navController: NavHostController,
    vm: SetPatternViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val teal = MaterialTheme.colorScheme.primary
    var currentDragPos by remember { mutableStateOf<Offset?>(null) }

    // Navigate back on success
    LaunchedEffect(state.success) {
        if (state.success) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            BackStyleHeader(
                title = if (state.step == PatternStep.DRAW) "Set Pattern" else "Confirm Pattern",
                onBack = { navController.popBackStack() },
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.Space4.dp)
                        .padding(top = 8.dp),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            // ── Hint text ────────────────────────────────────────
            Text(
                text = state.hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(8.dp))

            // ── Error text ───────────────────────────────────────
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Pattern grid ─────────────────────────────────────
            PatternGrid(
                selectedDots = state.selectedDots,
                currentDragPos = currentDragPos,
                onDotSelected = { index ->
                    vm.onDotSelected(index)
                },
                onDragStart = {
                    vm.onDragStart()
                },
                onDragEnd = {
                    vm.onDragEnd()
                },
                onDragCancel = {
                    vm.onDragStart() // reset
                },
                lineColor = teal,
                dotColorUnselected = teal.copy(alpha = 0.9f),
                dotColorSelected = teal,
                isLoading = false,
            )

            Spacer(Modifier.height(24.dp))

            // ── Step indicator ───────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { index ->
                    val isActive =
                        when (state.step) {
                            PatternStep.DRAW -> index == 0
                            PatternStep.CONFIRM -> index == 1
                        }
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (isActive) teal else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    CircleShape,
                                ),
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
