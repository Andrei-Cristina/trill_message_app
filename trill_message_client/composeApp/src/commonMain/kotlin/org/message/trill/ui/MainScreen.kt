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


//@Composable
//fun MainScreen(
//    client: MessageClient,
//    currentUserEmail: String,
//    onNavigateToProfile: () -> Unit
//) {
//    val logger = LoggerFactory.getLogger("MainScreen-$currentUserEmail") // More specific logger
//    val scope = rememberCoroutineScope()
//
//    var searchQuery by remember { mutableStateOf("") }
//    var selectedContactEmail by remember { mutableStateOf<String?>(null) }
//
//    // These will persist as long as MainScreen is in composition with the same key
//    val conversations = remember { mutableStateMapOf<String, SnapshotStateList<ConversationMessage>>() }
//    val contactNicknames = remember { mutableStateMapOf<String, String>() }
//
//    var searchResults by remember { mutableStateOf<List<User>>(emptyList()) }
//    var isLoadingContacts by remember { mutableStateOf(true) }
//    var isLoadingMessages by remember { mutableStateOf(false) }
//    var globalErrorMessage by remember { mutableStateOf<String?>(null) }
//    var isSearching by remember { mutableStateOf(false) }
//
//    // CHANGE 1: Flag to ensure initial load logic runs only once per currentUserEmail actual change
//    // This prevents clearing data if MainScreen recomposes for other reasons (e.g., navigation)
//    // but the user is still the same.
//    var initialLoadForCurrentUserDone by remember(currentUserEmail) { mutableStateOf(false) }
//
//
//    val chatMessagesListState = rememberLazyListState()
//    val contactListState = rememberLazyListState()
//
//    suspend fun getOrFetchNickname(email: String): String {
//        return contactNicknames[email] ?: client.getLocalUserNickname(email)?.also {
//            contactNicknames[email] = it
//            logger.debug("Fetched and cached nickname for $email: $it")
//        } ?: email.also { logger.debug("Nickname for $email not found, using email.") }
//    }
//
//    // Effect for initial loading of contacts/conversations
//    LaunchedEffect(currentUserEmail) {
//        // CHANGE 2: Use the initialLoadForCurrentUserDone flag
//        if (!initialLoadForCurrentUserDone) {
//            logger.info("LaunchedEffect (Initial Load) for $currentUserEmail: Starting.")
//            isLoadingContacts = true
//            globalErrorMessage = null
//            // Clear state ONLY if it's a new user or first time for this user.
//            // If navigating back and currentUserEmail is the same, these maps should retain their state.
//            // The remember(currentUserEmail) for initialLoadForCurrentUserDone handles new user scenario.
//            conversations.clear()
//            contactNicknames.clear()
//            searchResults = emptyList()
//            selectedContactEmail = null // Reset selected contact when user changes
//
//            try {
//                logger.info("Attempting to load recent conversation partners for $currentUserEmail")
//                val partners = client.getRecentConversationPartners(currentUserEmail)
//                logger.info("Raw partners from client for $currentUserEmail: $partners")
//
//                if (partners.isEmpty()) {
//                    logger.info("No recent partners found for $currentUserEmail.")
//                } else {
//                    partners.forEach { partnerEmail ->
//                        // Ensure an empty list exists for each partner to be populated later or by polling
//                        conversations.putIfAbsent(partnerEmail, mutableStateListOf())
//                        scope.launch {
//                            getOrFetchNickname(partnerEmail) // Pre-fetch nicknames
//                        }
//                    }
//                }
//                logger.info("Finished processing ${partners.size} recent partners for $currentUserEmail. Conversation map keys: ${conversations.keys}")
//            } catch (e: Exception) {
//                logger.error("Error loading recent contacts for $currentUserEmail: ${e.message}", e)
//                globalErrorMessage = "Could not load contacts: ${e.message}"
//            } finally {
//                isLoadingContacts = false
//                initialLoadForCurrentUserDone = true // Mark initial load as done for this user
//                logger.info("isLoadingContacts set to false for $currentUserEmail. initialLoadForCurrentUserDone = true. Current conversation keys: ${conversations.keys.joinToString()}")
//            }
//        } else {
//            logger.info("LaunchedEffect (Initial Load) for $currentUserEmail: Skipped, initialLoadForCurrentUserDone is true.")
//            // If we are recomposing but the user is the same, and load is done,
//            // we might want to refresh existing contacts if needed, but not clear everything.
//            // For now, polling handles new messages. If contacts list needs refresh, add logic here.
//            // Example: if selectedContactEmail is null and conversations is not empty, select the first one.
//            if (selectedContactEmail == null && conversations.keys.isNotEmpty()) {
//                // Potentially auto-select the first contact if none is selected after returning to the screen
//                // selectedContactEmail = conversations.keys.firstOrNull()
//                // logger.info("Auto-selected first contact: $selectedContactEmail as none was selected.")
//            }
//        }
//    }
//
//    // Effect for loading messages when a contact is selected
//    LaunchedEffect(selectedContactEmail, currentUserEmail) { // Add currentUserEmail to re-trigger if user changes
//        selectedContactEmail?.let { contact ->
//            if (conversations[contact]?.isEmpty() == true || conversations[contact] == null) { // Load only if not already loaded or empty
//                isLoadingMessages = true
//                globalErrorMessage = null // Clear previous message loading errors
//                logger.info("LaunchedEffect (Load Messages): Loading messages for $currentUserEmail with $contact")
//                try {
//                    val messages = client.loadMessagesForConversation(currentUserEmail, contact)
//                    val messageList = messages.toMutableStateList()
//                    conversations[contact] = messageList // Replace or set the message list
//                    if (messageList.isNotEmpty()) {
//                        scope.launch { chatMessagesListState.animateScrollToItem(messageList.size - 1) }
//                    }
//                    logger.info("Loaded ${messageList.size} messages for $contact.")
//                } catch (e: Exception) {
//                    logger.error("Error loading messages for $contact: ${e.message}", e)
//                    globalErrorMessage = "Could not load messages for $contact: ${e.message}"
//                } finally {
//                    isLoadingMessages = false
//                }
//            } else {
//                logger.info("LaunchedEffect (Load Messages): Messages for $contact already loaded or list not empty, scrolling if needed.")
//                conversations[contact]?.let {
//                    if (it.isNotEmpty()) scope.launch { chatMessagesListState.animateScrollToItem(it.size - 1) }
//                }
//            }
//        }
//    }
//
//    // Effect for polling for new messages
//    LaunchedEffect(currentUserEmail) { // Keyed to currentUserEmail
//        logger.info("LaunchedEffect (Polling) for $currentUserEmail: Started.")
//        while (true) {
//            if (!initialLoadForCurrentUserDone) { // Don't poll if initial load isn't even done
//                delay(1000L) // Wait a bit
//                continue
//            }
//            try {
//                // logger.debug("Polling: Checking for new messages for $currentUserEmail")
//                val newReceivedMessages = client.receiveMessages(currentUserEmail)
//                if (newReceivedMessages.isNotEmpty()) {
//                    globalErrorMessage = null // Clear error on successful message receipt
//                    logger.info("Polling: Received ${newReceivedMessages.size} new messages for $currentUserEmail.")
//                }
//
//                var newContactAdded = false
//                newReceivedMessages.forEach { receivedMsg ->
//                    val contact = receivedMsg.senderId
//                    val uiMessage = ConversationMessage(
//                        id = Clock.System.now().toEpochMilliseconds().toString() + receivedMsg.content.hashCode() + Random.nextInt(), // more unique ID
//                        content = receivedMsg.content,
//                        isSent = false,
//                        timestamp = TimestampFormatter.format(receivedMsg.timestamp.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds())
//                    )
//
//                    val messageList = conversations.getOrPut(contact) {
//                        logger.info("Polling: New contact '$contact' detected from received message. Adding to conversations map.")
//                        newContactAdded = true // Flag that a new contact appeared
//                        mutableStateListOf()
//                    }
//
//                    // Avoid adding duplicate messages if polling runs too fast or messages are re-fetched
//                    if (!messageList.any { it.content == uiMessage.content && it.timestamp == uiMessage.timestamp && !it.isSent }) {
//                        messageList.add(uiMessage)
//                        logger.debug("Polling: Added new received message from $contact. Message: '${uiMessage.content.take(30)}'. New list size: ${messageList.size}")
//
//                        if (!contactNicknames.containsKey(contact)) {
//                            scope.launch {
//                                logger.debug("Polling: Fetching nickname for new contact $contact")
//                                getOrFetchNickname(contact)
//                            }
//                        }
//                    } else {
//                        logger.debug("Polling: Duplicate received message from $contact detected, not adding. Message: '${uiMessage.content.take(30)}'")
//                    }
//
//                    if (contact == selectedContactEmail && messageList.isNotEmpty()) {
//                        scope.launch {
//                            // logger.debug("Polling: Scrolling to end of chat for selected contact $contact")
//                            chatMessagesListState.animateScrollToItem(messageList.size - 1)
//                        }
//                    }
//                }
//                // CHANGE 3: If a new contact was added, and no contact is selected,
//                // you might want to select it automatically or ensure the UI refreshes the contact list.
//                // The displayContactsAndInfo should automatically pick up changes to conversations.keys.
//                if (newContactAdded) {
//                    logger.info("Polling: At least one new contact was added through received messages. UI should update contact list.")
//                    // If you want to auto-select the new contact when no chat is open:
//                    // if (selectedContactEmail == null && newReceivedMessages.isNotEmpty()) {
//                    //    selectedContactEmail = newReceivedMessages.first().senderId
//                    // }
//                }
//
//            } catch (e: Exception) {
//                logger.error("Polling: Error polling for messages for $currentUserEmail: ${e.message}", e)
//                // globalErrorMessage = "Error fetching new messages." // Can be noisy
//            }
//            delay(3000L) // Poll every 3 seconds
//        }
//    }
//
//    val allKnownContactEmails = remember(conversations.keys, searchResults) {
//        val emailsFromSearch = searchResults.map { it.email }
//        (conversations.keys + emailsFromSearch).distinct().also {
//            logger.debug("Recalculated allKnownContactEmails. Count: ${it.size}. Keys: [${it.joinToString()}]")
//        }
//    }
//
//    // This key for displayContactsAndInfo is crucial for it to update.
//    // It depends on:
//    // 1. allKnownContactEmails (changes if new contact appears in conversations.keys or searchResults)
//    // 2. The content/timestamp of the last message in any conversation (catches new messages in existing chats)
//    // 3. Nicknames (if a nickname is fetched/updated)
//    // 4. Search query and searching state
//    val displayContactsAndInfo = remember(
//        allKnownContactEmails,
//        conversations.entries.joinToString { entry -> // CHANGE 4: More sensitive key to last message changes
//            "${entry.key}:${entry.value.lastOrNull()?.content?.hashCode()}:${entry.value.lastOrNull()?.timestamp}"
//        },
//        contactNicknames.toMap(), // Make sure this captures changes correctly
//        searchQuery,
//        isSearching
//    ) {
//        logger.debug("Recalculating displayContactsAndInfo. Query: '$searchQuery'. AllKnownContactEmails count: ${allKnownContactEmails.size}. Nicknames count: ${contactNicknames.size}.")
//
//        val listToDisplay = allKnownContactEmails
//            .mapNotNull { email ->
//                val displayName = contactNicknames[email] ?: email
//                if (searchQuery.isBlank() || displayName.contains(searchQuery, ignoreCase = true) || email.contains(searchQuery, ignoreCase = true)) {
//                    val lastMsgObject = conversations[email]?.lastOrNull()
//                    val lastMessageText = lastMsgObject?.content ?: run {
//                        if (conversations.containsKey(email)) { // Key exists but list is empty
//                            "No messages yet"
//                        } else { // Key doesn't exist (e.g., from search result not yet chatted with)
//                            "Tap to start chat"
//                        }
//                    }
//                    ContactDisplayInfo(
//                        email = email,
//                        displayName = displayName,
//                        lastMessage = lastMessageText,
//                        lastMessageTimestamp = lastMsgObject?.timestamp ?: "",
//                        avatarSeed = email // For generating consistent avatar look
//                    )
//                } else {
//                    null
//                }
//            }
//            .sortedWith(compareByDescending<ContactDisplayInfo> {
//                // Give "Sending..." and "Failed" a very high sort order temporarily, then by actual timestamp
//                when (it.lastMessageTimestamp) {
//                    "Sending..." -> Long.MAX_VALUE.toString()
//                    "Failed" -> (Long.MAX_VALUE -1).toString()
//                    else -> it.lastMessageTimestamp.takeIf { ts -> ts.isNotBlank() } ?: "0"
//                }
//            }.thenBy { it.displayName }) // Secondary sort by name for consistent ordering
//            .also {
//                logger.debug("Finished recalculating displayContactsAndInfo. Count: ${it.size}. For Query: '$searchQuery'. First 5: ${it.take(5).joinToString { c -> c.displayName + " (" + c.lastMessage.take(10) + ")" }}")
//            }
//        listToDisplay
//    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Messages") },
//                actions = {
//                    IconButton(onClick = onNavigateToProfile) {
//                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
//                    }
//                },
//                backgroundColor = MaterialTheme.colors.primarySurface
//            )
//        }
//    ) { paddingValues ->
//        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
//            globalErrorMessage?.let {
//                Text(
//                    it,
//                    color = MaterialTheme.colors.error,
//                    modifier = Modifier.fillMaxWidth().padding(8.dp)
//                        .background(MaterialTheme.colors.error.copy(alpha = 0.1f)).padding(8.dp)
//                )
//            }
//
//            Row(
//                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                OutlinedTextField(
//                    value = searchQuery,
//                    onValueChange = {
//                        searchQuery = it
//                        if (it.isBlank()) {
//                            searchResults = emptyList() // Clear explicit search results
//                            logger.info("Search query cleared, searchResults (List<User>) also cleared.")
//                        }
//                    },
//                    label = { Text("Search or start new chat (email)") },
//                    modifier = Modifier.weight(1f),
//                    singleLine = true,
//                    trailingIcon = {
//                        if (searchQuery.isNotBlank()) {
//                            IconButton(onClick = {
//                                if (searchQuery.isNotBlank() && searchQuery.lowercase() != currentUserEmail.lowercase()) {
//                                    scope.launch {
//                                        globalErrorMessage = null
//                                        isSearching = true
//                                        logger.info("Search button clicked. Query: '$searchQuery'")
//                                        try {
//                                            val usersFound: List<User> = client.searchUsersByEmail(searchQuery)
//                                            logger.info("Search - Raw users found from client: ${usersFound.size} for query: '$searchQuery'")
//                                            usersFound.forEach { logger.debug("Search - Found user: ${it.email} / ${it.nickname}") }
//
//                                            searchResults = usersFound // This will trigger allKnownContactEmails and displayContactsAndInfo
//
//                                            if (usersFound.isEmpty()) {
//                                                globalErrorMessage = "No user found for '$searchQuery'."
//                                            } else {
//                                                usersFound.forEach { user ->
//                                                    // Update nicknames from search results
//                                                    if (contactNicknames[user.email] != user.nickname && !user.nickname.isNullOrBlank()) {
//                                                        contactNicknames[user.email] = user.nickname
//                                                        logger.info("Search - Added/Updated nickname for ${user.email}: ${user.nickname}")
//                                                    }
//                                                    // Ensure searched user is in conversations map to be selectable, even if no messages yet
//                                                    conversations.putIfAbsent(user.email, mutableStateListOf())
//                                                }
//                                            }
//                                        } catch (e: Exception) {
//                                            globalErrorMessage = "Search failed: ${e.message}"
//                                            logger.error("Search exception for query '$searchQuery':", e)
//                                            searchResults = emptyList()
//                                        } finally {
//                                            isSearching = false
//                                        }
//                                    }
//                                } else if (searchQuery.lowercase() == currentUserEmail.lowercase()) {
//                                    globalErrorMessage = "You cannot search for yourself."
//                                }
//                            }) {
//                                if (isSearching) {
//                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
//                                } else {
//                                    Icon(Icons.Filled.Search, "Search users")
//                                }
//                            }
//                        }
//                    },
//                    colors = TextFieldDefaults.outlinedTextFieldColors(
//                        focusedBorderColor = MaterialTheme.colors.primary,
//                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
//                    )
//                )
//            }
//            Divider(modifier = Modifier.padding(horizontal = 16.dp))
//
//            Row(modifier = Modifier.fillMaxSize()) {
//                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 4.dp, top = 8.dp)) {
//                    if (isLoadingContacts && searchQuery.isBlank() && !isSearching && !initialLoadForCurrentUserDone) { // Show loading only on very first load
//                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                            CircularProgressIndicator()
//                            logger.debug("UI: Displaying loading indicator for initial contacts.")
//                        }
//                    } else if (displayContactsAndInfo.isEmpty()) { // General empty state
//                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                            Text(
//                                when {
//                                    isSearching -> "Searching..."
//                                    searchQuery.isNotBlank() -> "No users match '$searchQuery'."
//                                    else -> "No conversations yet. Search to start!"
//                                }
//                            )
//                            logger.debug("UI: Displaying empty state message. isSearching=$isSearching, searchQuery='$searchQuery'")
//                        }
//                    } else {
//                        Box(modifier = Modifier.fillMaxHeight()) {
//                            LazyColumn(
//                                modifier = Modifier.fillMaxHeight(),
//                                state = contactListState
//                            ) {
//                                // Log the exact list being rendered
//                                logger.debug("UI: Rendering LazyColumn for contacts. Count: ${displayContactsAndInfo.size}. Items: ${displayContactsAndInfo.take(3).map { it.displayName }}")
//                                items(displayContactsAndInfo, key = { it.email }) { contactInfo ->
//                                    ContactCardItem(
//                                        contactInfo = contactInfo,
//                                        isSelected = contactInfo.email == selectedContactEmail,
//                                        onClick = {
//                                            logger.info("ContactCardItem clicked for ${contactInfo.email} (DisplayName: ${contactInfo.displayName})")
//                                            selectedContactEmail = contactInfo.email
//                                            searchQuery = "" // Clear search after selection
//                                            searchResults = emptyList()
//                                        }
//                                    )
//                                    Divider(modifier = Modifier.padding(start = 60.dp)) // Standard Material Design indentation for divider after avatar
//                                }
//                            }
//                            VerticalScrollbar(
//                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
//                                adapter = rememberScrollbarAdapter(scrollState = contactListState)
//                            )
//                        }
//                    }
//                }
//
//                Divider(
//                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
//                    modifier = Modifier.fillMaxHeight().width(1.dp)
//                )
//
//                if (selectedContactEmail != null) {
//                    val currentChatPartnerEmail = selectedContactEmail!!
//                    Column(modifier = Modifier.weight(2.5f).fillMaxHeight()) {
//                        TopAppBar(
//                            title = {
//                                Text(
//                                    text = contactNicknames[currentChatPartnerEmail] ?: currentChatPartnerEmail,
//                                    fontWeight = FontWeight.SemiBold,
//                                    fontSize = 18.sp
//                                )
//                            },
//                            actions = {
//                                IconButton(onClick = { /* TODO: Implement more chat options */ }) {
//                                    Icon(Icons.Filled.MoreVert, "More options")
//                                }
//                            },
//                            backgroundColor = MaterialTheme.colors.surface, // Or primarySurface
//                            elevation = AppBarDefaults.TopAppBarElevation / 2
//                        )
//                        Divider()
//
//                        if (isLoadingMessages && (conversations[currentChatPartnerEmail]?.isEmpty() == true || conversations[currentChatPartnerEmail] == null)) {
//                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
//                                CircularProgressIndicator()
//                            }
//                        } else {
//                            ChatArea(
//                                messages = conversations[currentChatPartnerEmail] ?: remember { mutableStateListOf() }, // Provide an empty list if null
//                                listState = chatMessagesListState,
//                                modifier = Modifier.weight(1f).fillMaxWidth()
//                            )
//                        }
//                        MessageInput { messageText ->
//                            scope.launch {
//                                val tempId = "temp_${Clock.System.now().toEpochMilliseconds()}_${messageText.hashCode()}"
//                                val optimisticMessage = ConversationMessage(
//                                    id = tempId,
//                                    content = messageText,
//                                    isSent = true,
//                                    timestamp = "Sending..."
//                                )
//                                val messageList = conversations.getOrPut(currentChatPartnerEmail) { mutableStateListOf() }
//                                messageList.add(optimisticMessage)
//                                // Ensure scrolling happens after state update
//                                scope.launch { if (messageList.isNotEmpty()) chatMessagesListState.animateScrollToItem(messageList.size - 1) }
//
//
//                                try {
//                                    client.sendMessage(currentUserEmail, currentChatPartnerEmail, messageText)
//                                    // Update optimistic message to confirmed
//                                    val optimisticIndex = messageList.indexOfFirst { it.id == tempId }
//                                    if (optimisticIndex != -1) {
//                                        messageList[optimisticIndex] = messageList[optimisticIndex].copy(
//                                            timestamp = TimestampFormatter.format(Clock.System.now().toEpochMilliseconds())
//                                            // id can remain tempId or be updated if server returns one
//                                        )
//                                    } else { // Optimistic message somehow removed, add confirmed one
//                                        val confirmedMsg = ConversationMessage(
//                                            id = "sent_${Clock.System.now().toEpochMilliseconds()}",
//                                            content = messageText,
//                                            isSent = true,
//                                            timestamp = TimestampFormatter.format(Clock.System.now().toEpochMilliseconds())
//                                        )
//                                        if (!messageList.any { it.content == confirmedMsg.content && it.isSent && it.timestamp == confirmedMsg.timestamp}) {
//                                            messageList.add(confirmedMsg)
//                                        }
//                                    }
//                                    scope.launch { if (messageList.isNotEmpty()) chatMessagesListState.animateScrollToItem(messageList.size - 1) }
//                                } catch (e: Exception) {
//                                    logger.error("Failed to send message to $currentChatPartnerEmail: ${e.message}", e)
//                                    globalErrorMessage = "Failed to send: ${e.message?.take(100)}"
//                                    val index = messageList.indexOfFirst { it.id == tempId }
//                                    if (index != -1) {
//                                        messageList[index] = optimisticMessage.copy(timestamp = "Failed")
//                                    }
//                                }
//                            }
//                        }
//                    }
//                } else { // No contact selected view
//                    Box(
//                        modifier = Modifier.weight(2.5f).fillMaxHeight().padding(16.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Text(
//                            when {
//                                isLoadingContacts && !initialLoadForCurrentUserDone -> "Loading contacts..."
//                                displayContactsAndInfo.isEmpty() && searchQuery.isBlank() && !isSearching -> "Search for users by email to start a new chat, or check existing conversations on the left."
//                                isSearching -> "Searching..."
//                                searchQuery.isNotBlank() && displayContactsAndInfo.isEmpty() -> "No users match '$searchQuery'. Try a different email."
//                                else -> "Select a contact to view messages or search to start a new chat."
//                            },
//                            style = MaterialTheme.typography.subtitle1,
//                            color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
//                        )
//                    }
//                }
//            }
//        }
//    }
//}


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
        if (isLoadingContacts) { // Așteaptă finalizarea încărcării inițiale a contactelor
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
