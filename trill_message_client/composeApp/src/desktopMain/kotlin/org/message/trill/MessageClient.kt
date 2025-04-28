package org.message.trill

import org.message.trill.encryption.keys.KeyManager
import org.message.trill.messaging.models.ReceivedMessage
import org.message.trill.networking.NetworkManager
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.SesameManager
import org.message.trill.session.sesame.UserRecord
import org.message.trill.session.storage.SessionStorage

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

        sessionStorage.setClientInfo(email, nickname, identityKey.publicKey.toString())
        sessionStorage.saveDeviceRecord(email, nickname, DeviceRecord(identityKey.publicKey.toString(), identityKey.publicKey, null))
        networkManager.registerDevice(email, identityKey = identityKey.publicKey, signedPreKey = signedPreKey, oneTimePreKeys = oneTimePreKeys)
    }

    actual suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: String) {
        sesameManager.sendMessage(senderId ,recipientUserId, plaintext.toByteArray(Charsets.UTF_8))
    }

    actual suspend fun receiveMessages(email: String): List<ReceivedMessage> {
        val messages = networkManager.fetchMessages(email, sessionStorage.loadDeviceId(email))
        return messages.map { message ->
            val plaintext = sesameManager.receiveMessage(message)
            ReceivedMessage(message.senderId, plaintext, message.timestamp)
        }
    }

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
}