package org.message.trill.ui


import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.message.trill.MessageClient

@Composable
fun ProfileScreen(
    userEmail: String,
    client: MessageClient,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    var nickname by remember { mutableStateOf("Loading...") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(userEmail) {
        scope.launch {
            nickname = client.getLocalUserNickname(userEmail) ?: userEmail
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text("My Profile", style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Email: $userEmail", style = MaterialTheme.typography.body1)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Nickname: $nickname", style = MaterialTheme.typography.body1)
            // TODO: Add fields for editing nickname, profile picture, etc.

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch {
                        try {
                            client.userLogOut()
                            onLogout()
                        } catch (e: Exception) {
                            println("Error during logout process: ${e.message}")
                            onLogout()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
            ) {
                Icon(Icons.Filled.ExitToApp, contentDescription = "Logout Icon", tint = MaterialTheme.colors.onError)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", color = MaterialTheme.colors.onError)
            }
        }
    }
}