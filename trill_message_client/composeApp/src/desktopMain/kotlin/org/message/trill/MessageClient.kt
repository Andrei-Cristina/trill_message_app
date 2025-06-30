package org.message.trill

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.message.trill.encryption.keys.KeyManager
import org.message.trill.encryption.utils.EncryptionUtils
import org.message.trill.encryption.utils.FilePointerHelper
import org.message.trill.encryption.utils.TimestampFormatter
import org.message.trill.encryption.utils.models.User
import org.message.trill.messaging.models.ConversationMessage
import org.message.trill.messaging.models.FilePointer
import org.message.trill.messaging.models.ReceivedMessage
import org.message.trill.networking.NetworkManager
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.SesameManager
import org.message.trill.session.sesame.UserRecord
import org.message.trill.session.storage.SessionStorage
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.swing.JFileChooser
import javax.swing.filechooser.FileSystemView

actual class MessageClient actual constructor() {
    private var currentLoggedInUserEmail: String? = null
    private var sessionStorage: SessionStorage? = null
    private var keyManager: KeyManager? = null
    private var sesameManager: SesameManager? = null

    private val networkManager = NetworkManager()

    private var messageClientScope: CoroutineScope? = null
    private val _newMessageForUiFlow = MutableSharedFlow<ReceivedMessage>(replay = 0, extraBufferCapacity = 64)
    actual val newMessageForUiFlow: SharedFlow<ReceivedMessage> = _newMessageForUiFlow.asSharedFlow()

    private fun startObservingIncomingMessages() {
        messageClientScope?.cancel()
        messageClientScope =
            CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("MessageClientObserver"))

        networkManager.incomingMessagesFlow
            .onEach { networkMessage ->
                println("MessageClient observed incoming WebSocket message for recipient: ${networkMessage.recipientId}")

                val activeUser = currentLoggedInUserEmail
                val currentSS = sessionStorage
                val currentSM = sesameManager

                if (activeUser == null || currentSS == null || currentSM == null) {
                    println("WebSocket message received, but MessageClient not fully initialized for user $activeUser. Ignoring.")
                    return@onEach
                }

                if (activeUser != networkMessage.recipientId) {
                    println("WebSocket message recipient ${networkMessage.recipientId} does not match active user $activeUser. Ignoring.")
                    return@onEach
                }

                try {
                    println("Processing WebSocket message from ${networkMessage.senderId} for $activeUser")
                    val plaintext = currentSM.receiveMessage(networkMessage)

                    val timestampMillis =
                        networkMessage.timestamp.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds()
                    currentSS.saveMessage(
                        senderEmail = networkMessage.senderId,
                        receiverEmail = activeUser,
                        content = plaintext,
                        timestamp = timestampMillis,
                        isSentByLocalUser = false
                    )
                    println("Received, decrypted, and saved message from ${networkMessage.senderId} via WebSocket for $activeUser.")

                    val filePointer = FilePointerHelper.parseFilePointer(plaintext)
                    val contentForUi = if (filePointer != null) {
                        "[File] Received: ${filePointer.fileName}"
                    } else {
                        plaintext
                    }

                    val receivedMsg = ReceivedMessage(
                        senderId = networkMessage.senderId,
                        content = contentForUi,
                        timestamp = networkMessage.timestamp,
                        filePointer = filePointer
                    )

                    _newMessageForUiFlow.tryEmit(receivedMsg)

                } catch (e: Exception) {
                    println("Failed to process/decrypt or save WebSocket message for $activeUser from ${networkMessage.senderId}: ${e.message}")
                }
            }
            .catch { e ->
                println("Error in MessageClient's incomingMessagesFlow collection: ${e.message}")
            }
            .launchIn(messageClientScope!!)
        println("MessageClient started observing incoming WebSocket messages.")
    }

    private fun stopObservingIncomingMessages() {
        println("MessageClient stopping observation of incoming WebSocket messages.")
        messageClientScope?.cancel()
        messageClientScope = null
    }

    suspend fun setActiveUser(email: String, isNewUser: Boolean = false) {
        if (currentLoggedInUserEmail == email && sessionStorage != null && !isNewUser) {
            println("User $email is already active.")
            if (messageClientScope == null || messageClientScope?.isActive == false) {
                startObservingIncomingMessages()
            }

            return
        }
        if (currentLoggedInUserEmail != null && currentLoggedInUserEmail != email) {
            println("Switching active user from $currentLoggedInUserEmail to $email")
            userLogOut()
        }

        println("Activating user: $email. New user: $isNewUser")
        currentLoggedInUserEmail = email
        val ss = SessionStorage(email)
        this.sessionStorage = ss
        this.keyManager = KeyManager(ss)
        this.sesameManager = SesameManager(this.keyManager!!, networkManager, ss)
        println("MessageClient initialized for user: $email")

        startObservingIncomingMessages()
    }

    actual suspend fun userLogOut() {
        println("Logging out user: $currentLoggedInUserEmail")
        stopObservingIncomingMessages()
        networkManager.logout()
        currentLoggedInUserEmail = null
        sessionStorage = null
        keyManager = null
        sesameManager = null
    }

    private fun getActiveComponents(): Triple<String, SessionStorage, KeyManager> {
        val email = currentLoggedInUserEmail
            ?: throw IllegalStateException("No user is active. Call setActiveUser first.")
        val ss = sessionStorage
            ?: throw IllegalStateException("SessionStorage not initialized for $email. Critical error.")
        val km = keyManager
            ?: throw IllegalStateException("KeyManager not initialized for $email. Critical error.")
        return Triple(email, ss, km)
    }

    private fun getActiveSesameManager(): SesameManager {
        return sesameManager
            ?: throw IllegalStateException("SesameManager not initialized. No user active or setup failed.")
    }

    actual suspend fun registerUser(email: String, password: String, nickname: String) {
        setActiveUser(email, isNewUser = true)
        val (_, ss, _) = getActiveComponents()

        ss.saveUserRecords(mutableMapOf(email to UserRecord(userId = email, nickname = nickname)))
        println("Local self-record created for $email in their DB.")

        networkManager.registerUser(email, password, nickname)
    }

    actual suspend fun registerDevice(email: String, nickname: String) {
        if (currentLoggedInUserEmail != email) {
            setActiveUser(email)
        }
        val (activeEmail, ss, km) = getActiveComponents()

        val identityKey = km.generateIdentityKey()
        ss.storeIdentityKey(activeEmail, identityKey)

        val signedPreKey = km.generateSignedPreKey()
        ss.storeSignedPreKey(activeEmail, signedPreKey)

        val oneTimePreKeys = km.generateOneTimePreKeys(10)
        ss.storeOneTimePreKeys(activeEmail, oneTimePreKeys)

        val deviceId = identityKey.publicKey.encodeToBase64()
        ss.setClientInfo(activeEmail, nickname, deviceId)

        ss.saveDeviceRecord(activeEmail, nickname, DeviceRecord(deviceId, identityKey.publicKey, null))
        println("Device record for self saved for $activeEmail with deviceId $deviceId.")

        networkManager.registerDevice(
            activeEmail,
            identityKey = identityKey.publicKey,
            signedPreKey = signedPreKey,
            oneTimePreKeys = oneTimePreKeys
        )
    }

    actual suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: String) {
        val (activeUser, ss, _) = getActiveComponents()
        if (senderId != activeUser) {
            throw IllegalArgumentException("sendMessage senderId $senderId does not match active user $activeUser")
        }
        val sm = getActiveSesameManager()

        withContext(Dispatchers.IO) {
            try {
                sm.sendMessage(senderId, recipientUserId, plaintext.toByteArray(Charsets.UTF_8))
                println("Message sent via SesameManager from $senderId to $recipientUserId.")

                val timestamp = Clock.System.now().toEpochMilliseconds()
                ss.saveMessage(
                    senderEmail = senderId,
                    receiverEmail = recipientUserId,
                    content = plaintext,
                    timestamp = timestamp,
                    isSentByLocalUser = true
                )
                println("Sent message saved locally for conversation between $activeUser and $recipientUserId.")
            } catch (e: Exception) {
                println("Failed to send or save message from $activeUser to $recipientUserId: ${e.message}")
                throw Exception("Message sending failed: ${e.message}", e)
            }
        }
    }

    actual suspend fun sendFile(senderId: String, recipientUserId: String, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) throw Exception("File not found: $filePath")

        var (fileKey, iv) = EncryptionUtils.generateKeyPair()
        iv = iv.copyOfRange(0, 16)
        val encryptedFileResult = EncryptionUtils.encryptFile(fileKey, iv, file.readBytes())

        val uploadInfo = networkManager.requestUploadUrl(file.name, file.length())
        println("Received upload URL ${uploadInfo.uploadUrl} for file ${file.name} with ID ${uploadInfo.fileId}.")

        networkManager.uploadFile(uploadInfo.uploadUrl, encryptedFileResult.ciphertext)

        val filePointer = FilePointer(
            fileId = uploadInfo.fileId,
            fileName = file.name,
            fileSize = file.length(),
            mimeType = withContext(Dispatchers.IO) { Files.probeContentType(file.toPath()) } ?: "application/octet-stream",
            key = fileKey,
            iv = iv,
            hmac = encryptedFileResult.hmac
        )

        val pointerContent = FilePointerHelper.createFilePointerContent(filePointer)

        sendMessage(senderId, recipientUserId, pointerContent)
    }

    actual suspend fun downloadAndDecryptFile(filePointer: FilePointer) {
        withContext(Dispatchers.IO) {
            val encryptedBytes = networkManager.downloadFile(filePointer.fileId)

            val decryptedBytes = EncryptionUtils.decryptFile(
                key = filePointer.key,
                iv = filePointer.iv,
                ciphertext = encryptedBytes,
                receivedHmac = filePointer.hmac
            )

            val fileChooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory)
            fileChooser.dialogTitle = "Save file"
            fileChooser.selectedFile = File(filePointer.fileName)
            val userSelection = fileChooser.showSaveDialog(null)

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                val fileToSave = fileChooser.selectedFile
                fileToSave.writeBytes(decryptedBytes)
                println("File saved successfully to: ${fileToSave.absolutePath}")
            }
        }
    }

    actual suspend fun receiveMessages(email: String): List<ReceivedMessage> {
        val (activeUser, ss, _) = getActiveComponents()
        if (email != activeUser) {
            throw IllegalArgumentException("receiveMessages called for $email, but active user is $activeUser.")
        }
        val sm = getActiveSesameManager()

        return withContext(Dispatchers.IO) {
            println("Checking for new messages for $activeUser")
            val deviceId = ss.loadDeviceId(activeUser)

            val networkMessages = networkManager.fetchMessages(activeUser, deviceId)
            println("Fetched ${networkMessages.size} raw messages from network for $activeUser.")

            val processedMessages = mutableListOf<ReceivedMessage>()
            for (netMsg in networkMessages) {
                try {
                    if (netMsg.recipientId != activeUser) {
                        println("Skipping message not intended for $activeUser. Actual recipient: ${netMsg.recipientId}, Sender: ${netMsg.senderId}")
                        continue
                    }

                    val plaintext = sm.receiveMessage(netMsg)
                    val receivedMsg = ReceivedMessage(
                        senderId = netMsg.senderId,
                        content = plaintext,
                        timestamp = netMsg.timestamp
                    )
                    processedMessages.add(receivedMsg)

                    val timestampMillis = netMsg.timestamp.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds()
                    ss.saveMessage(
                        senderEmail = receivedMsg.senderId,
                        receiverEmail = activeUser,
                        content = plaintext,
                        timestamp = timestampMillis,
                        isSentByLocalUser = false
                    )
                    println("Received and saved message from ${receivedMsg.senderId} for $activeUser.")
                } catch (e: Exception) {
                    println("Failed to process/decrypt or save received message for $activeUser from ${netMsg.senderId}: ${e.message}")
                }
            }
            println("Processed ${processedMessages.size} messages for $activeUser.")
            processedMessages
        }
    }

    actual suspend fun loginUser(email: String, password: String): String {
        println("Logging in user: $email")
        setActiveUser(email)
        val (activeUser, ss, _) = getActiveComponents()

        val identityKeyForLogin = ss.getDevicePublicKey(activeUser)
            ?: throw Exception("No device public key found for $activeUser.")

        println("Attempting login for $activeUser with public key.")
        val nickname = getLocalUserNickname(email)

        return networkManager.login(email, password, nickname!!, identityKeyForLogin)
            ?: throw Exception("Login failed for $activeUser: Network authentication failed or device not recognized.")
    }

    actual suspend fun searchUsersByEmail(email: String): List<User> {
        getActiveComponents()
        return networkManager.searchUsersByEmail(email)
    }

    actual suspend fun getRecentConversationPartners(currentUserEmail: String): List<String> {
        val (activeUser, ss, _) = getActiveComponents()
        if (currentUserEmail != activeUser) {
            throw IllegalArgumentException("getRecentConversationPartners called for $currentUserEmail, but active user is $activeUser.")
        }
        return withContext(Dispatchers.IO) {
            val partners = ss.getRecentConversationPartners(activeUser)
            println("Found ${partners.size} recent partners for $activeUser.")
            partners
        }
    }

    actual suspend fun loadMessagesForConversation(
        currentUserEmail: String,
        contactEmail: String
    ): List<ConversationMessage> {
        val (activeUser, ss, _) = getActiveComponents()
        if (currentUserEmail != activeUser) {
            throw IllegalArgumentException("loadMessagesForConversation called for $currentUserEmail, but active user is $activeUser.")
        }
        return withContext(Dispatchers.IO) {
            val localDbMessages = ss.loadMessagesForConversation(activeUser, contactEmail)
            val uiMessages = localDbMessages.map { dbMsg ->
                val filePointer = FilePointerHelper.parseFilePointer(dbMsg.content)
                val contentForUi = if (filePointer != null) {
                    val direction = if (dbMsg.senderEmail == activeUser) "Sent" else "Received"
                    "[File] $direction: ${filePointer.fileName}"
                } else {
                    dbMsg.content
                }

                ConversationMessage(
                    id = dbMsg.id.toString(),
                    content = contentForUi,
                    isSent = dbMsg.senderEmail == activeUser,
                    timestamp = TimestampFormatter.format(dbMsg.timestamp),
                    filePointer = filePointer
                )
            }
            println("Loaded ${uiMessages.size} messages for conversation $activeUser <-> $contactEmail.")
            uiMessages
        }
    }

    actual suspend fun getLocalUserNickname(email: String): String? {
        val (activeUser, ss, _) = getActiveComponents()

        return withContext(Dispatchers.IO) {
            ss.getContactNickname(email)
        }
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
}