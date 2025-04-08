package org.message.trill.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.message.trill.MessageClient

@Composable
fun MainScreen(client: MessageClient) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedConversation by remember { mutableStateOf<String?>(null) }
    var conversations by remember { mutableStateOf(mapOf<String, List<String>>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            val messages = client.receiveMessages()
            val updatedConversations = conversations.toMutableMap()
            messages.forEach { msg ->
                val sender = msg.split(":", limit = 2)[0].trim()
                val content = msg.split(":", limit = 2)[1].trim()
                updatedConversations[sender] = (updatedConversations[sender] ?: emptyList()) + content
            }
            conversations = updatedConversations
            kotlinx.coroutines.delay(5000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search contacts (email or nickname)") },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)
            ) {
                items(conversations.keys.filter {
                    searchQuery.isBlank() || it.contains(searchQuery, ignoreCase = true)
                }) { contact ->
                    ConversationItem(
                        contact = contact,
                        isSelected = contact == selectedConversation,
                        onClick = { selectedConversation = contact }
                    )
                }
            }

            Column(
                modifier = Modifier.weight(2f).fillMaxHeight().padding(8.dp)
            ) {
                if (selectedConversation != null) {
                    ChatArea(
                        messages = conversations[selectedConversation] ?: emptyList(),
                        modifier = Modifier.weight(1f)
                    )
                    MessageInput { message ->
                        scope.launch {
                            client.sendMessage(selectedConversation!!, message)
                            val updated = conversations.toMutableMap()
                            updated[selectedConversation!!] = (updated[selectedConversation!!] ?: emptyList()) + message
                            conversations = updated
                        }
                    }
                } else {
                    Text(
                        "Select a conversation to start chatting",
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
                    )
                }
            }
        }
    }
}
