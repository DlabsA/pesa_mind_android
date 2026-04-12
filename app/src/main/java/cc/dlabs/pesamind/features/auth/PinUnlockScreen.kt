// Updated PinUnlockScreen.kt
package cc.dlabs.pesamind.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes

@Composable
fun PinUnlockScreen(
    navController: NavHostController,
    isSetup: Boolean = false,
    vm: UnlockViewModel = viewModel()
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val state by vm.state.collectAsState()
    val maxPin = 4
    val navy = Color(0xFF1E2240)

    LaunchedEffect(pin) {
        if (pin.length == maxPin) {
            if (isSetup) {
                vm.setupPin(pin) {
                    navController.navigate(Routes.Dashboard.route) {
                        popUpTo(Routes.LockSetup.route) { inclusive = true }
                    }
                }
            } else {
                vm.unlockWithPin(
                    enteredPin = pin,
                    onSuccess = {
                        navController.navigate(Routes.Dashboard.route) {
                            popUpTo(Routes.PinUnlock.route) { inclusive = true }
                        }
                    },
                    onError = { error ->
                        errorMessage = error
                        pin = ""
                    }
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(navy),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // PIN dots display
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(maxPin) { index ->
                    Text(
                        text = if (index < pin.length) "●" else "0",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val displayError = errorMessage ?: state.errorMessage
        if (displayError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = displayError, color = Color(0xFFE74C3C), fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Keypad
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("face", "0", "back"),
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clickable(enabled = !state.isLoading) {
                                when (key) {
                                    "back" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    "face" -> { /* TODO: biometric */ }
                                    else -> if (pin.length < maxPin) pin += key
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "back" -> Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            "face" -> Icon(
                                Icons.Filled.Fingerprint,
                                contentDescription = "Fingerprint",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            else -> Text(
                                text = key,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
