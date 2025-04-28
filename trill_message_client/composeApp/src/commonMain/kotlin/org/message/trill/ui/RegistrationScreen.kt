package org.message.trill.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun RegistrationScreen(
    onRegister: suspend (String, String) -> Unit,
    onLogin: suspend (String, String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isLoginMode) "Login" else "Register",
            style = MaterialTheme.typography.h4
        )
        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Nickname") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            RadioButton(
                selected = !isLoginMode,
                onClick = { isLoginMode = false }
            )
            Text("Register", modifier = Modifier.padding(end = 16.dp))
            RadioButton(
                selected = isLoginMode,
                onClick = { isLoginMode = true }
            )
            Text("Login")
        }

        Button(
            onClick = {
                scope.launch {
                    try {
                        if (isLoginMode) {
                            onLogin(email, nickname)
                        } else {
                            onRegister(email, nickname)
                        }
                        errorMessage = null
                    } catch (e: Exception) {
                        errorMessage = "Operation failed: ${e.message}"
                    }
                }
            },
            enabled = email.isNotBlank() && nickname.isNotBlank()
        ) {
            Text(if (isLoginMode) "Login" else "Register")
        }
    }
}
