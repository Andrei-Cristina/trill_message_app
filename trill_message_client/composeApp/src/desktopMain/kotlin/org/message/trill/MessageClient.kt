package org.message.trill

import org.message.trill.encryption.keys.KeyManager
import org.message.trill.networking.NetworkManager
import org.message.trill.session.sesame.DeviceRecord
import org.message.trill.session.sesame.SesameManager
import org.message.trill.session.sesame.UserRecord
import org.message.trill.session.storage.SessionStorage

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
}