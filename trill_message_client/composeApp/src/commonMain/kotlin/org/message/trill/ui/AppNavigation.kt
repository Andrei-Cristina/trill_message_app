package org.message.trill.ui

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.message.trill.MessageClient


@Composable
fun AppNavigation(client: MessageClient) {
    Navigator(LoginScreenHost(client))
}

class LoginScreenHost(private val client: MessageClient) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        LoginScreen(
            onLogin = { email ->
                client.loginUser(email)
                navigator.replaceAll(MainScreenHost(client, email))
            },
            onNavigateToRegister = {
                navigator.push(RegisterScreenHost(client))
            }
        )
    }
}

class RegisterScreenHost(private val client: MessageClient) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        RegisterScreen(
            onRegister = { email, nickname ->
                client.registerUser(email, nickname)
                client.loginUser(email)
                client.registerDevice(email, nickname)
                navigator.replaceAll(MainScreenHost(client, email))
            },
            onNavigateToLogin = {
                if (navigator.canPop) navigator.pop()
                else navigator.replaceAll(LoginScreenHost(client))
            }
        )
    }
}

class MainScreenHost(
    private val client: MessageClient,
    private val currentUserEmail: String
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        MainScreen(
            client = client,
            currentUserEmail = currentUserEmail,
            onNavigateToProfile = {
                navigator.push(ProfileScreenHost(client, currentUserEmail))
            }
        )
    }
}

class ProfileScreenHost(
    private val client: MessageClient,
    private val userEmail: String
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        ProfileScreen(
            userEmail = userEmail,
            client = client,
            onBack = { navigator.pop() },
            onLogout = {
                // TODO: client.clearUserSession() if needed
                navigator.replaceAll(LoginScreenHost(client))
            }
        )
    }
}