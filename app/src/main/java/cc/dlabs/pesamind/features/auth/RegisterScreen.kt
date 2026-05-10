package cc.dlabs.pesamind.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.dlabs.pesamind.core.navigation.Routes
import cc.dlabs.pesamind.core.network.ApiClient
import cc.dlabs.pesamind.core.network.models.CreateChannelRequest
import cc.dlabs.pesamind.core.network.models.RegisterRequest
import cc.dlabs.pesamind.core.storage.ChannelManager.getChannels
import cc.dlabs.pesamind.core.storage.ChannelManager.saveChannels
import kotlinx.coroutines.launch
import kotlin.collections.plus

@Composable
fun RegisterScreen(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val initialChannel = "Initial Cash"
    val description = "This is the initial channel that is created with the account"
    val initialChannelType = "Cash"
    val initialChannelDesc = "This is the channel type that is created with the account"


    val scope = rememberCoroutineScope()
    val teal = MaterialTheme.colorScheme.primary

    fun doRegister() {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = ApiClient.api.register(RegisterRequest(username, email, password))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.id != null) {
                        val initialChannelResponse = ApiClient.api.createChannel(CreateChannelRequest(initialChannel, initialChannelType, description, initialChannelDesc, true))
                        if (initialChannelResponse.isSuccessful) {
                            val channels = getChannels()
                            initialChannelResponse.body()?.also {
                                // Refresh local cache
                                val updatedChannels = channels + it
                                saveChannels(updatedChannels)
                            }
                        } else {
                            null
                        }
                        navController.navigate("login")
                    } else {
                        errorMessage = body?.error ?: "Registration failed"
                    }

                } else {
                    errorMessage = when (response.code()) {
                        409 -> "Email already in use"
                        400 -> "Invalid details"
                        else -> "Registration failed (${response.code()})"
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
        Text("Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = teal)
        Text(
            "Sign up to get started",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            isError = errorMessage != null
        )

        Spacer(Modifier.height(16.dp))

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
            onClick = { doRegister() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = teal),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text("Sign Up", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { navController.navigate(Routes.Login.route) }) {
            Text("Already have an account? ", color = Color.Gray)
            Text("Sign In", color = teal, fontWeight = FontWeight.SemiBold)
        }
    }
}
