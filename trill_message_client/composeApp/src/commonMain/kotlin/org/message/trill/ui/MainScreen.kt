package org.message.trill.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.message.trill.MessageClient
import org.message.trill.encryption.utils.TimestampFormatter
import org.slf4j.LoggerFactory

@Composable
fun MainScreen(
    client: MessageClient,
    currentUserEmail: String,
    onNavigateToProfile: () -> Unit
) {
    val logger = LoggerFactory.getLogger("MainScreen")
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedContactEmail by remember { mutableStateOf<String?>(null) }

    val conversations = remember { mutableStateMapOf<String, SnapshotStateList<ConversationMessage>>() }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingContacts by remember { mutableStateOf(true) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var globalErrorMessage by remember { mutableStateOf<String?>(null) }

    val chatListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        isLoadingContacts = true
        globalErrorMessage = null
        try {
            logger.info("MainScreen: Loading recent conversation partners for $currentUserEmail")
            val partners = client.getRecentConversationPartners(currentUserEmail)
            partners.forEach { partnerEmail ->
                conversations.putIfAbsent(partnerEmail, mutableStateListOf())
            }
            logger.info("MainScreen: Loaded ${partners.size} recent partners.")
        } catch (e: Exception) {
            logger.error("MainScreen: Error loading recent contacts: ${e.message}", e)
            globalErrorMessage = "Could not load contacts: ${e.message}"
        } finally {
            isLoadingContacts = false
        }
    }

    LaunchedEffect(selectedContactEmail) {
        selectedContactEmail?.let { contact ->
            isLoadingMessages = true
            globalErrorMessage = null
            try {
                logger.info("MainScreen: Loading messages for conversation with $contact")
                val messages = client.loadMessagesForConversation(currentUserEmail, contact)
                conversations[contact] = messages.toMutableStateList()
                if (messages.isNotEmpty()) {
                    scope.launch { chatListState.animateScrollToItem(messages.size - 1) }
                }
                logger.info("MainScreen: Loaded ${messages.size} messages for $contact.")
            } catch (e: Exception) {
                logger.error("MainScreen: Error loading messages for $contact: ${e.message}", e)
                globalErrorMessage = "Could not load messages: ${e.message}"
            } finally {
                isLoadingMessages = false
            }
        }
    }

    LaunchedEffect(currentUserEmail) {
        while (true) {
            try {
                logger.debug("MainScreen: Polling for new messages for $currentUserEmail.")
                val newReceivedMessages = client.receiveMessages(currentUserEmail)
                if (newReceivedMessages.isNotEmpty()) {
                    globalErrorMessage = null
                    logger.info("MainScreen: Received ${newReceivedMessages.size} new messages.")
                }

                newReceivedMessages.forEach { receivedMsg ->
                    val contact = receivedMsg.senderId
                    val uiMessage = ConversationMessage(
                        id = Clock.System.now().toEpochMilliseconds().toString() + receivedMsg.content.hashCode(),
                        content = receivedMsg.content,
                        isSent = false,
                        timestamp = TimestampFormatter.format(receivedMsg.timestamp.toLongOrNull() ?: 0L)
                    )

                    val messageList = conversations.getOrPut(contact) { mutableStateListOf() }
                    if (!messageList.any { it.content == uiMessage.content && it.timestamp == uiMessage.timestamp }) {
                        messageList.add(uiMessage)
                    }

                    if (contact == selectedContactEmail && messageList.isNotEmpty()) {
                        scope.launch {
                            chatListState.animateScrollToItem(messageList.size - 1)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("MainScreen: Error polling for messages: ${e.message}", e)
            }
            delay(5000L)
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedContactEmail ?: "Messages") },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            globalErrorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.fillMaxWidth().padding(8.dp).background(MaterialTheme.colors.error.copy(alpha = 0.1f)).padding(8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search or start new chat (email)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                if (searchQuery.isNotBlank() && searchQuery != currentUserEmail) {
                                    scope.launch {
                                        globalErrorMessage = null
                                        try {
                                            searchResults = client.searchUsersByEmail(searchQuery)
                                            if (searchResults.isEmpty()) {
                                                globalErrorMessage = "No users found for '$searchQuery'."
                                            }
                                        } catch (e: Exception) {
                                            globalErrorMessage = "Search failed: ${e.message}"
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Search, "Search users")
                            }
                        }
                    }
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp)) {
                    if (isLoadingContacts) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxHeight()) {
                            val displayContacts = conversations.keys
                                .filter { it.contains(searchQuery, ignoreCase = true) || searchQuery.isBlank() }
                                .sorted()

                            items(displayContacts) { contactEmail ->
                                ConversationItem(
                                    contact = contactEmail,
                                    isSelected = contactEmail == selectedContactEmail,
                                    onClick = {
                                        selectedContactEmail = contactEmail
                                        searchResults = emptyList()
                                        searchQuery = ""
                                    }
                                )
                            }

                            val newSearchResults = searchResults.filter { it !in displayContacts && it != currentUserEmail }
                            if (newSearchResults.isNotEmpty()) {
                                item {
                                    Text("Search Results:", style = MaterialTheme.typography.subtitle2, modifier = Modifier.padding(8.dp))
                                }
                                items(newSearchResults) { email ->
                                    ConversationItem(
                                        contact = "Start chat with $email",
                                        isSelected = false,
                                        onClick = {
                                            selectedContactEmail = email
                                            conversations.putIfAbsent(email, mutableStateListOf())
                                            searchQuery = ""
                                            searchResults = emptyList()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                selectedContactEmail?.let { contact ->
                    Column(modifier = Modifier.weight(2f).fillMaxHeight()) {
                        if (isLoadingMessages) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            ChatArea(
                                messages = conversations[contact] ?: emptyList(),
                                listState = chatListState,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                        MessageInput { messageText ->
                            scope.launch {
                                val tempId = Clock.System.now().toEpochMilliseconds().toString() + messageText.hashCode()
                                val optimisticMessage = ConversationMessage(
                                    id = tempId,
                                    content = messageText,
                                    isSent = true,
                                    timestamp = "Sending..."
                                )
                                val messageList = conversations.getOrPut(contact) { mutableStateListOf() }
                                messageList.add(optimisticMessage)
                                if (messageList.isNotEmpty()) chatListState.animateScrollToItem(messageList.size - 1)

                                try {
                                    client.sendMessage(currentUserEmail, contact, messageText)
                                    messageList.remove(optimisticMessage)
                                    val updatedMessages = client.loadMessagesForConversation(currentUserEmail, contact)
                                    conversations[contact] = updatedMessages.toMutableStateList()
                                    if (updatedMessages.isNotEmpty()) chatListState.animateScrollToItem(updatedMessages.size -1)

                                } catch (e: Exception) {
                                    globalErrorMessage = "Failed to send: ${e.message}"
                                    messageList.find { it.id == tempId }?.let {
                                        val updated = it.copy(timestamp = "Failed to send")
                                        val index = messageList.indexOf(it)
                                        if(index != -1) messageList[index] = updated
                                    }
                                }
                            }
                        }
                    }
                } ?: Box(
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isLoadingContacts) "Loading contacts..." else "Select or search a contact to chat.")
                }
            }
        }
    }
}

data class ConversationMessage(val id: String, val content: String, val isSent: Boolean, val timestamp: String)