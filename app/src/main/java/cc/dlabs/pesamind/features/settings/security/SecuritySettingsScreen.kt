package cc.dlabs.pesamind.features.settings.security

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    navController: NavHostController,
    vm: SecurityViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val teal = Color(0xFF1A9E8F)

    // Show snackbar on message
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = teal)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "App Lock",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            // ── PIN option ──────────────────────────────────────
            SecurityOptionRow(
                icon = Icons.Filled.Pin,
                title = "PIN Lock",
                subtitle = if (state.currentMode == LockMode.PIN)
                    "Active — tap to change" else "Set a 4-digit PIN",
                isActive = state.currentMode == LockMode.PIN,
                teal = teal,
                onClick = { navController.navigate(Routes.SetPin.route) }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            // ── Pattern option ───────────────────────────────────
            SecurityOptionRow(
                icon = Icons.Filled.Pattern,
                title = "Pattern Lock",
                subtitle = if (state.currentMode == LockMode.PATTERN)
                    "Active — tap to change" else "Draw an unlock pattern",
                isActive = state.currentMode == LockMode.PATTERN,
                teal = teal,
                onClick = { navController.navigate(Routes.SetPattern.route) }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            // ── Disable lock ─────────────────────────────────────
            if (state.currentMode != LockMode.NONE) {
                SecurityOptionRow(
                    icon = Icons.Filled.LockOpen,
                    title = "Remove Lock",
                    subtitle = "Disable app lock entirely",
                    isActive = false,
                    teal = Color(0xFFE74C3C),
                    onClick = { vm.disableLock() }
                )
            }
        }
    }
}

@Composable
private fun SecurityOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isActive: Boolean,
    teal: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = if (isActive) teal else Color.Gray,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
        if (isActive) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Active", tint = teal)
        } else {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Go", tint = Color.Gray)
        }
    }
}