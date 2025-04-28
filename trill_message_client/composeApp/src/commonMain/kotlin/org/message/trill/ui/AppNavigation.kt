package org.message.trill.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.message.trill.MessageClient

@Composable
fun AppNavigation(client: MessageClient) {
    Navigator(RegistrationScreenImpl(client))
}

class RegistrationScreenImpl(private val client: MessageClient) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        RegistrationScreen(
            onRegister = { email, nickname ->
                client.registerUser(email, nickname)
                client.registerDevice(email, nickname)
                client.loginUser(email, nickname)
                navigator.push(MainScreenImpl(client, email))
            },
            onLogin = { email, nickname ->
                client.loginUser(email, nickname)
                navigator.push(MainScreenImpl(client, email))
            }
        )
    }
}

class MainScreenImpl(private val client: MessageClient, private val userEmail: String) : Screen {
    @Composable
    override fun Content() {
        MainScreen(client, userEmail)
    }
}