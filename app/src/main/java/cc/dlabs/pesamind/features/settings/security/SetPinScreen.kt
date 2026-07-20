package cc.dlabs.pesamind.features.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPinScreen(
    navController: NavHostController,
    vm: SetPinViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    // Navigate back on success
    LaunchedEffect(state.success) {
        if (state.success) navController.popBackStack()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.Space6.dp, vertical = Spacing.Space8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.Space4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = if (state.step == PinStep.ENTER) "Set PIN" else "Confirm PIN",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.Space4.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        // Instruction text
        Text(
            text = if (state.step == PinStep.ENTER) "Enter a 4-digit PIN" else "Confirm your PIN",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.Space6.dp))

        // PIN dots display
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.Space8.dp, vertical = Spacing.Space5.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Space6.dp)) {
                repeat(4) { index ->
                    Text(
                        text = if (index < state.pin.length) "●" else "○",
                        style = MaterialTheme.typography.headlineMedium,
                        color =
                            if (index < state.pin.length) {
                                primaryColor
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Error message
        if (state.error != null) {
            Spacer(Modifier.height(Spacing.Space3.dp))
            Text(
                text = state.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.weight(1f))

        // Keypad
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.Space5.dp, vertical = Spacing.Space4.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space2.dp),
        ) {
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "back"),
            ).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            Spacing.Space4.dp,
                            alignment = Alignment.CenterHorizontally,
                        ),
                ) {
                    row.forEach { key ->
                        Box(
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (key.isEmpty()) {
                                            MaterialTheme.colorScheme.surface
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        },
                                    )
                                    .clickable(enabled = key.isNotEmpty()) {
                                        if (key == "back") vm.onDelete() else vm.onKeyPress(key)
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (key) {
                                "back" ->
                                    Icon(
                                        Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                "" -> {} // Empty spacer
                                else ->
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                    )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
}
