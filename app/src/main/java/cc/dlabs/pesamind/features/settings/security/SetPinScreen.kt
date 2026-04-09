package cc.dlabs.pesamind.features.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPinScreen(
    navController: NavHostController,
    vm: SetPinViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val teal = Color(0xFF1A9E8F)
    val navy = Color(0xFF1E2240)

    // Navigate back on success
    LaunchedEffect(state.success) {
        if (state.success) navController.popBackStack()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(navy),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                if (state.step == PinStep.ENTER) "Set PIN" else "Confirm PIN",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            if (state.step == PinStep.ENTER) "Enter a 4-digit PIN"
            else "Enter PIN again to confirm",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )

        Spacer(Modifier.height(24.dp))

        // PIN dots
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(4) { index ->
                    Text(
                        text = if (index < state.pin.length) "●" else "○",
                        color = if (index < state.pin.length) teal else Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Error
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = Color(0xFFE74C3C), fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))

        // Keypad
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "back"),
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clickable(enabled = key.isNotEmpty()) {
                                if (key == "back") vm.onDelete() else vm.onKeyPress(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "back" -> Icon(
                                Icons.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            "" -> {}
                            else -> Text(
                                key,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}