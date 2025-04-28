package org.message.trill.encryption.keys

import org.message.trill.session.storage.SessionStorage

class KeyManager(private val storage: SessionStorage) {
    fun generateIdentityKey(): IdentityKey =
        IdentityKey.generate()

    fun getIdentityKey(userEmail: String): IdentityKey = storage.getIdentityKey(userEmail)
        ?: throw IllegalStateException("No identity key found for $userEmail")

    fun generateSignedPreKey(): SignedPreKey = SignedPreKey.generate()

    fun getSignedPreKey(userEmail: String): SignedPreKey = storage.getSignedPreKey(userEmail)
        ?: throw IllegalStateException("No signed pre-key found for $userEmail")

    fun generateOneTimePreKeys(count: Int): List<PreKey> = (1..count).map { PreKey.generate(it) }

    fun getOneTimePreKey(userEmail: String): PreKey = storage.getOneTimePreKey(userEmail)

    fun deleteOneTimePreKey(userEmail: String, id: Int) {
        storage.removeOneTimePreKey(userEmail, id)
    }
}