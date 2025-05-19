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

actual class MessageClient actual constructor() {
    private val sessionStorage = SessionStorage()
    private val networkManager = NetworkManager()
    private val keyManager = KeyManager(sessionStorage)
    private val sesameManager = SesameManager(keyManager, networkManager, sessionStorage)


    actual suspend fun registerUser(email:String, nickname: String) {
        sessionStorage.saveUserRecords(mutableMapOf(email to UserRecord(userId = email, nickname = nickname)) )
        networkManager.registerUser(email, nickname)
    }

    actual suspend fun registerDevice(email: String, nickname: String) {
        val identityKey = keyManager.generateIdentityKey()
        sessionStorage.storeIdentityKey(email, identityKey)

        val signedPreKey = keyManager.generateSignedPreKey()
        sessionStorage.storeSignedPreKey(email, signedPreKey)

        val oneTimePreKeys = keyManager.generateOneTimePreKeys(10)
        sessionStorage.storeOneTimePreKeys(email, oneTimePreKeys)

        val deviceId = identityKey.publicKey.encodeToBase64()
        sessionStorage.setClientInfo(email, nickname, deviceId)
        sessionStorage.saveDeviceRecord(email, nickname, DeviceRecord(deviceId, identityKey.publicKey, null))
        networkManager.registerDevice(email, identityKey = identityKey.publicKey, signedPreKey = signedPreKey, oneTimePreKeys = oneTimePreKeys)
    }



//    actual suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: String) {
//        println("Sending message: $plaintext to server")
//        sesameManager.sendMessage(senderId ,recipientUserId, plaintext.toByteArray(Charsets.UTF_8))
//    }

    actual suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: String) {
        withContext(Dispatchers.IO) {
            println("Preparing to send message from $senderId to $recipientUserId: '$plaintext'")
            try {
                sesameManager.sendMessage(senderId ,recipientUserId, plaintext.toByteArray(Charsets.UTF_8))
                println("Message sent via SesameManager from $senderId to $recipientUserId.")

                val timestamp = Clock.System.now().toEpochMilliseconds()
                sessionStorage.saveMessage(
                    senderEmail = senderId,
                    receiverEmail = recipientUserId,
                    content = plaintext,
                    timestamp = timestamp,
                    isSentByLocalUser = true
                )
                println("Sent message saved locally for conversation between $senderId and $recipientUserId.")
            } catch (e: Exception) {
                println("Failed to send or save message from $senderId to $recipientUserId: ${e.message}")
                throw Exception("Message sending failed: ${e.message}", e)
            }
        }
    }

    actual suspend fun receiveMessages(email: String): List<ReceivedMessage> {
        return withContext(Dispatchers.IO) {
            println("Checking for new messages for $email")
            val deviceId = sessionStorage.loadDeviceId(email)
            val networkMessages = networkManager.fetchMessages(email, deviceId)
            println("Fetched ${networkMessages.size} raw messages from network for $email.")

            val processedMessages = mutableListOf<ReceivedMessage>()
            for (netMsg in networkMessages) {
                try {
                    if (netMsg.recipientId != email) {
                        println("Skipping message not intended for $email. Actual recipient: ${netMsg.recipientId}, Sender: ${netMsg.senderId}")
                        continue
                    }

                    val plaintext = sesameManager.receiveMessage(netMsg)
                    val receivedMsg = ReceivedMessage(
                        senderId = netMsg.senderId,
                        content = plaintext,
                        timestamp = netMsg.timestamp
                    )
                    processedMessages.add(receivedMsg)

                    val timestampMillis = netMsg.timestamp.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds()
                    sessionStorage.saveMessage(
                        senderEmail = receivedMsg.senderId,
                        receiverEmail = email,
                        content = plaintext,
                        timestamp = timestampMillis,
                        isSentByLocalUser = false
                    )
                    println("Received and saved message from ${receivedMsg.senderId} for $email.")
                } catch (e: Exception) {
                    println("Failed to process/decrypt or save received message for $email from ${netMsg.senderId}: ${e.message}")
                }
            }
            println("Processed ${processedMessages.size} messages for $email.")
            processedMessages
        }
    }

//    actual suspend fun receiveMessages(email: String): List<ReceivedMessage> {
//        val messages = networkManager.fetchMessages(email, sessionStorage.loadDeviceId(email))
//        return messages.map { message ->
//            val plaintext = sesameManager.receiveMessage(message)
//            ReceivedMessage(message.senderId, plaintext, message.timestamp)
//        }


    actual suspend fun loginUser(email: String, nickname: String): String {
        val deviceId = sessionStorage.loadDeviceId(email)

        val identityKey = sessionStorage.getDevicePublicKey(email)
            ?: throw Exception("No device public key found for deviceId: $deviceId")

        println("identityKey: $identityKey")

        return networkManager.login(email, nickname, identityKey)
            ?: throw Exception("Login failed: Device not registered")
    }

    actual suspend fun searchUsersByEmail(email: String): List<String> {
        return networkManager.searchUsersByEmail(email)
    }

    actual suspend fun getRecentConversationPartners(currentUserEmail: String): List<String> {
        return withContext(Dispatchers.IO) {
            println("Fetching recent conversation partners for $currentUserEmail")
            val partners = sessionStorage.getRecentConversationPartners(currentUserEmail)
            println("Found ${partners.size} recent partners for $currentUserEmail.")
            partners
        }
    }

    actual suspend fun loadMessagesForConversation(currentUserEmail: String, contactEmail: String): List<ConversationMessage> {
        return withContext(Dispatchers.IO) {
            println("Loading messages for conversation between $currentUserEmail and $contactEmail")
            val localDbMessages = sessionStorage.loadMessagesForConversation(currentUserEmail, contactEmail)
            val uiMessages = localDbMessages.map { dbMsg ->
                ConversationMessage(
                    id = dbMsg.id.toString(),
                    content = dbMsg.content,
                    isSent = dbMsg.senderEmail == currentUserEmail,
                    timestamp = TimestampFormatter.format(dbMsg.timestamp)
                )
            }
            println("Loaded ${uiMessages.size} messages for conversation $currentUserEmail <-> $contactEmail.")
            uiMessages
        }
    }

    actual suspend fun getLocalUserNickname(email: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                sessionStorage.listClientInfos().find { it.first == email }?.second
            } catch (e: Exception) {
                println("Could not retrieve nickname for $email from local storage: ${e.message}")
                null
            }
        }
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
}