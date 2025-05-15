package org.message.trill.session.sesame

import kotlinx.datetime.Clock
import org.message.trill.encryption.double_ratchet.DoubleRatchet
import org.message.trill.encryption.double_ratchet.RatchetState
import org.message.trill.encryption.keys.KeyManager
import org.message.trill.encryption.x3dh.X3DH
import org.message.trill.messaging.models.Header
import org.message.trill.messaging.models.Message
import org.message.trill.messaging.models.MessageContent
import org.message.trill.networking.NetworkManager
import org.message.trill.networking.NetworkResponse
import org.message.trill.session.storage.SessionStorage
import java.util.*

class SesameManager(
private val keyManager: KeyManager,
private val networkManager: NetworkManager,
private val sessionStorage: SessionStorage
) {
    private var userRecords: MutableMap<String, UserRecord> = mutableMapOf()
    private val MAX_INACTIVE_SESSIONS = 10
    private val MAX_LATENCY = 14400000L

    init {
        userRecords = sessionStorage.loadUserRecords().toMutableMap()
        println("Initialized SesameManager with ${userRecords.size} user records")
    }

    suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: ByteArray) {
        println("sendMessage on SesameManager: senderId=$senderId, recipientUserId=$recipientUserId, plaintext=${plaintext.decodeToString()}")
        cleanupStaleRecords()
        val recipientUserRecord = userRecords[recipientUserId] ?: createUserRecord(recipientUserId)
        if (recipientUserRecord.isStale || recipientUserRecord.devices.isEmpty()) {
            println("Updating user record for $recipientUserId due to stale or empty devices")
            updateUserRecord(recipientUserId, networkManager.fetchUserDevices(recipientUserId))
        }

        val updatedUserRecord = userRecords[recipientUserId]!!
        if (updatedUserRecord.devices.isEmpty()) {
            println("No devices found for $recipientUserId after update")
            throw Exception("Cannot send message: No devices registered for $recipientUserId")
        }

        val messages = mutableListOf<Message>()
        for (device in updatedUserRecord.devices.values) {
            if (device.isStale) {
                println("Skipping stale device ${device.deviceId} for $recipientUserId")
                continue
            }
            if (device.publicKey.isEmpty()) {
                println("Skipping device ${device.deviceId} for $recipientUserId: Empty public key")
                continue
            }
            println("Preparing message for device ${device.deviceId} of $recipientUserId")
            val content = prepareMessageContent(senderId, recipientUserId, device.deviceId, plaintext)

            val message = Message(
                senderId = senderId,
                senderDeviceId = sessionStorage.loadDeviceId(senderId),
                recipientId = recipientUserId,
                recipientDeviceId = device.deviceId,
                content = content,
                timestamp = Clock.System.now().toString()
            )
            messages.add(message)
            println("Created message: senderDeviceId=${message.senderDeviceId}, recipientDeviceId=${message.recipientDeviceId}, header.n=${content.header.n}, ciphertext size=${content.ciphertext.size}")
        }

        if (messages.isEmpty()) {
            println("No messages generated for $recipientUserId: All devices stale or invalid")
            throw Exception("Cannot send message: No valid devices for $recipientUserId")
        }

        println("Sending ${messages.size} messages to $recipientUserId")

        val response = networkManager.sendMessages(messages)
        handleNetworkResponse(senderId, response, recipientUserId, plaintext)
    }

    private suspend fun prepareMessageContent(senderId: String, recipientUserId: String, recipientDeviceId: String, plaintext: ByteArray): MessageContent {
        println("prepareMessageContent on SesameManager: senderId=$senderId, recipientUserId=$recipientUserId, recipientDeviceId=$recipientDeviceId, plaintext=${plaintext.decodeToString()}")
        cleanupStaleRecords()
        val recipientUserRecord = userRecords[recipientUserId] ?: createUserRecord(recipientUserId)
        if (recipientUserRecord.isStale || recipientUserRecord.devices.isEmpty()) {
            println("Updating user record for $recipientUserId in prepareMessageContent")
            updateUserRecord(recipientUserId, networkManager.fetchUserDevices(recipientUserId))
        }

        val deviceRecord = recipientUserRecord.devices[recipientDeviceId] ?: createDeviceRecord(recipientDeviceId, recipientUserId)
        if (deviceRecord.publicKey.isEmpty()) {
            println("Device $recipientDeviceId for $recipientUserId has empty public key, updating device")
            updateUserRecord(recipientUserId, networkManager.fetchUserDevices(recipientUserId))
        }

        val (session, isInitial) = deviceRecord.activeSession?.let { it to false } ?: (createNewSession(senderId, recipientUserId, recipientDeviceId) to true)
        println("Using session ${session.sessionId} (initial=$isInitial) for $recipientUserId/$recipientDeviceId")
        println("Session ratchetState: dhs.public=${session.ratchetState.dhs.second.encodeToBase64()}, dhr=${session.ratchetState.dhr?.encodeToBase64() ?: "null"}, rk=${session.ratchetState.rk.encodeToBase64()}, cks=${session.ratchetState.cks?.encodeToBase64() ?: "null"}, ckr=${session.ratchetState.ckr?.encodeToBase64() ?: "null"}, ns=${session.ratchetState.ns}, nr=${session.ratchetState.nr}, pn=${session.ratchetState.pn}, ad=${session.ratchetState.ad.encodeToBase64()}, ek=${session.ratchetState.ek.encodeToBase64()}")

        val doubleRatchet = DoubleRatchet(session.ratchetState)
        println("Encrypting message, ad=${session.ratchetState.ad.encodeToBase64()}")

        val (header, ciphertext) = doubleRatchet.encrypt(plaintext, session.ratchetState.ad)
        println("Encrypted message: header.dh=${header.dh.encodeToBase64()}, header.pn=${header.pn}, header.n=${header.n}, ciphertext size=${ciphertext.size}")

        header.ek = session.ratchetState.ek

        return MessageContent(header, ciphertext)
    }
//        val (session, isInitial) = deviceRecord.activeSession?.let { it to false } ?: (createNewSession(senderId, recipientUserId, recipientDeviceId) to true)
//        println("Using session ${session.sessionId} (initial=$isInitial) for $recipientUserId/$recipientDeviceId")

//        val doubleRatchet = DoubleRatchet(session.ratchetState)
//        val (header, ciphertext) = if (isInitial) {
//            val identityKey = keyManager.getIdentityKey(senderId).publicKey
//            println("Encrypting initial message for $recipientUserId/$recipientDeviceId, identityKey=${identityKey.encodeToBase64()}, ad size=${session.ratchetState.ad?.size ?: 0}")
//            val (initialHeader, initialCiphertext) = doubleRatchet.encrypt(plaintext, session.ratchetState.ad)
//            println("Encrypted initial message, header.n=${initialHeader.n}, ciphertext size=${initialCiphertext.size}")
//            val modifiedCiphertext = identityKey + initialCiphertext
//            println("Appended identityKey, modifiedCiphertext size=${modifiedCiphertext.size}")
//            initialHeader.copy(n = -1) to modifiedCiphertext
//        } else {
//            println("Encrypting non-initial message, ad size=${session.ratchetState.ad?.size ?: 0}")
//            val (header, ciphertext) = doubleRatchet.encrypt(plaintext, session.ratchetState.ad)
//            println("Encrypted non-initial message, header.n=${header.n}, ciphertext size=${ciphertext.size}")
//            header to ciphertext
//        }
//        return MessageContent(header, ciphertext)




//    suspend fun receiveMessage(message: Message): String {
//        println("receiveMessage on SesameManager: from ${message.senderId}/${message.senderDeviceId} to ${message.recipientId}/${message.recipientDeviceId}, header.n=${message.content.header.n}, ciphertext size=${message.content.ciphertext.size}")
//        val userRecord = userRecords[message.senderId] ?: createUserRecord(message.senderId)
//        if (userRecord.isStale) {
//            userRecord.isStale = false
//            userRecord.staleTransitionTimestamp = null
//            println("Marked user ${message.senderId} as non-stale")
//        }
//
//        val deviceRecord = userRecord.devices[message.senderDeviceId] ?: createDeviceRecord(message.senderDeviceId, message.senderId)
//        if (deviceRecord.isStale) {
//            deviceRecord.isStale = false
//            deviceRecord.staleTransitionTimestamp = null
//            println("Marked device ${message.senderDeviceId} as non-stale")
//        }
//
//        val content = message.content
//        try {
//            return if (content.header.n == -1) {
//                println("Handling initial message (n=-1) from ${message.senderId}/${message.senderDeviceId}")
//                val identityKeySize = 32
//                if (content.ciphertext.size < identityKeySize) {
//                    println("Invalid initial message ciphertext: size=${content.ciphertext.size}, expected at least $identityKeySize")
//                    throw IllegalStateException("Invalid initial message ciphertext")
//                }
//                val senderIdentityKey = content.ciphertext.copyOfRange(0, identityKeySize)
//                val actualCiphertext = content.ciphertext.copyOfRange(identityKeySize, content.ciphertext.size)
//                println("Extracted senderIdentityKey (size=${senderIdentityKey.size}) and actualCiphertext (size=${actualCiphertext.size})")
//
//                val x3dh = X3DH(keyManager)
//                val x3dhResult = try {
//                    println("Calling X3DH.receive with recipientId=${message.recipientId}, senderIdentityKey=${senderIdentityKey.encodeToBase64()}, dh=${content.header.dh.encodeToBase64()}")
//                    x3dh.receive(message.recipientId, senderIdentityKey, content.header.dh)
//                } catch (e: Exception) {
//                    println("X3DH.receive failed: ${e.message}")
//                    throw Exception("Failed to initialize X3DH: ${e.message}")
//                }
//                println("X3DH.receive succeeded, sk=${x3dhResult.sk.encodeToBase64()}, ad size=${x3dhResult.ad.size}")
//
//                val signedPreKey = keyManager.getSignedPreKey(message.recipientId).preKey
//                println("Retrieved signedPreKey for ${message.recipientId}")
//                val ratchetState = try {
//                    RatchetState.initAsReceiver(x3dhResult, signedPreKey)
//                } catch (e: Exception) {
//                    println("RatchetState.initAsReceiver failed: ${e.message}")
//                    throw Exception("Failed to initialize ratchet state: ${e.message}")
//                }
//                println("Initialized ratchetState, ad size=${ratchetState.ad.size}")
//
//                val session = Session(UUID.randomUUID().toString(), ratchetState, isInitiating = false)
//                val doubleRatchet = DoubleRatchet(ratchetState)
//                val modifiedContent = MessageContent(content.header.copy(n = 0), actualCiphertext)
//                println("Attempting to decrypt with header.n=${modifiedContent.header.n}, ciphertext size=${modifiedContent.ciphertext.size}")
//                val plaintext = try {
//                    doubleRatchet.decrypt(modifiedContent, session.ratchetState.ad)
//                } catch (e: Exception) {
//                    println("DoubleRatchet.decrypt failed: ${e.message}")
//                    throw Exception("Failed to decrypt message: ${e.message}")
//                }
//
//                deviceRecord.activeSession = session
//                deviceRecord.inactiveSessions.add(0, session)
//                if (deviceRecord.inactiveSessions.size > MAX_INACTIVE_SESSIONS) {
//                    deviceRecord.inactiveSessions.removeLast()
//                }
//
//                println("Saved new session ${session.sessionId} for ${message.senderId}/${message.senderDeviceId}")
//
//                try {
//                    sessionStorage.saveUserRecords(userRecords)
//                    println("Saved user records after decryption")
//                } catch (e: Exception) {
//                    println("Failed to save user records: ${e.message}")
//                    throw Exception("Failed to save session: ${e.message}")
//                }
//
//                val decryptedText = plaintext.decodeToString()
//                println("Decrypted message: $decryptedText")
//                decryptedText
//            } else {
//                println("Handling non-initial message (n=${content.header.n}) from ${message.senderId}/${message.senderDeviceId}")
//                val session = findSessionForMessage(deviceRecord, content) ?: run {
//                    println("No session found for message from ${message.senderId}, creating new session")
//                    createNewSession(message.recipientId, message.senderId, message.senderDeviceId)
//                }
//                activateSession(deviceRecord, session)
//
//                val doubleRatchet = DoubleRatchet(session.ratchetState)
//                println("Attempting to decrypt with session ${session.sessionId}, header.n=${content.header.n}, ciphertext size=${content.ciphertext.size}")
//                val plaintext = try {
//                    doubleRatchet.decrypt(content, session.ratchetState.ad)
//                } catch (e: Exception) {
//                    println("DoubleRatchet.decrypt failed for non-initial message: ${e.message}")
//                    throw Exception("Failed to decrypt message: ${e.message}")
//                }
//
//                println("Decrypted message for ${message.senderId}/${message.senderDeviceId} with session ${session.sessionId}")
//
//                try {
//                    sessionStorage.saveUserRecords(userRecords)
//                    println("Saved user records after decryption")
//                } catch (e: Exception) {
//                    println("Failed to save user records: ${e.message}")
//                    throw Exception("Failed to save session: ${e.message}")
//                }
//
//                val decryptedText = plaintext.decodeToString()
//                println("Decrypted message: $decryptedText")
//                decryptedText
//            }
//        } catch (e: Exception) {
//            println("Failed to process message from ${message.senderId}/${message.senderDeviceId}: ${e.message}")
//            throw Exception("Failed to receive message: ${e.message}")
//        }
//    }

    // v2
//    suspend fun receiveMessage(message: Message): String {
//        println("receiveMessage on SesameManager: from ${message.senderId}/${message.senderDeviceId} to ${message.recipientId}/${message.recipientDeviceId}, header.n=${message.content.header.n}, ciphertext size=${message.content.ciphertext.size}")
//        val userRecord = userRecords[message.senderId] ?: createUserRecord(message.senderId)
//        if (userRecord.isStale) {
//            userRecord.isStale = false
//            userRecord.staleTransitionTimestamp = null
//            println("Marked user ${message.senderId} as non-stale")
//        }
//
//        val deviceRecord = userRecord.devices[message.senderDeviceId] ?: createDeviceRecord(message.senderDeviceId, message.senderId)
//        if (deviceRecord.isStale) {
//            deviceRecord.isStale = false
//            deviceRecord.staleTransitionTimestamp = null
//            println("Marked device ${message.senderDeviceId} as non-stale")
//        }
//
//        val content = message.content
//        try {
//            val session = findSessionForMessage(deviceRecord, content) ?: run {
//                println("No session found for message from ${message.senderId}/${message.senderDeviceId}, creating new session")
//                createNewSession(message.recipientId, message.senderId, message.senderDeviceId)
//            }
//            println("Using session ${session.sessionId} for decryption")
//            println("Session ratchetState: dhs.public=${session.ratchetState.dhs.second.encodeToBase64()}, dhr=${session.ratchetState.dhr?.encodeToBase64() ?: "null"}, rk=${session.ratchetState.rk.encodeToBase64()}, cks=${session.ratchetState.cks?.encodeToBase64() ?: "null"}, ckr=${session.ratchetState.ckr?.encodeToBase64() ?: "null"}, ns=${session.ratchetState.ns}, nr=${session.ratchetState.nr}, pn=${session.ratchetState.pn}, ad=${session.ratchetState.ad.encodeToBase64()}")
//
//            activateSession(deviceRecord, session)
//            val doubleRatchet = DoubleRatchet(session.ratchetState)
//
//            println("Attempting to decrypt with header.dh=${content.header.dh.encodeToBase64()}, header.n=${content.header.n}, ad=${session.ratchetState.ad.encodeToBase64()}")
//            val plaintext = try {
//                doubleRatchet.decrypt(content, session.ratchetState.ad)
//            } catch (e: Exception) {
//                println("DoubleRatchet.decrypt failed: ${e.message}")
//                throw Exception("Failed to decrypt message: ${e.message}")
//            }
//
//            println("Decrypted plaintext: ${plaintext.decodeToString()}")
//
//            sessionStorage.saveUserRecords(userRecords)
//            println("Saved user records after decryption")
//
//            return plaintext.decodeToString()
//        } catch (e: Exception) {
//            println("Failed to process message from ${message.senderId}/${message.senderDeviceId}: ${e.message}")
//            throw Exception("Failed to receive message: ${e.message}")
//        }
//    }

    suspend fun receiveMessage(message: Message): String {
        println("receiveMessage: from ${message.senderId}/${message.senderDeviceId} to ${message.recipientId}/${message.recipientDeviceId}, header.n=${message.content.header.n}, ciphertext size=${message.content.ciphertext.size}")
        val userRecord = userRecords[message.senderId] ?: createUserRecord(message.senderId)
        if (userRecord.isStale) {
            userRecord.isStale = false
            userRecord.staleTransitionTimestamp = null
            println("Marked user ${message.senderId} as non-stale")
        }

        val deviceRecord = userRecord.devices[message.senderDeviceId] ?: createDeviceRecord(message.senderDeviceId, message.senderId)
        if (deviceRecord.isStale) {
            deviceRecord.isStale = false
            deviceRecord.staleTransitionTimestamp = null
            println("Marked device ${message.senderDeviceId} as non-stale")
        }

        val content = message.content
        try {
            val session = findSessionForMessage(deviceRecord, content) ?: run {
                println("No session found for message from ${message.senderId}/${message.senderDeviceId}, initializing receiving session")
                val prekeyBundle = networkManager.fetchPrekeyBundle(message.senderId, message.senderDeviceId)
                println("Fetched prekeyBundle: identityKey=${prekeyBundle.identityKey}, signedPreKey=${prekeyBundle.signedPreKey}, signature=${prekeyBundle.signature}")

                val identityKey = Base64.getDecoder().decode(prekeyBundle.identityKey).also {
                    if (it.size != 32) throw IllegalArgumentException("Identity key must be 32 bytes, got ${it.size}")
                    println("Decoded identityKey: ${it.encodeToBase64()}")
                }
                val signedPreKey = Base64.getDecoder().decode(prekeyBundle.signedPreKey).also {
                    if (it.size != 32) throw IllegalArgumentException("Signed prekey must be 32 bytes, got ${it.size}")
                    println("Decoded signedPreKey: ${it.encodeToBase64()}")
                }
                val signature = Base64.getDecoder().decode(prekeyBundle.signature).also {
                    if (it.size != 64) throw IllegalArgumentException("Signature size is ${it.size} bytes, expected 64 bytes")
                    println("Decoded signature: ${it.encodeToBase64()}")
                }

//                if (!EncryptionUtils.verify(identityKey, signedPreKey, signature)) {
//                    println("Signature verification failed for sender ${message.senderId}")
//                    throw Exception("Signature verification failed")
//                }

                val x3dh = X3DH(keyManager)

                val x3dhResult = x3dh.receive(
                    userId = message.recipientId,
                    senderIdentityKey = identityKey,
                    senderEphemeralKey = content.header.ek
                )
                println("X3DH received: sk=${x3dhResult.sk.encodeToBase64()}, ad=${x3dhResult.ad.encodeToBase64()}, ek=${x3dhResult.ek.encodeToBase64()}")

                val ratchetState = RatchetState.initAsReceiver(x3dhResult, keyManager.getSignedPreKey(message.recipientId).preKey)
                println("RatchetState initialized: dhs.public=${ratchetState.dhs.second.encodeToBase64()}, dhr=${ratchetState.dhr?.encodeToBase64() ?: "null"}, rk=${ratchetState.rk.encodeToBase64()}, cks=${ratchetState.cks?.encodeToBase64() ?: "null"}, ckr=${ratchetState.ckr?.encodeToBase64() ?: "null"}, ad=${ratchetState.ad.encodeToBase64()}, ek=${ratchetState.ek.encodeToBase64()}")

                val session = Session(UUID.randomUUID().toString(), ratchetState, isInitiating = false)
                deviceRecord.activeSession = session
                deviceRecord.inactiveSessions.add(0, session)
                if (deviceRecord.inactiveSessions.size > MAX_INACTIVE_SESSIONS) {
                    deviceRecord.inactiveSessions.removeLast()
                }
                sessionStorage.saveUserRecords(userRecords)
                println("Saved new receiving session ${session.sessionId} for ${message.senderId}/${message.senderDeviceId}")
                session
            }

            println("Using session ${session.sessionId} for decryption")
            println("Session ratchetState: dhs.public=${session.ratchetState.dhs.second.encodeToBase64()}, dhr=${session.ratchetState.dhr?.encodeToBase64() ?: "null"}, rk=${session.ratchetState.rk.encodeToBase64()}, cks=${session.ratchetState.cks?.encodeToBase64() ?: "null"}, ckr=${session.ratchetState.ckr?.encodeToBase64() ?: "null"}, ns=${session.ratchetState.ns}, nr=${session.ratchetState.nr}, pn=${session.ratchetState.pn}, ad=${session.ratchetState.ad.encodeToBase64()}, ek=${session.ratchetState.ek.encodeToBase64()}")

            activateSession(deviceRecord, session)
            val doubleRatchet = DoubleRatchet(session.ratchetState)
            println("Attempting to decrypt with header.dh=${content.header.dh.encodeToBase64()}, header.n=${content.header.n}, ad=${session.ratchetState.ad.encodeToBase64()}")
            val plaintext = try {
                doubleRatchet.decrypt(content, session.ratchetState.ad)
            } catch (e: Exception) {
                println("DoubleRatchet.decrypt failed: ${e.message}")
                throw Exception("Failed to decrypt message: ${e.message}")
            }

            println("Decrypted plaintext: ${plaintext.decodeToString()}")
            sessionStorage.saveUserRecords(userRecords)
            println("Saved user records after decryption")
            return plaintext.decodeToString()
        } catch (e: Exception) {
            println("Failed to process message from ${message.senderId}/${message.senderDeviceId}: ${e.message}")
            throw Exception("Failed to receive message: ${e.message}")
        }
    }

    private fun cleanupStaleRecords() {
        val currentTime = System.currentTimeMillis()
        println("cleanupStaleRecords on SesameManager: currentTime=$currentTime")
        userRecords.values.removeIf { user ->
            if (user.isStale && user.staleTransitionTimestamp != null && currentTime - user.staleTransitionTimestamp!! > MAX_LATENCY) {
                println("Cleaning up stale user records for ${user.userId} at $currentTime")
                true
            } else {
                user.devices.values.removeIf { device ->
                    val shouldRemove = device.isStale && device.staleTransitionTimestamp != null && currentTime - device.staleTransitionTimestamp!! > MAX_LATENCY
                    if (shouldRemove) {
                        println("Cleaning up stale device ${device.deviceId} for ${user.userId}")
                    }
                    shouldRemove
                }
                false
            }
        }
        sessionStorage.saveUserRecords(userRecords)
    }

    private suspend fun createNewSession(userId: String, recipientUserId: String, deviceId: String): Session {
        println("createNewSession: userId=$userId, recipientUserId=$recipientUserId, deviceId=$deviceId")
        val prekeyBundle = try {
            networkManager.fetchPrekeyBundle(recipientUserId, deviceId)
        } catch (e: Exception) {
            println("Failed to fetch prekey bundle for $recipientUserId/$deviceId: ${e.message}")
            throw Exception("Cannot create session: Prekey bundle not available for $recipientUserId/$deviceId")
        }
        println("Fetched prekeyBundle: identityKey=${prekeyBundle.identityKey}, signedPreKey=${prekeyBundle.signedPreKey}, oneTimePreKey=${prekeyBundle.oneTimePreKey}, signature=${prekeyBundle.signature}")

        val identityKey = try {
            Base64.getDecoder().decode(prekeyBundle.identityKey).also {
                if (it.size != 32) throw IllegalArgumentException("Identity key must be 32 bytes, got ${it.size}")
                println("Decoded identityKey: ${it.encodeToBase64()}, size=${it.size}")
            }
        } catch (e: Exception) {
            println("Failed to decode identityKey: ${e.message}")
            throw Exception("Invalid prekey bundle: ${e.message}")
        }

        val signedPreKey = try {
            Base64.getDecoder().decode(prekeyBundle.signedPreKey).also {
                if (it.size != 32) throw IllegalArgumentException("Signed prekey must be 32 bytes, got ${it.size}")
                println("Decoded signedPreKey: ${it.encodeToBase64()}, size=${it.size}")
            }
        } catch (e: Exception) {
            println("Failed to decode signedPreKey: ${e.message}")
            throw Exception("Invalid prekey bundle: ${e.message}")
        }

        val oneTimePreKey = prekeyBundle.oneTimePreKey.let { it ->
            try {
                Base64.getDecoder().decode(it).also {
                    if (it.size != 32) throw IllegalArgumentException("One-time prekey must be 32 bytes, got ${it.size}")
                    println("Decoded oneTimePreKey: ${it.encodeToBase64()}, size=${it.size}")
                }
            } catch (e: Exception) {
                println("Failed to decode oneTimePreKey: ${e.message}")
                throw Exception("Invalid prekey bundle: ${e.message}")
            }
        }

        val signature = prekeyBundle.signature.let { it ->
            try {
                Base64.getDecoder().decode(it).also {
                    if (it.size != 64) throw IllegalArgumentException("Signature size is ${it.size} bytes, expected 64 bytes")
                    println("Decoded signature: ${it.encodeToBase64()}, size=${it.size}")
                }
            } catch (e: Exception) {
                println("Failed to decode signature: ${e.message}")
                throw Exception("Invalid prekey bundle: ${e.message}")
            }
        }

        val deviceRecord = userRecords[recipientUserId]?.devices?.get(deviceId) ?: run {
            println("Device $deviceId not found for $recipientUserId, creating new device record")
            createDeviceRecord(deviceId, recipientUserId)
        }

        if (!deviceRecord.publicKey.contentEquals(identityKey)) {
            println("Updating device $deviceId public key for $recipientUserId: new=${identityKey.encodeToBase64()}")
            deviceRecord.publicKey = identityKey
            sessionStorage.saveUserRecords(userRecords)
        }

        val x3dh = X3DH(keyManager)
        val x3dhResult = try {
            x3dh.initiate(userId, identityKey, signedPreKey, oneTimePreKey, signature)
        } catch (e: Exception) {
            println("X3DH initiation failed for $recipientUserId/$deviceId: ${e.message}")
            throw Exception("Failed to initiate X3DH: ${e.message}")
        }
        println("X3DH initiated: sk=${x3dhResult.sk.encodeToBase64()}, ad=${x3dhResult.ad.encodeToBase64()}, ek=${x3dhResult.ek.encodeToBase64()}")

        val ratchetState = try {
            RatchetState.initAsSender(x3dhResult, signedPreKey)
        } catch (e: Exception) {
            println("Failed to initialize ratchet state for $recipientUserId/$deviceId: ${e.message}")
            throw Exception("Failed to initialize ratchet state: ${e.message}")
        }
        println("RatchetState initialized: dhs.public=${ratchetState.dhs.second.encodeToBase64()}, dhr=${ratchetState.dhr?.encodeToBase64() ?: "null"}, rk=${ratchetState.rk.encodeToBase64()}, cks=${ratchetState.cks?.encodeToBase64() ?: "null"}, ckr=${ratchetState.ckr?.encodeToBase64() ?: "null"}, ad=${ratchetState.ad.encodeToBase64()}, ek = ${ratchetState.ek.encodeToBase64()}")

        val session = Session(UUID.randomUUID().toString(), ratchetState, isInitiating = true)
        deviceRecord.activeSession = session
        deviceRecord.inactiveSessions.add(0, session)

        if (deviceRecord.inactiveSessions.size > MAX_INACTIVE_SESSIONS) {
            deviceRecord.inactiveSessions.removeLast()
        }

        sessionStorage.saveUserRecords(userRecords)
        println("Saved new session ${session.sessionId} for $recipientUserId/$deviceId")
        return session
    }

//    private fun findSessionForMessage(deviceRecord: DeviceRecord, content: MessageContent): Session? {
//        println("findSessionForMessage: deviceId=${deviceRecord.deviceId}, header.dh=${content.header.dh.encodeToBase64()}")
//        if (deviceRecord.activeSession?.ratchetState?.dhr?.contentEquals(content.header.dh) == true) {
//            println("Found active session for device ${deviceRecord.deviceId}")
//            return deviceRecord.activeSession
//        }
//        val session = deviceRecord.inactiveSessions.find { it.ratchetState.dhr?.contentEquals(content.header.dh) == true }
//        println("Found session in inactive sessions: ${session?.sessionId ?: "none"}")
//        return session
//    }

    private fun findSessionForMessage(deviceRecord: DeviceRecord, content: MessageContent): Session? {
        println("findSessionForMessage: deviceId=${deviceRecord.deviceId}")
        if (deviceRecord.activeSession != null) {
            println("Found active session for device ${deviceRecord.deviceId}")
            return deviceRecord.activeSession
        }
        println("No active session found for device ${deviceRecord.deviceId}")
        return null
    }


    private fun activateSession(deviceRecord: DeviceRecord, session: Session) {
        if (deviceRecord.activeSession != session) {
            deviceRecord.inactiveSessions.remove(session)
            if (deviceRecord.activeSession != null) {
                deviceRecord.inactiveSessions.add(0, deviceRecord.activeSession!!)
            }
            deviceRecord.activeSession = session
            println("Activated session ${session.sessionId} for device ${deviceRecord.deviceId}")
        }
        sessionStorage.saveUserRecords(userRecords)
    }

    private fun updateUserRecord(userId: String, devices: Map<String, ByteArray>) {
        println("updateUserRecord: userId=$userId, devices=${devices.keys}")
        val userRecord = userRecords.getOrPut(userId) { UserRecord(userId) }
        userRecord.isStale = false
        userRecord.staleTransitionTimestamp = null
        devices.forEach { (deviceId, publicKey) ->
            if (publicKey.isEmpty()) {
                println("Skipping device $deviceId for $userId: Empty public key")
                return@forEach
            }
            val deviceRecord = userRecord.devices.getOrPut(deviceId) { DeviceRecord(deviceId, publicKey, null) }
            if (!publicKey.contentEquals(deviceRecord.publicKey)) {
                println("Updating device $deviceId with new public key ${publicKey.encodeToBase64()}")
                deviceRecord.publicKey = publicKey
                deviceRecord.activeSession = null
                deviceRecord.inactiveSessions.clear()
            }
            deviceRecord.isStale = false
            deviceRecord.staleTransitionTimestamp = null
        }
        val currentDeviceIds = devices.keys
        userRecord.devices.keys.filter { it !in currentDeviceIds }.forEach { deviceId ->
            val deviceRecord = userRecord.devices[deviceId]!!
            deviceRecord.isStale = true
            deviceRecord.staleTransitionTimestamp = System.currentTimeMillis()
            println("Marked device $deviceId as stale for $userId")
        }
        sessionStorage.saveUserRecords(userRecords)
        println("Saved updated user record for $userId")
    }

    private suspend fun handleNetworkResponse(senderId: String, response: NetworkResponse, recipientUserId: String, plaintext: ByteArray) {
        println("handleNetworkResponse: senderId=$senderId, recipientUserId=$recipientUserId, response=$response")
        when (response) {
            is NetworkResponse.Success -> {
                println("Message sent successfully to $recipientUserId")
            }
            is NetworkResponse.UserNotFound -> {
                val userRecord = userRecords[recipientUserId]
                if (userRecord != null) {
                    userRecord.isStale = true
                    userRecord.staleTransitionTimestamp = System.currentTimeMillis()
                    sessionStorage.saveUserRecords(userRecords)
                    println("Marked user $recipientUserId as stale due to UserNotFound")
                }
            }
            is NetworkResponse.DeviceMismatch -> {
                val userRecord = userRecords[recipientUserId]!!
                response.oldDevices.forEach { deviceId ->
                    val deviceRecord = userRecord.devices[deviceId]
                    if (deviceRecord != null) {
                        deviceRecord.isStale = true
                        deviceRecord.staleTransitionTimestamp = System.currentTimeMillis()
                        println("Marked device $deviceId as stale for $recipientUserId")
                    }
                }
                updateUserRecord(recipientUserId, response.newDevices)
                println("Retrying message send to $recipientUserId after DeviceMismatch")
                sendMessage(senderId, recipientUserId, plaintext)
            }
        }
    }

    private fun createUserRecord(userId: String): UserRecord {
        println("createUserRecord: userId=$userId")
        val userRecord = UserRecord(userId)
        userRecords[userId] = userRecord
        sessionStorage.saveUserRecords(userRecords)
        println("Saved new user record for $userId")
        return userRecord
    }

    private fun createDeviceRecord(deviceId: String, userId: String): DeviceRecord {
        println("createDeviceRecord: userId=$userId, deviceId=$deviceId")
        val deviceRecord = DeviceRecord(deviceId, byteArrayOf(), null)
        userRecords[userId]!!.devices[deviceId] = deviceRecord
        sessionStorage.saveUserRecords(userRecords)
        println("Saved new device record $deviceId for $userId")
        return deviceRecord
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
}