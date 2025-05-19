package org.message.trill.ui


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    onRegister: suspend (email: String, nickname: String) -> Unit, // Made suspend
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Register", style = MaterialTheme.typography.h4)
        Spacer(modifier = Modifier.height(24.dp))

        errorMessage?.let {
            Text(it, color = MaterialTheme.colors.error, modifier = Modifier.padding(bottom = 8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(0.8f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it.trim() },
            label = { Text("Nickname") },
            modifier = Modifier.fillMaxWidth(0.8f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isNotBlank() && nickname.isNotBlank()) {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            onRegister(email, nickname)
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Registration failed."
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    errorMessage = "Email and Nickname cannot be empty."
                }
            },
            enabled = email.isNotBlank() && nickname.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colors.onPrimary)
            } else {
                Text("Register")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Already have an account? Login",
            modifier = Modifier.clickable { if (!isLoading) onNavigateToLogin() },
            color = MaterialTheme.colors.primary,
            textDecoration = TextDecoration.Underline
        )
    }
}