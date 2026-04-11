package cc.dlabs.pesamind.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.AuthProfile
import cc.dlabs.pesamind.core.network.models.LoginRequest
import cc.dlabs.pesamind.core.storage.AccountManager
import cc.dlabs.pesamind.core.storage.TokenManager
import cc.dlabs.pesamind.core.storage.TokenManager.LockState
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val teal = MaterialTheme.colorScheme.primary

    fun doLogin() {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = ApiClient.api.login(LoginRequest(email.trim(), password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.accessToken != null && body.refreshToken != null) {
                        // Save tokens
                        TokenManager.saveTokens(body.accessToken, body.refreshToken)

                         if (body.profile != null) {
                             AccountManager.saveAccount(
                                 body.profile.id ?: "",
                                 email = email.trim(),
                                 body.profile.username ?: "",
                                 body.profile.balance?.toString() ?: "",
                                 body.profile.type ?: ""
                             )
                         }

                        // Check if user has set PIN or pattern
                        val destination = when (TokenManager.getLockState()) {
                            LockState.NONE -> Routes.LockSetup.route
                            LockState.PIN -> Routes.PinUnlock.route
                            LockState.PATTERN -> Routes.PatternUnlock.route
                        }
                        navController.navigate(destination) {
                            popUpTo(Routes.Login.route) { inclusive = true }
                        }
                    } else {
                        errorMessage = body?.error ?: "Invalid email or password"
                    }
                } else {
                    errorMessage = when (response.code()) {
                        401 -> "Invalid email or password"
                        404 -> "Account not found"
                        else -> "Login failed (${response.code()})"
                    }
                }
            } catch (t: Throwable) {
                errorMessage = when (t) {
                    is ExceptionInInitializerError -> "Check API base URL in ApiClient (invalid host or format)."
                    else -> "Cannot reach server. Check your connection."
                }
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome Back", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E2240))
        Text(
            "Sign in to your account",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            isError = errorMessage != null
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            isError = errorMessage != null
        )

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { doLogin() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = teal),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { navController.navigate(Routes.Register.route) }) {
            Text("Don't have an account? ", color = Color.Gray)
            Text("Sign Up", color = teal, fontWeight = FontWeight.SemiBold)
        }
    }
}