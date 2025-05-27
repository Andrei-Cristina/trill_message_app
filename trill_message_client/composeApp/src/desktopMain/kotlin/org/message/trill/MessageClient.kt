package org.message.trill

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.message.trill.encryption.keys.KeyManager
import org.message.trill.messaging.models.ReceivedMessage
import org.message.trill.networking.NetworkManager
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.SesameManager
import org.message.trill.session.sesame.UserRecord
import org.message.trill.session.storage.SessionStorage
import org.message.trill.ui.ConversationMessage
import java.util.*
import org.message.trill.encryption.utils.TimestampFormatter
import org.message.trill.encryption.utils.models.User

actual class MessageClient actual constructor() {
    private var currentLoggedInUserEmail: String? = null
    private var sessionStorage: SessionStorage? = null
    private var keyManager: KeyManager? = null
    private var sesameManager: SesameManager? = null

    private val networkManager = NetworkManager()

    suspend fun setActiveUser(email: String, isNewUser: Boolean = false) {
        if (currentLoggedInUserEmail == email && sessionStorage != null && !isNewUser) {
            println("User $email is already active.")
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
    }

    actual suspend fun userLogOut() {
        println("Logging out user: $currentLoggedInUserEmail")
        networkManager.logout()
        currentLoggedInUserEmail = null
        sessionStorage = null
        keyManager = null
        sesameManager = null
    }

    private fun getActiveComponents(): Triple<String, SessionStorage, KeyManager> {
        println("Current logged in user: $currentLoggedInUserEmail")
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
                ConversationMessage(
                    id = dbMsg.id.toString(),
                    content = dbMsg.content,
                    isSent = dbMsg.senderEmail == activeUser,
                    timestamp = TimestampFormatter.format(dbMsg.timestamp)
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