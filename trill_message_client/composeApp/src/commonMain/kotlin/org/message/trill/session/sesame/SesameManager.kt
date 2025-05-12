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

        val doubleRatchet = DoubleRatchet(session.ratchetState)
        val (header, ciphertext) = if (isInitial) {
            val identityKey = keyManager.getIdentityKey(senderId).publicKey
            val (initialHeader, initialCiphertext) = doubleRatchet.encrypt(plaintext, session.ratchetState.ad)
            val modifiedCiphertext = identityKey + initialCiphertext
            initialHeader.copy(n = -1) to modifiedCiphertext
        } else {
            doubleRatchet.encrypt(plaintext, session.ratchetState.ad)
        }
        return MessageContent(header, ciphertext)
    }

    suspend fun receiveMessage(message: Message): String {
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
        return if (content.header.n == -1) {
            val identityKeySize = 32
            if (content.ciphertext.size < identityKeySize) {
                throw IllegalStateException("Invalid initial message ciphertext")
            }
            val senderIdentityKey = content.ciphertext.copyOfRange(0, identityKeySize)
            val actualCiphertext = content.ciphertext.copyOfRange(identityKeySize, content.ciphertext.size)

            val x3dh = X3DH(keyManager)
            val x3dhResult = x3dh.receive(message.senderId, senderIdentityKey, content.header.dh)
            val signedPreKey = keyManager.getSignedPreKey(message.senderId).preKey
            val ratchetState = RatchetState.initAsReceiver(x3dhResult, signedPreKey)
            val session = Session(UUID.randomUUID().toString(), ratchetState, isInitiating = false)
            val doubleRatchet = DoubleRatchet(ratchetState)
            val modifiedContent = MessageContent(content.header.copy(n = 0), actualCiphertext)
            val plaintext = doubleRatchet.decrypt(modifiedContent, session.ratchetState.ad)

            deviceRecord.activeSession = session
            deviceRecord.inactiveSessions.add(0, session)
            if (deviceRecord.inactiveSessions.size > MAX_INACTIVE_SESSIONS) {
                deviceRecord.inactiveSessions.removeLast()
            }

            println("Saved new session ${session.sessionId} for ${message.senderId}/${message.senderDeviceId}")

            sessionStorage.saveUserRecords(userRecords)
            plaintext.decodeToString()
        } else {
            val session = findSessionForMessage(deviceRecord, content) ?: run {
                println("No session found for message from ${message.senderId}, creating new session")
                createNewSession(message.senderId, message.senderId, message.senderDeviceId)
            }
            activateSession(deviceRecord, session)

            val doubleRatchet = DoubleRatchet(session.ratchetState)
            val plaintext = doubleRatchet.decrypt(content, session.ratchetState.ad)

            println("Decrypted message for ${message.senderId}/${message.senderDeviceId} with session ${session.sessionId}")

            sessionStorage.saveUserRecords(userRecords)
            plaintext.decodeToString()
        }
    }

    private fun cleanupStaleRecords() {
        val currentTime = System.currentTimeMillis()
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
        println("Creating new session for $recipientUserId with device ID $deviceId")
        val prekeyBundle = try {
            networkManager.fetchPrekeyBundle(recipientUserId, deviceId)
        } catch (e: Exception) {
            println("Failed to fetch prekey bundle for $recipientUserId/$deviceId: ${e.message}")
            throw Exception("Cannot create session: Prekey bundle not available for $recipientUserId/$deviceId")
        }

        val identityKey = try {
            Base64.getDecoder().decode(prekeyBundle.identityKey).also {
                if (it.size != 32) throw IllegalArgumentException("Identity key must be 32 bytes, got ${it.size}")
            }
        } catch (e: Exception) {
            println("Failed to decode identityKey: ${e.message}")
            throw Exception("Invalid prekey bundle: ${e.message}")
        }

        val signedPreKey = try {
            Base64.getDecoder().decode(prekeyBundle.signedPreKey).also {
                if (it.size != 32) throw IllegalArgumentException("Signed prekey must be 32 bytes, got ${it.size}")
            }
        } catch (e: Exception) {
            println("Failed to decode signedPreKey: ${e.message}")
            throw Exception("Invalid prekey bundle: ${e.message}")
        }

        val oneTimePreKey = prekeyBundle.oneTimePreKey.let { it ->
            try {
                Base64.getDecoder().decode(it).also {
                    if (it.size != 32) throw IllegalArgumentException("One-time prekey must be 32 bytes, got ${it.size}")
                }
            } catch (e: Exception) {
                println("Failed to decode oneTimePreKey: ${e.message}")
                throw Exception("Invalid prekey bundle: ${e.message}")
            }
        }

//        sessionStorage.getSignedPreKey(userId)?.let { key ->
//            println("Users key: ${key.signature.encodeToBase64()}")
//        }

        val signature = prekeyBundle.signature.let { it ->
            try {
                Base64.getDecoder().decode(it).also {
                    if (it.size > 64 ) throw IllegalArgumentException("Signature size is ${it.size} bytes, expected 64 bytes")
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
            println("Updating device $deviceId public key for $recipientUserId")
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

        val ratchetState = try {
            RatchetState.initAsSender(x3dhResult, signedPreKey)
        } catch (e: Exception) {
            println("Failed to initialize ratchet state for $recipientUserId/$deviceId: ${e.message}")
            throw Exception("Failed to initialize ratchet state: ${e.message}")
        }

        val session = Session(UUID.randomUUID().toString(), ratchetState, isInitiating = true)
        val newDeviceRecord = userRecords[recipientUserId]?.devices?.get(deviceId) ?: run {
            println("Device $deviceId not found for $recipientUserId, creating new device record")
            createDeviceRecord(deviceId, recipientUserId)
        }

        newDeviceRecord.activeSession = session
        newDeviceRecord.inactiveSessions.add(0, session)

        if (newDeviceRecord.inactiveSessions.size > MAX_INACTIVE_SESSIONS) {
            newDeviceRecord.inactiveSessions.removeLast()
        }

        try {
            sessionStorage.saveUserRecords(userRecords)
            println("Saved new session ${session.sessionId} for $recipientUserId/$deviceId")
        } catch (e: Exception) {
            println("Failed to save session for $recipientUserId/$deviceId: ${e.message}")
            throw Exception("Failed to save session: ${e.message}")
        }

        return session
    }

    private fun findSessionForMessage(deviceRecord: DeviceRecord, content: MessageContent): Session? {
        println("Finding session for message from device ${deviceRecord.deviceId}")
        if (deviceRecord.activeSession?.ratchetState?.dhr?.contentEquals(content.header.dh) == true) {
            return deviceRecord.activeSession
        }
        return deviceRecord.inactiveSessions.find { it.ratchetState.dhr?.contentEquals(content.header.dh) == true }
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
        println("Updating user record for $userId with ${devices.size} devices")
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
        try {
            sessionStorage.saveUserRecords(userRecords)
            println("Saved updated user record for $userId")
        } catch (e: Exception) {
            println("Failed to save user record for $userId: ${e.message}")
            throw Exception("Failed to save user record for $userId: ${e.message}")
        }
    }

    private suspend fun handleNetworkResponse(senderId: String, response: NetworkResponse, recipientUserId: String, plaintext: ByteArray) {
        println("Handling network response for $recipientUserId: $response")
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
        println("Creating user record for $userId")
        val userRecord = UserRecord(userId)
        userRecords[userId] = userRecord
        try {
            sessionStorage.saveUserRecords(userRecords)
            println("Saved new user record for $userId")
        } catch (e: Exception) {
            println("Failed to save user record for $userId: ${e.message}")
            throw Exception("Failed to save user record: ${e.message}")
        }
        return userRecord
    }

    private fun createDeviceRecord(deviceId: String, userId: String): DeviceRecord {
        println("Creating device record for $userId with device ID $deviceId")
        val deviceRecord = DeviceRecord(deviceId, byteArrayOf(), null)
        userRecords[userId]!!.devices[deviceId] = deviceRecord
        try {
            sessionStorage.saveUserRecords(userRecords)
            println("Saved new device record $deviceId for $userId")
        } catch (e: Exception) {
            println("Failed to save device record $deviceId for $userId: ${e.message}")
            throw Exception("Failed to save device record: ${e.message}")
        }
        return deviceRecord
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
}