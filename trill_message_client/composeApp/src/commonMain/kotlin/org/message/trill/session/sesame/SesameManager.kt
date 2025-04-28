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
import java.util.UUID

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
    }

    suspend fun prepareMessageContent(senderId: String, recipientUserId: String, recipientDeviceId: String, plaintext: ByteArray): MessageContent {
        cleanupStaleRecords()

        val recipientUserRecord = userRecords[recipientUserId] ?: createUserRecord(recipientUserId)

        if (recipientUserRecord.isStale) {
            updateUserRecord(recipientUserId, networkManager.fetchUserDevices(recipientUserId))
        }

        val deviceRecord = recipientUserRecord.devices[recipientDeviceId] ?: createDeviceRecord(recipientDeviceId, recipientUserId)

        val (session, isInitial) = deviceRecord.activeSession?.let { it to false } ?: (createNewSession(
            senderId,
            recipientUserId,
            recipientDeviceId
        ) to true)

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

    fun receiveMessage(message: Message): String {
        val userRecord = userRecords[message.senderId] ?: createUserRecord(message.senderId)

        if (userRecord.isStale) {
            userRecord.isStale = false
            userRecord.staleTransitionTimestamp = null
        }

        val deviceRecord = userRecord.devices[message.senderDeviceId] ?: createDeviceRecord(message.senderDeviceId, message.senderId)

        if (deviceRecord.isStale) {
            deviceRecord.isStale = false
            deviceRecord.staleTransitionTimestamp = null
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
            val ratchetState = RatchetState.initAsReceiver(x3dhResult, keyManager.getSignedPreKey(message.senderId).preKey)
            val session = Session(UUID.randomUUID().toString(), ratchetState, isInitiating = false)
            val doubleRatchet = DoubleRatchet(ratchetState)
            val modifiedContent = MessageContent(content.header.copy(n = 0), actualCiphertext)
            val plaintext = doubleRatchet.decrypt(modifiedContent, session.ratchetState.ad)

            deviceRecord.activeSession = session
            deviceRecord.inactiveSessions.add(0, session)

            if (deviceRecord.inactiveSessions.size > MAX_INACTIVE_SESSIONS) {
                deviceRecord.inactiveSessions.removeLast()
            }

            sessionStorage.saveUserRecords(userRecords)
            plaintext.decodeToString()
        } else {
            val session = findSessionForMessage(deviceRecord, content)
                ?: throw Exception("No session found for message")

            activateSession(deviceRecord, session)

            val doubleRatchet = DoubleRatchet(session.ratchetState)
            val plaintext = doubleRatchet.decrypt(content, session.ratchetState.ad)

            sessionStorage.saveUserRecords(userRecords)
            plaintext.decodeToString()
        }
    }

    private fun cleanupStaleRecords() {
        val currentTime = System.currentTimeMillis()
        userRecords.values.removeIf { user ->
            if (user.isStale && user.staleTransitionTimestamp != null && currentTime - user.staleTransitionTimestamp!! > MAX_LATENCY) {
                true
            } else {
                user.devices.values.removeIf { device ->
                    device.isStale && device.staleTransitionTimestamp != null && currentTime - device.staleTransitionTimestamp!! > MAX_LATENCY
                }
                false
            }
        }
        sessionStorage.saveUserRecords(userRecords)
    }

    private suspend fun createNewSession(userId: String, recipientUserId: String, deviceId: String): Session {
        val prekeyBundle = networkManager.fetchPrekeyBundle(recipientUserId, deviceId)
        val x3dh = X3DH(keyManager)
        val x3dhResult = x3dh.initiate(
            userId,
            prekeyBundle.identityKey,
            prekeyBundle.signedPreKey,
            prekeyBundle.oneTimePreKey,
            prekeyBundle.signature
        )
        val ratchetState = RatchetState.initAsSender(x3dhResult, prekeyBundle.signedPreKey)
        val session = Session(UUID.randomUUID().toString(), ratchetState, isInitiating = true)
        val deviceRecord = userRecords[recipientUserId]!!.devices[deviceId]!!

        deviceRecord.activeSession = session
        deviceRecord.inactiveSessions.add(0, session)
        if (deviceRecord.inactiveSessions.size > MAX_INACTIVE_SESSIONS) {
            deviceRecord.inactiveSessions.removeLast()
        }
        sessionStorage.saveUserRecords(userRecords)
        return session
    }

    private fun findSessionForMessage(deviceRecord: DeviceRecord, content: MessageContent): Session? {
        if (deviceRecord.activeSession?.ratchetState?.dhr?.contentEquals(content.header.dh) == true) {
            return deviceRecord.activeSession
        }
        return deviceRecord.inactiveSessions.find { it.ratchetState.dhr?.contentEquals(content.header.dh) == true }
    }

    private fun activateSession(deviceRecord: DeviceRecord, session: Session) {
        if (deviceRecord.activeSession != session) {
            deviceRecord.inactiveSessions.remove(session)
            deviceRecord.inactiveSessions.add(0, deviceRecord.activeSession!!)
            deviceRecord.activeSession = session
        }
        sessionStorage.saveUserRecords(userRecords)
    }

    private fun updateUserRecord(userId: String, devices: Map<String, ByteArray>) {
        val userRecord = userRecords.getOrPut(userId) { UserRecord(userId) }
        devices.forEach { (deviceId, publicKey) ->
            val deviceRecord = userRecord.devices.getOrPut(deviceId) { DeviceRecord(deviceId, publicKey, null) }
            if (!publicKey.contentEquals(deviceRecord.publicKey)) {
                deviceRecord.publicKey = publicKey
                deviceRecord.activeSession = null
                deviceRecord.inactiveSessions.clear()
            }
        }
        val currentDeviceIds = devices.keys
        userRecord.devices.keys.filter { it !in currentDeviceIds }.forEach { deviceId ->
            val deviceRecord = userRecord.devices[deviceId]!!
            deviceRecord.isStale = true
            deviceRecord.staleTransitionTimestamp = System.currentTimeMillis()
        }
        sessionStorage.saveUserRecords(userRecords)
    }

    private suspend fun handleNetworkResponse(senderId:String, response: NetworkResponse, recipientUserId: String, plaintext: ByteArray) {
        when (response) {
            is NetworkResponse.Success -> {}
            is NetworkResponse.UserNotFound -> {
                val userRecord = userRecords[recipientUserId]
                if (userRecord != null) {
                    userRecord.isStale = true
                    userRecord.staleTransitionTimestamp = System.currentTimeMillis()
                    sessionStorage.saveUserRecords(userRecords)
                }
            }
            is NetworkResponse.DeviceMismatch -> {
                val userRecord = userRecords[recipientUserId]!!
                response.oldDevices.forEach { deviceId ->
                    val deviceRecord = userRecord.devices[deviceId]
                    if (deviceRecord != null) {
                        deviceRecord.isStale = true
                        deviceRecord.staleTransitionTimestamp = System.currentTimeMillis()
                    }
                }
                updateUserRecord(recipientUserId, response.newDevices)
                sendMessage(senderId, recipientUserId, plaintext)
            }
        }
    }

    private fun createUserRecord(userId: String): UserRecord {
        val userRecord = UserRecord(userId)
        userRecords[userId] = userRecord
        sessionStorage.saveUserRecords(userRecords)
        return userRecord
    }

    private fun createDeviceRecord(deviceId: String, userId: String): DeviceRecord {
        val deviceRecord = DeviceRecord(deviceId, byteArrayOf(), null)
        userRecords[userId]!!.devices[deviceId] = deviceRecord
        sessionStorage.saveUserRecords(userRecords)
        return deviceRecord
    }

    suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: ByteArray) {
        cleanupStaleRecords()

        val recipientUserRecord = userRecords[recipientUserId] ?: createUserRecord(recipientUserId)

        if (recipientUserRecord.isStale) {
            updateUserRecord(recipientUserId, networkManager.fetchUserDevices(recipientUserId))
        }

        val messages = mutableListOf<Message>()
        val updatedUserRecord = userRecords[recipientUserId]!!

        for (device in updatedUserRecord.devices.values) {
            if (device.isStale) continue

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

        val response = networkManager.sendMessages(messages)
        handleNetworkResponse(senderId, response, recipientUserId, plaintext)
    }
}