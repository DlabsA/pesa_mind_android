package cc.dlabs.pesamind.features.auth

// Features/Auth/Views/LoginScreen.kt
import cc.dlabs.pesamind.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
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
fun LoginScreen(
    navController: NavHostController,
    vm: AuthViewModel = viewModel(),
) {
    val form      by vm.loginForm.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()

    val focusManager    = LocalFocusManager.current
    val passwordFocus   = remember { FocusRequester() }

    val isLoading    = authState is AuthUiState.Loading
    val errorMessage = (authState as? AuthUiState.Error)?.message

    // Navigate on success
    LaunchedEffect(authState) {
        if (authState is AuthUiState.LoginSuccess) {
            val destination = (authState as AuthUiState.LoginSuccess).destination
            navController.navigate(destination) {
                popUpTo(Routes.Login.route) { inclusive = true }
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

        // ── Form card (overlaps hero by 30dp, matching Swift's .offset(y: -30)) ──
        Column(
            modifier = Modifier
                .offset(y = (-80).dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
                text       = "Welcome Back",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
            )

            // Email
            AuthTextField(
                value         = form.email,
                onValueChange = vm::onLoginEmailChange,
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
            )

            // Password
            var passwordVisible by remember { mutableStateOf(false) }
            AuthTextField(
                value         = form.password,
                onValueChange = vm::onLoginPasswordChange,
                label         = "Password",
                placeholder   = "Enter your password",
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
                        vm.login()
                    }
                ),
                modifier = Modifier.focusRequester(passwordFocus),
            )

            // Forgot password
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = { /* TODO: navigate to reset */ }) {
                    Text(
                        text  = "Forgot password?",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                    )
                }
            }

            // Sign in button
            Button(
                onClick  = {
                    focusManager.clearFocus()
                    vm.login()
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
                    Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Divider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("or", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Create account
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { navController.navigate(Routes.Register.route) }) {
                    Text("Don't have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text("Create one", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────

@Composable
fun LoginHero() {
    Box(
        modifier        = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative circles — matches Swift's Circle().fill(white.opacity(0.06))
        Box(
            Modifier
                .size(160.dp)
                .offset(x = 120.dp, y = (-30).dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.06f))
        )
        Box(
            Modifier
                .size(100.dp)
                .offset(x = (-100).dp, y = 10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.05f))
        )

        // Logo placeholder — swap for your actual Image("Logo") asset
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "App Logo",
                modifier = Modifier.size(100.dp)
            )
        }
    }
}