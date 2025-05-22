package org.message.trill.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val contactNicknames = remember { mutableStateMapOf<String, String>() }

    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingContacts by remember { mutableStateOf(true) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var globalErrorMessage by remember { mutableStateOf<String?>(null) }

    val chatMessagesListState = rememberLazyListState()
    val contactListState = rememberLazyListState()

    suspend fun getOrFetchNickname(email: String): String {
        return contactNicknames[email] ?: client.getLocalUserNickname(email)?.also {
            contactNicknames[email] = it
            logger.debug("Fetched and cached nickname for $email: $it")
        } ?: email.also { logger.debug("Nickname for $email not found, using email.") }
    }

    // Initial data loading keyed to currentUserEmail
    LaunchedEffect(currentUserEmail) {
        logger.info("MainScreen: LaunchedEffect for initial load triggered for user: $currentUserEmail")
        isLoadingContacts = true // Set loading true at the start of the effect
        globalErrorMessage = null
        selectedContactEmail = null
        conversations.clear()
        contactNicknames.clear()
        searchResults = emptyList()

        try {
            logger.info("MainScreen: Attempting to load recent conversation partners for $currentUserEmail")
            val partners = client.getRecentConversationPartners(currentUserEmail)
            logger.info("MainScreen: Raw partners from client.getRecentConversationPartners for $currentUserEmail: $partners")

            if (partners.isEmpty()) {
                logger.info("MainScreen: No recent partners found for $currentUserEmail.")
            } else {
                partners.forEach { partnerEmail ->
                    conversations.putIfAbsent(partnerEmail, mutableStateListOf())
                    scope.launch {
                        getOrFetchNickname(partnerEmail)
                    }
                }
            }
            logger.info("MainScreen: Finished processing ${partners.size} recent partners for $currentUserEmail. Conversations map keys: ${conversations.keys}")
        } catch (e: Exception) {
            logger.error("MainScreen: Error loading recent contacts for $currentUserEmail: ${e.message}", e)
            globalErrorMessage = "Could not load contacts: ${e.message}"
        } finally {
            isLoadingContacts = false // Set loading false after all processing (success or fail)
            logger.info("MainScreen: isLoadingContacts set to false for $currentUserEmail. Current conversation keys: ${conversations.keys.joinToString()}")
        }
    }

    LaunchedEffect(selectedContactEmail) {
        selectedContactEmail?.let { contact ->
            isLoadingMessages = true
            globalErrorMessage = null
            try {
                logger.info("MainScreen: Loading messages for conversation with $contact")
                val messages = client.loadMessagesForConversation(currentUserEmail, contact)
                val messageList = messages.toMutableStateList()
                conversations[contact] = messageList
                if (messageList.isNotEmpty()) {
                    scope.launch { chatMessagesListState.animateScrollToItem(messageList.size - 1) }
                }
                logger.info("MainScreen: Loaded ${messageList.size} messages for $contact.")
            } catch (e: Exception) {
                logger.error("MainScreen: Error loading messages for $contact: ${e.message}", e)
                globalErrorMessage = "Could not load messages: ${e.message}"
            } finally {
                isLoadingMessages = false
            }
        }
    }

    LaunchedEffect(currentUserEmail) {
        logger.info("MainScreen: LaunchedEffect for polling started for user: $currentUserEmail")
        while (true) {
            try {
                val newReceivedMessages = client.receiveMessages(currentUserEmail)
                if (newReceivedMessages.isNotEmpty()) {
                    globalErrorMessage = null
                    logger.info("MainScreen: Received ${newReceivedMessages.size} new messages for $currentUserEmail.")
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
                    if (!messageList.any { it.content == uiMessage.content && it.timestamp == uiMessage.timestamp && !it.isSent }) {
                        messageList.add(uiMessage)
                        if (!contactNicknames.containsKey(contact)) {
                            scope.launch { getOrFetchNickname(contact) }
                        }
                        logger.debug("Added new received message from $contact. New message list size: ${messageList.size}")
                    }

                    if (contact == selectedContactEmail && messageList.isNotEmpty()) {
                        scope.launch {
                            chatMessagesListState.animateScrollToItem(messageList.size - 1)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("MainScreen: Error polling for messages for $currentUserEmail: ${e.message}", e)
            }
            delay(5000L)
        }
    }

    val allKnownContactEmails = remember(conversations.keys.toList(), searchResults) {
        logger.debug("Recalculating allKnownContactEmails. Conversation keys: ${conversations.keys.joinToString()}, Search results: ${searchResults.joinToString()}")
        (conversations.keys + searchResults).distinct()
    }

    val displayContactsAndInfo = remember(
        allKnownContactEmails,
        conversations.map { it.key to (it.value.lastOrNull()?.timestamp ?: "") }.hashCode(),
        contactNicknames.hashCode(),
        searchQuery
    ) {
        logger.debug("Recalculating displayContactsAndInfo. AllKnown: ${allKnownContactEmails.joinToString()}, Query: $searchQuery, NicknamesHash: ${contactNicknames.hashCode()}, ConvosLastMsgHash: ${conversations.map { it.key to (it.value.lastOrNull()?.timestamp ?: "") }.hashCode()}")
        allKnownContactEmails
            .mapNotNull { email ->
                val displayName = contactNicknames[email] ?: email
                if (searchQuery.isBlank() || displayName.contains(searchQuery, ignoreCase = true) || email.contains(searchQuery, ignoreCase = true)) {
                    val lastMsgObject = conversations[email]?.lastOrNull()
                    ContactDisplayInfo(
                        email = email,
                        displayName = displayName,
                        lastMessage = lastMsgObject?.content ?: if (conversations.containsKey(email)) "No messages yet" else "Tap to start chat",
                        lastMessageTimestamp = lastMsgObject?.timestamp ?: "",
                        avatarSeed = email
                    )
                } else {
                    null
                }
            }
            .sortedByDescending { it.lastMessageTimestamp.takeIf { ts -> ts.isNotBlank() } ?: "0000000000000" }
            .also { logger.debug("Finished recalculating displayContactsAndInfo. Count: ${it.size}. First item if any: ${it.firstOrNull()?.displayName}") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                    }
                },
                backgroundColor = MaterialTheme.colors.primarySurface
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            globalErrorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colors.error,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                        .background(MaterialTheme.colors.error.copy(alpha = 0.1f)).padding(8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                                            val results = client.searchUsersByEmail(searchQuery)
                                            results.forEach { email -> getOrFetchNickname(email) }
                                            searchResults = results
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
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                    )
                )
            }
            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 4.dp, top = 8.dp)) {
                    if (isLoadingContacts) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                            logger.debug("UI: Displaying loading indicator for contacts.")
                        }
                    } else if (displayContactsAndInfo.isEmpty() && searchQuery.isBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No conversations yet. Start a new one!")
                            logger.debug("UI: Displaying 'No conversations yet' message.")
                        }
                    }
                    else {
                        Box(modifier = Modifier.fillMaxHeight()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxHeight(),
                                state = contactListState
                            ) {
                                if (displayContactsAndInfo.isNotEmpty() && !isLoadingContacts) {
                                    logger.debug("UI: Rendering LazyColumn for contacts. Count: ${displayContactsAndInfo.size}")
                                }
                                items(displayContactsAndInfo, key = { it.email }) { contactInfo ->
                                    ContactCardItem(
                                        contactInfo = contactInfo,
                                        isSelected = contactInfo.email == selectedContactEmail,
                                        onClick = {
                                            selectedContactEmail = contactInfo.email
                                            if (!conversations.containsKey(contactInfo.email)) {
                                                conversations[contactInfo.email] = mutableStateListOf()
                                            }
                                            searchResults = emptyList()
                                            searchQuery = ""
                                        }
                                    )
                                    Divider(modifier = Modifier.padding(start = 60.dp))
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(scrollState = contactListState)
                            )
                        }
                    }
                }

                Divider(
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxHeight().width(1.dp)
                )

                if (selectedContactEmail != null) {
                    val currentChatPartnerEmail = selectedContactEmail!!
                    Column(modifier = Modifier.weight(2.5f).fillMaxHeight()) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = contactNicknames[currentChatPartnerEmail] ?: currentChatPartnerEmail,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                )
                            },
                            actions = {
                                IconButton(onClick = { /* TODO: Implement more chat options */ }) {
                                    Icon(Icons.Filled.MoreVert, "More options")
                                }
                            },
                            backgroundColor = MaterialTheme.colors.surface,
                            elevation = AppBarDefaults.TopAppBarElevation / 2
                        )
                        Divider()

                        if (isLoadingMessages) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            ChatArea(
                                messages = conversations[currentChatPartnerEmail] ?: emptyList(),
                                listState = chatMessagesListState,
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
                                val messageList = conversations.getOrPut(currentChatPartnerEmail) { mutableStateListOf() }
                                messageList.add(optimisticMessage)
                                if (messageList.isNotEmpty()) chatMessagesListState.animateScrollToItem(messageList.size - 1)

                                try {
                                    client.sendMessage(currentUserEmail, currentChatPartnerEmail, messageText)
                                    messageList.removeAll { it.id == tempId && it.timestamp == "Sending..." }

                                    val confirmedMsg = ConversationMessage(
                                        id = Clock.System.now().toEpochMilliseconds().toString(),
                                        content = messageText,
                                        isSent = true,
                                        timestamp = TimestampFormatter.format(Clock.System.now().toEpochMilliseconds())
                                    )
                                    if (!messageList.any { it.content == confirmedMsg.content && it.isSent }) {
                                        messageList.add(confirmedMsg)
                                    }
                                    if (messageList.isNotEmpty()) chatMessagesListState.animateScrollToItem(messageList.size - 1)

                                } catch (e: Exception) {
                                    globalErrorMessage = "Failed to send: ${e.message}"
                                    val index = messageList.indexOfFirst { it.id == tempId }
                                    if (index != -1) {
                                        messageList[index] = optimisticMessage.copy(timestamp = "Failed")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(2.5f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                isLoadingContacts -> "Loading contacts..."
                                displayContactsAndInfo.isEmpty() && searchQuery.isBlank() && !isLoadingContacts -> "Search for users to start a new chat."
                                else -> "Select or search a contact to chat."
                            }
                        )
                    }
                }
            }
        }
    }
}

data class ConversationMessage(val id: String, val content: String, val isSent: Boolean, val timestamp: String)