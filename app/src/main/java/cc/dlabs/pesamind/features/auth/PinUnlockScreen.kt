package cc.dlabs.pesamind.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.theme.Spacing
import cc.dlabs.pesamind.core.utils.BiometricAuthResult
import cc.dlabs.pesamind.core.utils.authenticateWithBiometrics
import cc.dlabs.pesamind.core.utils.isBiometricAvailable
import kotlinx.coroutines.launch

@Composable
fun PinUnlockScreen(
    navController: NavHostController,
    isSetup: Boolean = false,
    vm: UnlockViewModel = viewModel(),
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val state by vm.state.collectAsStateWithLifecycle()
    val maxPin = 4

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val biometricAvailable = remember { (context as? FragmentActivity)?.let(::isBiometricAvailable) ?: false }

    fun onBiometricTap() {
        val activity = context as? FragmentActivity ?: return
        scope.launch {
            when (val result = authenticateWithBiometrics(activity)) {
                BiometricAuthResult.Success ->
                    vm.unlockWithBiometric(
                        onSuccess = {
                            navController.navigate(Routes.Dashboard.route) {
                                popUpTo(Routes.PinUnlock.route) { inclusive = true }
                            }
                        },
                        onError = { err -> errorMessage = err },
                    )
                is BiometricAuthResult.Error -> errorMessage = result.message
                BiometricAuthResult.Cancelled -> Unit // user backed out — no error noise
            }
        }
    }

    LaunchedEffect(pin, state.isLoading) {
        if (pin.length == maxPin && !state.isLoading) {
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
                    },
                )
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Spacing.Space6.dp, vertical = Spacing.Space8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Spacer(modifier = Modifier.height(Spacing.Space4.dp))

        Text(
            text = if (isSetup) "Create PIN" else "Enter PIN",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(Spacing.Space3.dp))

        Text(
            text = if (isSetup) "Set a 4-digit PIN to secure the app" else "Use your 4-digit PIN to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.Space10.dp))

        // PIN dots display
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.Space8.dp, vertical = Spacing.Space5.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Space6.dp)) {
                repeat(maxPin) { index ->
                    Text(
                        text = if (index < pin.length) "●" else "○",
                        style = MaterialTheme.typography.headlineMedium,
                        color =
                            if (index < pin.length) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Error or status message
        val displayError = errorMessage ?: state.errorMessage
        Spacer(Modifier.height(Spacing.Space3.dp))
        if (displayError != null) {
            Text(
                text = displayError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (state.isLoading) {
            Text(
                text = "Checking PIN…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.Space4.dp))

        // Keypad
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.Space5.dp, vertical = Spacing.Space4.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.Space2.dp),
        ) {
            val keys =
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("face", "0", "back"),
                )

            keys.forEach { row ->
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .clickable(enabled = !state.isLoading) {
                                        errorMessage = null
                                        when (key) {
                                            "back" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            "face" -> if (!isSetup && biometricAvailable) onBiometricTap()
                                            else -> if (pin.length < maxPin) pin += key
                                        }
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (key) {
                                "back" ->
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                "face" ->
                                    Icon(
                                        imageVector = Icons.Filled.Fingerprint,
                                        contentDescription = "Biometric",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
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
