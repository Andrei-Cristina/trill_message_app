package org.message.trill.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLogin: suspend (email: String) -> Unit, // Made suspend
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope() // For launching suspend onLogin

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Login", style = MaterialTheme.typography.h4)
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
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isNotBlank()) {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            onLogin(email)
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Login failed."
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    errorMessage = "Email cannot be empty."
                }
            },
            enabled = email.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colors.onPrimary)
            } else {
                Text("Login")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "You don't have an account? Register",
            modifier = Modifier.clickable { if (!isLoading) onNavigateToRegister() },
            color = MaterialTheme.colors.primary,
            textDecoration = TextDecoration.Underline
        )
    }
}