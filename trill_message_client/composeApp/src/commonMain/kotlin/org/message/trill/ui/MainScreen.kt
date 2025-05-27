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
import org.message.trill.encryption.utils.models.User
import org.slf4j.LoggerFactory
import kotlin.random.Random

data class ConversationMessage(val id: String, val content: String, val isSent: Boolean, val timestamp: String)

@Composable
fun MainScreen(
    client: MessageClient,
    currentUserEmail: String,
    onNavigateToProfile: () -> Unit
) {
    val logger = LoggerFactory.getLogger("MainScreen-$currentUserEmail")
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedContactEmail by remember { mutableStateOf<String?>(null) }

    val conversations = remember { mutableStateMapOf<String, SnapshotStateList<ConversationMessage>>() }
    val contactNicknames = remember { mutableStateMapOf<String, String>() }
    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }

    var isLoadingContacts by remember { mutableStateOf(true) }
    var isLoadingMessages by remember { mutableStateOf(false) }
    var globalErrorMessage by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    val chatMessagesListState = rememberLazyListState()
    val contactListState = rememberLazyListState()

    suspend fun getOrFetchNickname(email: String): String {
        return contactNicknames[email] ?: client.getLocalUserNickname(email)?.also {
            contactNicknames[email] = it
            logger.debug("Fetched and cached nickname for $email: $it")
        } ?: email.also { logger.debug("Nickname for $email not found, using email: $email") }
    }

    LaunchedEffect(currentUserEmail) {
        logger.info("LaunchedEffect (Initial Load) for $currentUserEmail: Starting.")
        isLoadingContacts = true
        globalErrorMessage = null
        selectedContactEmail = null
        conversations.clear()
        contactNicknames.clear()
        searchResults = emptyList()

        try {
            val partners = client.getRecentConversationPartners(currentUserEmail)
            logger.info("Initial Load: Raw partners for $currentUserEmail: $partners")

            if (partners.isNotEmpty()) {
                val initialConversations = mutableMapOf<String, SnapshotStateList<ConversationMessage>>()
                partners.forEach { partnerEmail ->
                    initialConversations[partnerEmail] = mutableStateListOf()
                    scope.launch { getOrFetchNickname(partnerEmail) }
                }
                conversations.putAll(initialConversations)
            } else {
                logger.info("Initial Load: No recent partners found for $currentUserEmail.")
            }
            logger.info("Initial Load: Finished processing partners. Conversation keys: ${conversations.keys.joinToString()}")
        } catch (e: Exception) {
            logger.error("Initial Load: Error loading recent contacts for $currentUserEmail: ${e.message}", e)
            globalErrorMessage = "Could not load contacts: ${e.message}"
        } finally {
            isLoadingContacts = false
            logger.info("Initial Load: isLoadingContacts set to false for $currentUserEmail.")
        }
    }

    LaunchedEffect(selectedContactEmail, currentUserEmail) {
        selectedContactEmail?.let { contact ->
            if (conversations[contact]?.isEmpty() == true || conversations[contact] == null) {
                isLoadingMessages = true
                globalErrorMessage = null
                logger.info("LaunchedEffect (Load Messages): Loading for $contact")
                try {
                    val messages = client.loadMessagesForConversation(currentUserEmail, contact)
                    conversations[contact] = messages.toMutableStateList()
                    if (messages.isNotEmpty()) {
                        scope.launch { chatMessagesListState.animateScrollToItem(messages.size - 1) }
                    }
                    logger.info("Load Messages: Loaded ${messages.size} messages for $contact.")
                } catch (e: Exception) {
                    logger.error("Load Messages: Error for $contact: ${e.message}", e)
                    globalErrorMessage = "Could not load messages for $contact: ${e.message}"
                } finally {
                    isLoadingMessages = false
                }
            } else if (conversations[contact]?.isNotEmpty() == true) {
                scope.launch { chatMessagesListState.animateScrollToItem(conversations[contact]!!.size - 1) }
            }
        }
    }

    LaunchedEffect(currentUserEmail) {
        if (isLoadingContacts) {
            delay(1000L)
        }
        logger.info("Polling for $currentUserEmail: Started.")
        while (true) {
            try {
                val newReceivedMessages = client.receiveMessages(currentUserEmail)
                if (newReceivedMessages.isNotEmpty()) {
                    globalErrorMessage = null
                    logger.info("Polling: Received ${newReceivedMessages.size} new messages.")
                }

                newReceivedMessages.forEach { receivedMsg ->
                    val contact = receivedMsg.senderId
                    val uiMessage = ConversationMessage(
                        id = "recv_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt()}",
                        content = receivedMsg.content,
                        isSent = false,
                        timestamp = TimestampFormatter.format(receivedMsg.timestamp.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds()),
                    )

                    val messageList = conversations.getOrPut(contact) {
                        logger.info("Polling: New contact '$contact' detected. Adding to conversations.")
                        scope.launch { getOrFetchNickname(contact) }
                        mutableStateListOf()
                    }

                    if (!messageList.any { it.id == uiMessage.id || (it.content == uiMessage.content && it.timestamp == uiMessage.timestamp && !it.isSent) }) {
                        messageList.add(uiMessage)
                        logger.debug("Polling: Added new message from $contact. List size: ${messageList.size}")
                    }

                    if (contact == selectedContactEmail && messageList.isNotEmpty()) {
                        scope.launch { chatMessagesListState.animateScrollToItem(messageList.size - 1) }
                    }
                }
            } catch (e: Exception) {
                logger.error("Polling: Error for $currentUserEmail: ${e.message}", e)
            }
            delay(3000L)
        }
    }

    val allKnownContactEmails = remember(conversations.keys.toList(), searchResults) {
        val searchEmails = searchResults.map { it.email }
        (conversations.keys + searchEmails).distinct().also {
            logger.debug("Recalculated allKnownContactEmails. Count: ${it.size}. Keys: [${it.joinToString()}]")
        }
    }

    val displayContactsAndInfo = remember(
        allKnownContactEmails,
        conversations.entries.associate { it.key to (it.value.lastOrNull()?.timestamp + it.value.size.toString()) },
        contactNicknames.toMap(),
        searchQuery,
        isSearching
    ) {
        logger.debug("Recalculating displayContactsAndInfo. Query: '$searchQuery'. allKnownContactEmails: ${allKnownContactEmails.size}, isSearching: $isSearching")
        allKnownContactEmails
            .mapNotNull { email ->
                val displayName = contactNicknames[email] ?: email
                if (searchQuery.isBlank() || displayName.contains(searchQuery, ignoreCase = true) || email.contains(searchQuery, ignoreCase = true)) {
                    val lastMsgObject = conversations[email]?.lastOrNull()
                    val lastMessageText = lastMsgObject?.content ?: run {
                        if (conversations.containsKey(email)) "No messages yet"
                        else "Tap to start chat"
                    }
                    ContactDisplayInfo(
                        email = email,
                        displayName = displayName,
                        lastMessage = lastMessageText,
                        lastMessageTimestamp = lastMsgObject?.timestamp ?: "",
                        avatarSeed = email
                    )
                } else { null }
            }
            .sortedWith(compareByDescending<ContactDisplayInfo> {
                when (it.lastMessageTimestamp) {
                    "Sending..." -> Long.MAX_VALUE.toString()
                    "Failed" -> (Long.MAX_VALUE - 1).toString()
                    "" -> "0"
                    else -> it.lastMessageTimestamp
                }
            }.thenBy { it.displayName })
            .also {
                logger.debug("Finished recalculating displayContactsAndInfo. Count: ${it.size}. First 5: ${it.take(5).map { c -> c.displayName }}")
            }
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
                    it, color = MaterialTheme.colors.error,
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
                                if (searchQuery.isNotBlank() && searchQuery.lowercase() != currentUserEmail.lowercase()) {
                                    scope.launch {
                                        globalErrorMessage = null
                                        isSearching = true
                                        try {
                                            val usersFound = client.searchUsersByEmail(searchQuery)
                                            searchResults = usersFound
                                            if (usersFound.isEmpty()) {
                                                globalErrorMessage = "No user found for '$searchQuery'."
                                            } else {
                                                usersFound.forEach { user ->
                                                    if (user.nickname.isNotBlank()) contactNicknames[user.email] = user.nickname
                                                }
                                            }
                                        } catch (e: Exception) {
                                            globalErrorMessage = "Search failed: ${e.message}"
                                            searchResults = emptyList()
                                        } finally { isSearching = false }
                                    }
                                } else if (searchQuery.lowercase() == currentUserEmail.lowercase()) {
                                    globalErrorMessage = "You cannot search for yourself."
                                }
                            }) {
                                if (isSearching) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                else Icon(Icons.Filled.Search, "Search users")
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
                            Text("Loading conversations...", modifier = Modifier.padding(top = 60.dp))
                        }
                    } else if (displayContactsAndInfo.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (searchQuery.isBlank()) "No conversations. Search to start!"
                                else "No users match '$searchQuery'."
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxHeight()) {
                            LazyColumn(modifier = Modifier.fillMaxHeight(), state = contactListState) {
                                items(displayContactsAndInfo, key = { it.email }) { contactInfo ->
                                    ContactCardItem(
                                        contactInfo = contactInfo,
                                        isSelected = contactInfo.email == selectedContactEmail,
                                        onClick = {
                                            selectedContactEmail = contactInfo.email
                                            conversations.putIfAbsent(contactInfo.email, mutableStateListOf())
                                            searchQuery = ""
                                            searchResults = emptyList()
                                        }
                                    )
                                    Divider(modifier = Modifier.padding(start = 72.dp))
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
                            title = { Text(contactNicknames[currentChatPartnerEmail] ?: currentChatPartnerEmail, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                            actions = { IconButton(onClick = { /* TODO */ }) { Icon(Icons.Filled.MoreVert, "More options") } },
                            backgroundColor = MaterialTheme.colors.surface,
                            elevation = AppBarDefaults.TopAppBarElevation / 2
                        )
                        Divider()

                        if (isLoadingMessages && (conversations[currentChatPartnerEmail]?.isEmpty() == true || conversations[currentChatPartnerEmail] == null)) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            ChatArea(
                                messages = conversations[currentChatPartnerEmail] ?: remember { mutableStateListOf() },
                                listState = chatMessagesListState,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                        MessageInput(
                            onSend = { messageText ->
                                scope.launch {
                                    val tempId = "sent_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt()}"
                                    val optimisticMessage = ConversationMessage(
                                        id = tempId,
                                        content = messageText,
                                        isSent = true,
                                        timestamp = "Sending..."
                                    )
                                    val messageList =
                                        conversations.getOrPut(currentChatPartnerEmail) { mutableStateListOf() }
                                    messageList.add(optimisticMessage)
                                    scope.launch {
                                        if (messageList.isNotEmpty()) chatMessagesListState.animateScrollToItem(
                                            messageList.size - 1
                                        )
                                    }

                                    try {
                                        client.sendMessage(currentUserEmail, currentChatPartnerEmail, messageText)
                                        val optimisticIndex = messageList.indexOfFirst { it.id == tempId }
                                        if (optimisticIndex != -1) {
                                            messageList[optimisticIndex] = messageList[optimisticIndex].copy(
                                                timestamp = TimestampFormatter.format(
                                                    Clock.System.now().toEpochMilliseconds()
                                                )
                                            )
                                        }
                                    } catch (e: Exception) {
                                        globalErrorMessage = "Failed to send: ${e.message?.take(100)}"
                                        val index = messageList.indexOfFirst { it.id == tempId }
                                        if (index != -1) messageList[index] =
                                            optimisticMessage.copy(timestamp = "Failed")
                                    }
                                }
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(2.5f).fillMaxHeight().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isLoadingContacts) "Loading..."
                            else if (displayContactsAndInfo.isEmpty() && searchQuery.isBlank()) "Search for users to start a new chat."
                            else "Select a contact to view messages.",
                            style = MaterialTheme.typography.subtitle1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                        )
                    }
                }
            }
        }
    }
}
