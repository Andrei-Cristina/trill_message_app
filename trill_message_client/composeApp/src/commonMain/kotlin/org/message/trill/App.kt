package org.message.trill

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import org.message.trill.MessageClient
import org.message.trill.ui.MainScreen
import org.message.trill.ui.RegistrationScreen

@Composable
fun App(clientFactory: (String) -> MessageClient) {
    MaterialTheme {
        var isRegistered by remember { mutableStateOf(false) }
        var client by remember { mutableStateOf<MessageClient?>(null) }
        val scope = rememberCoroutineScope()

        if (!isRegistered || client == null) {
            RegistrationScreen(
                onRegister = { email, nickname ->
                    client = clientFactory(email)
                    scope.launch {
                        try {
                            client?.registerUser(email, nickname)
                            client?.registerDevice(email, nickname)
                            isRegistered = true
                        } catch (e: Exception) {
                            println("Registration failed: ${e.message}")
                        }
                    }
                },
                onLogin = { email, nickname ->
                    client = clientFactory(email)
                    scope.launch {
                        try {
                            val deviceId = client?.loginUser(email, nickname)
                            if (deviceId != null) {
                                isRegistered = true
                            } else {
                                client?.registerDevice(email, nickname)
                                isRegistered = true
                            }
                        } catch (e: Exception) {
                            println("Login failed: ${e.message}")
                        }
                    }
                }
            )
        } else {
            MainScreen(client!!)
        }
    }
}