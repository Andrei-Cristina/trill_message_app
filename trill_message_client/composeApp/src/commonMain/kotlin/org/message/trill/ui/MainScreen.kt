package org.message.trill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.message.trill.MessageClient
import org.slf4j.LoggerFactory


@Composable
fun MainScreen(client: MessageClient, userEmail: String) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedConversation by remember { mutableStateOf<String?>(null) }
    var conversations by remember { mutableStateOf(mapOf<String, MutableList<ConversationMessage>>()) }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val logger = LoggerFactory.getLogger("MainScreen")

    Column(modifier = Modifier.fillMaxSize()) {
        errorMessage?.let { error ->
            Text(
                text = error,
                color = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search contacts by email") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        scope.launch {
                            try {
                                searchResults = client.searchUsersByEmail(searchQuery)
                                errorMessage = null
                            } catch (e: Exception) {
                                logger.error("Failed to search users for query: $searchQuery", e)
                                errorMessage = "Failed to search users. Please try again."
                                searchResults = emptyList()
                            }
                        }
                    } else {
                        searchResults = emptyList()
                        errorMessage = null
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Search")
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
                items(conversations.keys.filter {
                    searchQuery.isBlank() || it.contains(searchQuery, ignoreCase = true)
                }) { contact ->
                    ConversationItem(
                        contact = contact,
                        isSelected = contact == selectedConversation,
                        onClick = { selectedConversation = contact }
                    )
                }
                items(searchResults.filter { it !in conversations.keys }) { email ->
                    Button(onClick = {
                        selectedConversation = email
                        conversations = conversations.toMutableMap().apply {
                            this[email] = mutableListOf()
                        }
                    }) {
                        Text("Start conversation with $email")
                    }
                }
            }

            selectedConversation?.let { contact ->
                Column(modifier = Modifier.weight(2f).fillMaxHeight()) {
                    ChatArea(
                        messages = conversations[contact] ?: emptyList(),
                        modifier = Modifier.weight(1f)
                    )
                    MessageInput { message ->
                        scope.launch {
                            try {
                                client.sendMessage(userEmail, contact, message)

                                val updated = conversations.toMutableMap()

                                updated[contact] = (updated[contact] ?: mutableListOf()).apply {
                                    add(ConversationMessage(message, true, Clock.System.now().toString()))
                                }
                                conversations = updated
                                errorMessage = null
                            } catch (e: Exception) {
                                logger.error("Failed to send message to $contact", e)
                                println("Failed to send message to $contact, ${e.message}")
                                errorMessage = "Failed to send message. Please try again."
                            }
                        }
                    }
                }
            } ?: Box(
                modifier = Modifier.weight(2f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text("Select a conversation to start chatting")
            }
        }
    }

    LaunchedEffect(userEmail) {
        while (true) {
            try {
                val newMessages = client.receiveMessages(userEmail)
                conversations = conversations.toMutableMap().apply {
                    newMessages.forEach { msg ->
                        val contact = msg.senderId
                        this[contact] = (this[contact] ?: mutableListOf()).apply {
                            add(ConversationMessage(msg.content, false, msg.timestamp))
                        }
                    }
                }
                errorMessage = null
            } catch (e: Exception) {
                logger.error("Failed to receive messages for $userEmail", e)
                errorMessage = "Failed to receive messages. Retrying..."
            }
            delay(5000)
        }
    }
}

data class ConversationMessage(val content: String, val isSent: Boolean, val timestamp: String)