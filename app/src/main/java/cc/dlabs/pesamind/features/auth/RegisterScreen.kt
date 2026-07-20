package cc.dlabs.pesamind.features.auth

// Features/Auth/Views/RegisterScreen.kt

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val form by vm.registerForm.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()

    // ── Google Sign-In (Signup Flow) ───────────────────────────────────────────
    // Each screen has its own GoogleSignInManager instance to avoid state conflicts
    val focusManager = LocalFocusManager.current
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val context = LocalContext.current

    val googleSignInManager =
        remember {
            Log.d("RegisterScreen", "Creating GoogleSignInManager for SIGNUP flow")
            GoogleSignInManager(context)
        }
    val isGoogleSignInConfigured =
        remember(googleSignInManager) {
            googleSignInManager.isConfigured
        }

    val googleSignInLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            Log.d("RegisterScreen", "Google Sign-In result code: ${result.resultCode}, data: ${result.data}")

            val data = result.data
            if (data == null) {
                Log.e("RegisterScreen", "Google Sign-In returned null data")
                vm.handleGoogleSignInError("Google Sign-In was canceled")
                return@rememberLauncherForActivityResult
            }

            // Note: Google returns RESULT_CANCELED (0) with valid data when using ActivityResultContracts
            // So we ignore the result code and try to extract the account from the intent
            val signInResult = googleSignInManager.handleSignInResult(result.resultCode, data)

            when (signInResult) {
                is GoogleSignInResult.Success -> {
                    Log.d("RegisterScreen", "Google Sign-In successful: ${signInResult.email}")
                    // Continue with backend mobile-signin strategy
                    vm.handleGoogleSignIn(
                        email = signInResult.email,
                        googleId = signInResult.googleId,
                        displayName = signInResult.displayName,
                        profilePhotoUrl = signInResult.profilePhotoUrl,
                    )
                }
                is GoogleSignInResult.Error -> {
                    Log.e("RegisterScreen", "Google Sign-In failed: ${signInResult.message}")
                    vm.handleGoogleSignInError(signInResult.message)
                }
            }
        }

    val isLoading = authState is AuthUiState.Loading
    val errorMessage = (authState as? AuthUiState.Error)?.message

    // Navigate on success
    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.RegisterSuccess -> {
                navController.navigate(Routes.Login.route) {
                    popUpTo(Routes.Register.route) { inclusive = true }
                }
            }
            is AuthUiState.GoogleSignupSuccess -> {
                navController.navigate(Routes.Dashboard.route) {
                    popUpTo(Routes.Register.route) { inclusive = true }
                }
            }
            is AuthUiState.LoginSuccess -> {
                // User signed in via Google and is an existing user
                // Navigate to the appropriate destination (lock setup, pin unlock, etc)
                Log.d(
                    "RegisterScreen",
                    "Existing user signed in via Google, navigating to ${(authState as AuthUiState.LoginSuccess).destination}",
                )
                navController.navigate((authState as AuthUiState.LoginSuccess).destination) {
                    popUpTo(Routes.Register.route) { inclusive = true }
                }
            }
            else -> {}
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Hero ──────────────────────────────────────────────────────────────
        LoginHero()

        // ── Form card ─────────────────────────────────────────────────────────
        Column(
            modifier =
                Modifier
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
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut(),
            ) {
                errorMessage?.let { ErrorBanner(message = it) }
            }

            Text(
                text = "Create Account",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Username — ImeAction.Next → moves to Email
            AuthTextField(
                value = form.username,
                onValueChange = vm::onRegisterUsernameChange,
                label = "Username",
                placeholder = "Your display name",
                leadingIcon = Icons.Outlined.Person,
                errorMessage = form.usernameError,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = { emailFocus.requestFocus() },
                    ),
            )

            // Email — ImeAction.Next → moves to Password
            AuthTextField(
                value = form.email,
                onValueChange = vm::onRegisterEmailChange,
                label = "Email address",
                placeholder = "you@example.com",
                leadingIcon = Icons.Outlined.Email,
                errorMessage = form.emailError,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = { passwordFocus.requestFocus() },
                    ),
                modifier = Modifier.focusRequester(emailFocus),
            )

            // Password — ImeAction.Go → submit
            var passwordVisible by remember { mutableStateOf(false) }
            AuthTextField(
                value = form.password,
                onValueChange = vm::onRegisterPasswordChange,
                label = "Password",
                placeholder = "At least 6 characters",
                leadingIcon = Icons.Outlined.Lock,
                errorMessage = form.passwordError,
                trailingIcon = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                onTrailingIconClick = { passwordVisible = !passwordVisible },
                visualTransformation =
                    if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            vm.register()
                        },
                    ),
                modifier = Modifier.focusRequester(passwordFocus),
            )

            // Sign up button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    vm.register()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text("Sign Up", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Divider - only show if Google Sign-In is available
            if (isGoogleSignInConfigured) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("or", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Google Sign-Up Button - only show if configured
            if (isGoogleSignInConfigured) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        googleSignInManager.getSignInIntentAfterSignOut { intent ->
                            if (intent != null) {
                                googleSignInLauncher.launch(intent)
                            } else {
                                Log.e("RegisterScreen", "Google Sign-In intent is null")
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFFFFF),
                            contentColor = Color(0xFF1F2937),
                        ),
                    enabled = !isLoading,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        content = {
                            // Google logo placeholder
                            Box(
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White),
                            )
                            Text(
                                "Sign up with Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        },
                    )
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
