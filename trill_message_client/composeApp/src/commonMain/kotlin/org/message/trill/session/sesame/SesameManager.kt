package org.message.trill.session.sesame

import kotlinx.datetime.Clock
import org.message.trill.encryption.double_ratchet.DoubleRatchet
import org.message.trill.encryption.double_ratchet.RatchetState
import org.message.trill.encryption.keys.KeyManager
import org.message.trill.encryption.x3dh.X3DH
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

    /**
     * Sends an encrypted message to all active devices of the recipient.
     */
    suspend fun sendMessage(recipientUserId: String, plaintext: ByteArray) {
        cleanupStaleRecords()

        val recipientUserRecord = userRecords[recipientUserId]

        if (recipientUserRecord == null || recipientUserRecord.isStale) {
            updateUserRecord(recipientUserId, networkManager.fetchUserDevices(recipientUserId))
        }

        val messages = mutableListOf<Message>()
        val updatedUserRecord = userRecords[recipientUserId]!!

        for (device in updatedUserRecord.devices.values) {
            if (device.isStale) continue

            val session = device.activeSession ?: createNewSession(recipientUserId, device.deviceId)
            val doubleRatchet = DoubleRatchet(session.ratchetState)
            val messageContent = doubleRatchet.encrypt(plaintext, session.ratchetState.ad)

            val message = Message(
                senderId = sessionStorage.loadUserEmail(),
                senderDeviceId = sessionStorage.loadDeviceId(),
                recipientId = recipientUserId,
                recipientDeviceId = device.deviceId,
                content = messageContent,
                timestamp = Clock.System.now().toString()
            )

            messages.add(message)
        }

        val response = networkManager.sendMessages(messages)

        handleNetworkResponse(response, recipientUserId, plaintext)
    }

    /**
     * Receives and decrypts a message from a sender's device.
     */
    fun receiveMessage(message: Message): String {
        val userRecord = userRecords[message.senderId] ?: createUserRecord(message.senderId)

        if (userRecord.isStale) {
            userRecord.isStale = false
            userRecord.staleTransitionTimestamp = null
        }

        val deviceRecord = userRecord.devices[message.senderId] ?: createDeviceRecord(message.senderDeviceId, message.senderId)

        if (deviceRecord.isStale) {
            deviceRecord.isStale = false
            deviceRecord.staleTransitionTimestamp = null
        }

        val session = findSessionForMessage(deviceRecord, message.content)

        if (session == null) {
            val x3dh = X3DH(keyManager)
            val x3dhResult = x3dh.receive(message.content.header.dh, message.content.header.dh)
            val ratchetState = RatchetState.initAsReceiver(x3dhResult, keyManager.getSignedPreKey().preKey)
            val newSession = Session(UUID.randomUUID().toString(), ratchetState, isInitiating = false)

            deviceRecord.activeSession = newSession
            deviceRecord.inactiveSessions.add(0, newSession)

            if (deviceRecord.inactiveSessions.size > MAX_INACTIVE_SESSIONS) {
                deviceRecord.inactiveSessions.removeLast()
            }

        } else {
            activateSession(deviceRecord, session)
        }

        val doubleRatchet = DoubleRatchet(session!!.ratchetState)
        val plaintext = doubleRatchet.decrypt(message.content, session.ratchetState.ad)

        return plaintext.toString()
    }

    /**
     * Cleans up stale user and device records that have been stale for longer than MAX_LATENCY.
     */
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

    /**
     * Creates a new session with a recipient device using X3DH.
     */
    private suspend fun createNewSession(recipientUserId: String, deviceId: String): Session {
        val prekeyBundle = networkManager.fetchPrekeyBundle(recipientUserId, deviceId)
        val x3dh = X3DH(keyManager)
        val x3dhResult = x3dh.initiate(
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

    /**
     * Finds a session capable of decrypting the incoming message.
     */
    private fun findSessionForMessage(deviceRecord: DeviceRecord, message: MessageContent): Session? {
        if (deviceRecord.activeSession?.ratchetState?.dhr?.contentEquals(message.header.dh) == true) {
            return deviceRecord.activeSession
        }

        return deviceRecord.inactiveSessions.find { it.ratchetState.dhr?.contentEquals(message.header.dh) == true }
    }

    /**
     * Activates a session, moving it to the active slot if necessary.
     */
    private fun activateSession(deviceRecord: DeviceRecord, session: Session) {
        if (deviceRecord.activeSession != session) {
            deviceRecord.inactiveSessions.remove(session)
            deviceRecord.inactiveSessions.add(0, deviceRecord.activeSession!!)
            deviceRecord.activeSession = session
        }

        sessionStorage.saveUserRecords(userRecords)
    }

    /**
     * Updates the user record with the latest device information.
     */
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

    /**
     * Handles network responses, including retries on device mismatches.
     */
    private suspend fun handleNetworkResponse(response: NetworkResponse, recipientUserId: String, plaintext: ByteArray) {
        when (response) {
            is NetworkResponse.Success -> {

            }
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

                sendMessage(recipientUserId, plaintext)
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
}