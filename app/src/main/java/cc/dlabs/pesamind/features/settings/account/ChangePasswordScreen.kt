package cc.dlabs.pesamind.features.settings.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    navController: NavHostController,
    vm: ChangePasswordViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val teal = Color(0xFF1A9E8F)

    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    // Go back on success
    LaunchedEffect(state.success) {
        if (state.success) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change Password") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Current password ─────────────────────────────
            PasswordField(
                value = state.currentPassword,
                onValueChange = { vm.onCurrentPasswordChange(it) },
                label = "Current Password",
                showPassword = showCurrent,
                onToggle = { showCurrent = !showCurrent },
                teal = teal
            )

            // ── New password ─────────────────────────────────
            PasswordField(
                value = state.newPassword,
                onValueChange = { vm.onNewPasswordChange(it) },
                label = "New Password",
                showPassword = showNew,
                onToggle = { showNew = !showNew },
                teal = teal
            )

            // ── Confirm new password ─────────────────────────
            PasswordField(
                value = state.confirmPassword,
                onValueChange = { vm.onConfirmPasswordChange(it) },
                label = "Confirm New Password",
                showPassword = showConfirm,
                onToggle = { showConfirm = !showConfirm },
                teal = teal,
                isError = state.error?.contains("match") == true
            )

            // ── Password strength indicator ──────────────────
            if (state.newPassword.isNotEmpty()) {
                PasswordStrengthBar(password = state.newPassword)
            }

            // ── Error message ────────────────────────────────
            if (state.error != null) {
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Submit button ────────────────────────────────
            Button(
                onClick = { vm.submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = teal),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text("Update Password", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Reusable password field ──────────────────────────────────
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showPassword: Boolean,
    onToggle: () -> Unit,
    teal: Color,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        isError = isError,
        visualTransformation = if (showPassword)
            VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        leadingIcon = {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = teal)
        },
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showPassword) "Hide" else "Show",
                    tint = Color.Gray
                )
            }
        }
    )
}

// ── Password strength bar ────────────────────────────────────
@Composable
private fun PasswordStrengthBar(password: String) {
    val strength = when {
        password.length < 6 -> 0
        password.length < 8 -> 1
        password.any { it.isDigit() } && password.any { it.isLetter() } -> 3
        else -> 2
    }
    val (label, color) = when (strength) {
        0 -> "Weak" to Color(0xFFE74C3C)
        1 -> "Fair" to Color(0xFFE67E22)
        2 -> "Good" to Color(0xFF3498DB)
        else -> "Strong" to Color(0xFF2ECC71)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { index ->
                LinearProgressIndicator(
                    progress = { if (index < strength) 1f else 0f },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = color,
                    trackColor = Color.LightGray
                )
            }
        }
        Text(label, color = color, fontSize = 12.sp)
    }
}