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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.message.trill.MessageClient
import org.message.trill.encryption.utils.TimestampFormatter
import org.message.trill.encryption.utils.models.User
import org.message.trill.messaging.models.ConversationMessage
import org.message.trill.messaging.models.ReceivedMessage
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.random.Random

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
        } ?: email.also { logger.debug("Nickname for $email not found, using email as display name.") }
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
            logger.info("Initial Load: Fetched ${partners.size} recent partners for $currentUserEmail.")

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
        } catch (e: Exception) {
            logger.error("Initial Load: Error loading recent contacts for $currentUserEmail: ${e.message}", e)
            globalErrorMessage = "Could not load contacts: ${e.message}"
        } finally {
            isLoadingContacts = false
            logger.info("Initial Load: Finished for $currentUserEmail. isLoadingContacts set to false.")
        }
    }

    LaunchedEffect(selectedContactEmail, currentUserEmail) {
        selectedContactEmail?.let { contact ->
            if (conversations[contact]?.isEmpty() == true || conversations[contact] == null) {
                isLoadingMessages = true
                globalErrorMessage = null
                logger.info("LaunchedEffect (Load Messages): Loading for $contact with $currentUserEmail")
                try {
                    val messages = client.loadMessagesForConversation(currentUserEmail, contact)
                    conversations[contact] = messages.toMutableStateList()
                    if (messages.isNotEmpty()) {
                        scope.launch {
                            try { chatMessagesListState.animateScrollToItem(messages.size - 1) }
                            catch (e: Exception) { logger.warn("Failed to scroll on initial message load: ${e.message}")}
                        }
                    }
                    logger.info("Load Messages: Loaded ${messages.size} messages for $contact.")
                } catch (e: Exception) {
                    logger.error("Load Messages: Error loading messages for $contact: ${e.message}", e)
                    globalErrorMessage = "Could not load messages for $contact: ${e.message}"
                } finally {
                    isLoadingMessages = false
                }
            } else if (conversations[contact]?.isNotEmpty() == true) {
                scope.launch {
                    try { chatMessagesListState.animateScrollToItem(conversations[contact]!!.size - 1) }
                    catch (e: Exception) { logger.warn("Failed to scroll on contact re-selection: ${e.message}")}
                }
            }
        }
    }

    LaunchedEffect(client, currentUserEmail) {
        logger.info("MainScreen starting to observe newMessageForUiFlow for $currentUserEmail")
        client.newMessageForUiFlow
            .collect { receivedMsg: ReceivedMessage ->
                logger.info("MainScreen received new message via Flow from ${receivedMsg.senderId} for ${receivedMsg.content.take(20)}...")
                globalErrorMessage = null

                if (receivedMsg.senderId == currentUserEmail) {
                    logger.info("Ignoring self-sent message received via WebSocket flow.")
                    return@collect
                }

                val contact = receivedMsg.senderId
                val uiMessage = ConversationMessage(
                    id = "recv_ws_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt()}",
                    content = receivedMsg.content,
                    isSent = false,
                    timestamp = TimestampFormatter.format(
                        receivedMsg.timestamp.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds()
                    ),
                    filePointer = receivedMsg.filePointer
                )

                val messageList = conversations.getOrPut(contact) {
                    logger.info("New message via flow for a new contact '$contact'. Adding to conversations map.")
                    scope.launch { getOrFetchNickname(contact) }
                    mutableStateListOf()
                }

                val isDuplicate = messageList.any {
                    !it.isSent && it.content == uiMessage.content &&
                            it.timestamp == uiMessage.timestamp
                }

                if (!isDuplicate) {
                    messageList.add(uiMessage)
                    logger.debug("Added new message from $contact via flow. List size for $contact: ${messageList.size}")

                    if (contact == selectedContactEmail && messageList.isNotEmpty()) {
                        scope.launch {
                            try {
                                chatMessagesListState.animateScrollToItem(messageList.size - 1)
                            } catch (e: Exception) {
                                logger.warn("Failed to animate scroll on new WS message: ${e.message}")
                            }
                        }
                    }
                } else {
                    logger.debug("Duplicate message detected via flow from $contact, not adding: ${uiMessage.content.take(30)}")
                }
            }
    }

    val allKnownContactEmails = remember(conversations.keys.toList(), searchResults) {
        val searchEmails = searchResults.map { it.email }
        (conversations.keys + searchEmails).distinct().also {
            logger.debug("Recalculated allKnownContactEmails. Count: ${it.size}")
        }
    }

    val displayContactsAndInfo = remember(
        allKnownContactEmails,
        conversations.entries.associate { it.key to (it.value.lastOrNull()?.id + it.value.size.toString()) },
        contactNicknames.toMap(),
        searchQuery
    ) {
        logger.debug("Recalculating displayContactsAndInfo. Query: '$searchQuery'. Known contacts: ${allKnownContactEmails.size}")
        allKnownContactEmails
            .mapNotNull { email ->
                val displayName = contactNicknames[email] ?: email
                if (searchQuery.isBlank() || displayName.contains(searchQuery, ignoreCase = true) || email.contains(searchQuery, ignoreCase = true)) {
                    val lastMsgObject = conversations[email]?.lastOrNull()
                    val lastMessageText = lastMsgObject?.content?.take(30) ?: run {
                        if (conversations.containsKey(email)) "No messages yet"
                        else "Tap to start chat"
                    }
                    ContactDisplayInfo(
                        email = email,
                        displayName = displayName,
                        lastMessage = lastMessageText,
                        lastMessageTimestamp = lastMsgObject?.timestamp ?: "0",
                        avatarSeed = email
                    )
                } else { null }
            }
            .sortedWith(compareByDescending<ContactDisplayInfo> {
                when (it.lastMessageTimestamp) {
                    "Sending..." -> Long.MAX_VALUE.toString()
                    "Failed" -> (Long.MAX_VALUE - 1).toString()
                    "0" -> "0"
                    else -> it.lastMessageTimestamp
                }
            }.thenBy { it.displayName })
            .also {
                logger.debug("Finished recalculating displayContactsAndInfo. Displayed count: ${it.size}")
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
                    onValueChange = { newQuery ->
                        searchQuery = newQuery
                        if (newQuery.isBlank()) {
                            searchResults = emptyList()
                        }
                    },
                    label = { Text("Search contacts or new email") },
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
                                        } finally {
                                            isSearching = false
                                        }
                                    }
                                } else if (searchQuery.lowercase() == currentUserEmail.lowercase()) {
                                    globalErrorMessage = "You cannot search for yourself."
                                }
                            }) {
                                if (isSearching) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                else Icon(Icons.Filled.Search, "Search users")
                            }
                        }
                    }
                )
            }
            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 4.dp, top = 8.dp)) {
                    if (isLoadingContacts && displayContactsAndInfo.isEmpty()) {
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
                                            if (selectedContactEmail != contactInfo.email) {
                                                selectedContactEmail = contactInfo.email
                                                conversations.putIfAbsent(contactInfo.email, mutableStateListOf())
                                            }
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
                            actions = { IconButton(onClick = { /* TODO: More options */ }) { Icon(Icons.Filled.MoreVert, "More options") } },
                            backgroundColor = MaterialTheme.colors.surface,
                            elevation = AppBarDefaults.TopAppBarElevation / 2
                        )
                        Divider()

                        if (isLoadingMessages && (conversations[currentChatPartnerEmail]?.isEmpty() == true || conversations[currentChatPartnerEmail] == null)) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                                Text("Loading messages...", modifier = Modifier.padding(top = 60.dp))
                            }
                        } else {
                            ChatArea(
                                messages = conversations[currentChatPartnerEmail] ?: remember { mutableStateListOf() },
                                listState = chatMessagesListState,
                                onDownloadClick = { filePointer ->
                                    scope.launch {
                                        try {
                                            client.downloadAndDecryptFile(filePointer)
                                        } catch (e: Exception) {
                                            globalErrorMessage = "Download failed: ${e.message}"
                                            logger.error("Download failed", e)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                        MessageInput(
                            onSendText = { messageText ->
                                scope.launch {
                                    val tempId = "sent_text_${Clock.System.now().toEpochMilliseconds()}"
                                    val optimisticMessage = ConversationMessage(
                                        id = tempId, content = messageText, isSent = true, timestamp = "Sending..."
                                    )
                                    val messageList = conversations.getOrPut(currentChatPartnerEmail) { mutableStateListOf() }
                                    messageList.add(optimisticMessage)
                                    scope.launch { if (messageList.isNotEmpty()) chatMessagesListState.animateScrollToItem(messageList.size - 1) }

                                    try {
                                        client.sendMessage(currentUserEmail, currentChatPartnerEmail, messageText)
                                        messageList.find { it.id == tempId }?.let {
                                            val index = messageList.indexOf(it)
                                            messageList[index] = it.copy(timestamp = TimestampFormatter.format(Clock.System.now().toEpochMilliseconds()))
                                        }
                                    } catch (e: Exception) {
                                        logger.error("Failed to send message", e)
                                        globalErrorMessage = "Failed to send: ${e.message?.take(100)}"
                                        messageList.find { it.id == tempId }?.let {
                                            val index = messageList.indexOf(it)
                                            messageList[index] = it.copy(timestamp = "Failed")
                                        }
                                    }
                                }
                            },
                            onSendFile = { filePath ->
                                scope.launch {
                                    val fileName = File(filePath).name
                                    val tempId = "sent_file_${Clock.System.now().toEpochMilliseconds()}"
                                    val optimisticMessage = ConversationMessage(
                                        id = tempId,
                                        content = "[File] Sending: $fileName",
                                        isSent = true,
                                        timestamp = "Sending...",
                                        filePointer = null
                                    )
                                    val messageList = conversations.getOrPut(currentChatPartnerEmail) { mutableStateListOf() }
                                    messageList.add(optimisticMessage)
                                    scope.launch { if (messageList.isNotEmpty()) chatMessagesListState.animateScrollToItem(messageList.size - 1) }

                                    try {
                                        client.sendFile(currentUserEmail, currentChatPartnerEmail, filePath)
                                        val newMessages = client.loadMessagesForConversation(currentUserEmail, currentChatPartnerEmail)
                                        conversations[currentChatPartnerEmail] = newMessages.toMutableStateList()
                                        scope.launch { if (newMessages.isNotEmpty()) chatMessagesListState.animateScrollToItem(newMessages.size - 1) }

                                    } catch (e: Exception) {
                                        logger.error("Failed to send file", e)
                                        globalErrorMessage = "Failed to send file: ${e.message}"
                                        messageList.find { it.id == tempId }?.let {
                                            val index = messageList.indexOf(it)
                                            messageList[index] = it.copy(timestamp = "Failed")
                                        }
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
                            if (isLoadingContacts) "Loading contacts..."
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
