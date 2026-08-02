package cc.dlabs.pesamind.features.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.utils.BiometricAuthResult
import cc.dlabs.pesamind.core.utils.authenticateWithBiometrics
import cc.dlabs.pesamind.core.utils.isBiometricAvailable
import cc.dlabs.pesamind.features.common.PatternGrid
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

private enum class PatternState { Idle, Drawing, Success, Error }

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun PatternUnlockScreen(
    navController: NavHostController,
    isSetup: Boolean = false,
    vm: UnlockViewModel = viewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val vmState by vm.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val biometricAvailable = remember { (context as? FragmentActivity)?.let(::isBiometricAvailable) ?: false }

    // Pattern drawing state
    val selectedDots = remember { mutableStateListOf<Int>() }
    var dragPos by remember { mutableStateOf<Offset?>(null) }

    // High-level visual state drives colours / messages
    var patternState by remember { mutableStateOf(PatternState.Idle) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val minDots = 4

    // Colours driven by state
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val successColor = Color(0xFF4CAF50)

    val activeLineColor by animateColorAsState(
        targetValue =
            when (patternState) {
                PatternState.Error -> errorColor
                PatternState.Success -> successColor
                else -> primaryColor
            },
        animationSpec = tween(300),
        label = "lineColor",
    )

    fun patternKey() = selectedDots.joinToString(",")

    fun resetPattern() {
        selectedDots.clear()
        dragPos = null
        patternState = PatternState.Idle
    }

    fun onDragEnd() {
        dragPos = null
        if (selectedDots.size < minDots) {
            patternState = PatternState.Error
            feedbackMessage = "Connect at least $minDots dots"
            selectedDots.clear()
            return
        }
        val key = patternKey()
        if (isSetup) {
            vm.setupPattern(key) {
                navController.navigate(Routes.Dashboard.route) {
                    popUpTo(Routes.LockSetup.route) { inclusive = true }
                }
            }
        } else {
            vm.unlockWithPattern(
                enteredPattern = key,
                onSuccess = {
                    patternState = PatternState.Success
                    feedbackMessage = null
                    navController.navigate(Routes.Dashboard.route) {
                        popUpTo(Routes.PatternUnlock.route) { inclusive = true }
                    }
                },
                onError = { err ->
                    patternState = PatternState.Error
                    feedbackMessage = err
                    selectedDots.clear()
                },
            )
        }
    }

    // -----------------------------------------------------------------------
    // Root layout — dark surface behind a centred card
    // -----------------------------------------------------------------------
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ----------------------------------------------------------------
            // Header
            // ----------------------------------------------------------------
            Spacer(Modifier.height(16.dp))

            Text(
                text = if (isSetup) "Set Pattern" else "Welcome back",
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text =
                    if (isSetup) {
                        "Draw a pattern connecting at least $minDots dots"
                    } else {
                        "Draw your unlock pattern to continue"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))

            // ----------------------------------------------------------------
            // Pattern grid card (reusable component)
            // ----------------------------------------------------------------
            PatternGrid(
                selectedDots = selectedDots,
                currentDragPos = dragPos,
                onDotSelected = { index ->
                    if (!selectedDots.contains(index)) {
                        selectedDots.add(index)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onDragStart = {
                    resetPattern()
                    patternState = PatternState.Drawing
                },
                onDragEnd = { onDragEnd() },
                onDragCancel = { resetPattern() },
                lineColor = activeLineColor,
                dotColorUnselected = primaryColor.copy(alpha = 0.9f),
                dotColorSelected = primaryColor,
                isLoading = vmState.isLoading,
            )

            // ----------------------------------------------------------------
            // Feedback area  (fixed-height so layout doesn't jump)
            // ----------------------------------------------------------------
            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier.height(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                val displayed = feedbackMessage ?: vmState.errorMessage
                when {
                    vmState.isLoading ->
                        Text(
                            text = "Verifying…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    displayed != null ->
                        Text(
                            text = displayed,
                            style = MaterialTheme.typography.bodySmall,
                            color = errorColor,
                        )
                    patternState == PatternState.Idle ->
                        Text(
                            text = "Connect dots to draw your pattern",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                }
            }

            // ----------------------------------------------------------------
            // Biometric shortcut (unlock mode only)
            // ----------------------------------------------------------------
            if (!isSetup && biometricAvailable) {
                Spacer(Modifier.height(32.dp))

                TextButton(
                    onClick = {
                        val activity = context as? FragmentActivity ?: return@TextButton
                        scope.launch {
                            when (val result = authenticateWithBiometrics(activity)) {
                                BiometricAuthResult.Success ->
                                    vm.unlockWithBiometric(
                                        onSuccess = {
                                            navController.navigate(Routes.Dashboard.route) {
                                                popUpTo(Routes.PatternUnlock.route) { inclusive = true }
                                            }
                                        },
                                        onError = { err -> feedbackMessage = err },
                                    )
                                is BiometricAuthResult.Error -> feedbackMessage = result.message
                                BiometricAuthResult.Cancelled -> Unit // user backed out — no error noise
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = "Use biometrics",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Use biometrics instead",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
