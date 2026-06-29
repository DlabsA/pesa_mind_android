package cc.dlabs.pesamind.features.auth

// Features/Auth/Views/RegisterScreen.kt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun RegisterScreen(
    navController: NavHostController,
    vm: AuthViewModel = viewModel(),
) {
    val form      by vm.registerForm.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()

    val focusManager  = LocalFocusManager.current
    val emailFocus    = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    val isLoading    = authState is AuthUiState.Loading
    val errorMessage = (authState as? AuthUiState.Error)?.message

    // Navigate to login on success
    LaunchedEffect(authState) {
        if (authState is AuthUiState.RegisterSuccess) {
            navController.navigate(Routes.Login.route) {
                popUpTo(Routes.Register.route) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {

        // ── Hero ──────────────────────────────────────────────────────────────
        LoginHero()

        // ── Form card ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .offset(y = (-100).dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // Error banner
            AnimatedVisibility(
                visible = errorMessage != null,
                enter   = fadeIn() + slideInVertically { -it / 2 },
                exit    = fadeOut(),
            ) {
                errorMessage?.let { ErrorBanner(message = it) }
            }

            Text(
                text       = "Create Account",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
            )

            // Username — ImeAction.Next → moves to Email
            AuthTextField(
                value         = form.username,
                onValueChange = vm::onRegisterUsernameChange,
                label         = "Username",
                placeholder   = "Your display name",
                leadingIcon   = Icons.Outlined.Person,
                errorMessage  = form.usernameError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction    = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { emailFocus.requestFocus() }
                ),
            )

            // Email — ImeAction.Next → moves to Password
            AuthTextField(
                value         = form.email,
                onValueChange = vm::onRegisterEmailChange,
                label         = "Email address",
                placeholder   = "you@example.com",
                leadingIcon   = Icons.Outlined.Email,
                errorMessage  = form.emailError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocus.requestFocus() }
                ),
                modifier = Modifier.focusRequester(emailFocus),
            )

            // Password — ImeAction.Go → submit
            var passwordVisible by remember { mutableStateOf(false) }
            AuthTextField(
                value         = form.password,
                onValueChange = vm::onRegisterPasswordChange,
                label         = "Password",
                placeholder   = "At least 6 characters",
                leadingIcon   = Icons.Outlined.Lock,
                errorMessage  = form.passwordError,
                trailingIcon  = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                onTrailingIconClick = { passwordVisible = !passwordVisible },
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        focusManager.clearFocus()
                        vm.register()
                    }
                ),
                modifier = Modifier.focusRequester(passwordFocus),
            )

            // Sign up button
            Button(
                onClick  = {
                    focusManager.clearFocus()
                    vm.register()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                enabled  = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        strokeWidth = 2.dp,
                        modifier    = Modifier.size(20.dp),
                    )
                } else {
                    Text("Sign Up", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Already have account
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { navController.navigate(Routes.Login.route) }) {
                    Text("Already have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text("Sign In", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}