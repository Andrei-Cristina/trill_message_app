package org.message.trill

import org.message.trill.encryption.keys.KeyManager
import org.message.trill.networking.NetworkManager
import org.message.trill.networking.models.LoginRequest
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.SesameManager
import org.message.trill.session.sesame.UserRecord
import org.message.trill.session.storage.SessionStorage
import java.util.*

actual class MessageClient actual constructor(
    private val userId: String
) {
    private val sessionStorage = SessionStorage()
    private val networkManager = NetworkManager()
    private val keyManager = KeyManager(sessionStorage)
    private val sesameManager = SesameManager(keyManager, networkManager, sessionStorage)


    actual suspend fun registerUser(email:String, nickname: String) {
        sessionStorage.saveUserRecords(mutableMapOf(email to UserRecord(userId = email, nickname = nickname)) )

        networkManager.registerUser(email, nickname)
    }

    actual suspend fun registerDevice(email: String, nickname: String) {
        println("Pulait")
        val identityKey = keyManager.generateIdentityKey()
        sessionStorage.storeIdentityKey(identityKey)

        val signedPreKey = keyManager.generateSignedPreKey()
        sessionStorage.storeSignedPreKey(signedPreKey)

        val oneTimePreKeys = keyManager.generateOneTimePreKeys(10)
        sessionStorage.storeOneTimePreKeys(oneTimePreKeys)

        sessionStorage.setClientInfo(email, nickname, identityKey.publicKey.toString())
        sessionStorage.saveDeviceRecord(email, nickname, DeviceRecord(identityKey.publicKey.toString(), identityKey.publicKey, null))
        networkManager.registerDevice(email, identityKey = identityKey.publicKey, signedPreKey = signedPreKey, oneTimePreKeys = oneTimePreKeys)
    }

    actual suspend fun sendMessage(recipientUserId: String, plaintext: String) {
        sesameManager.sendMessage(recipientUserId, plaintext.toByteArray(Charsets.UTF_8))
    }

    actual suspend fun receiveMessages(): List<String> {
        val messages = networkManager.fetchMessages(sessionStorage.loadUserEmail(), sessionStorage.loadDeviceId())

        return messages.map { message ->
            sesameManager.receiveMessage(message)
        }
    }

    actual suspend fun loginUser(email: String, nickname: String): String {
        val deviceId = sessionStorage.loadDeviceId()

        val identityKey = sessionStorage.getDevicePublicKey(deviceId)
            ?: throw Exception("No device public key found for deviceId: $deviceId")
        println("identityKey: $identityKey")

        return networkManager.login(email, nickname, identityKey)
            ?: throw Exception("Login failed: Device not registered")
    }
}