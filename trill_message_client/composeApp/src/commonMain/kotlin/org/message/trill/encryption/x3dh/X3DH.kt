package org.message.trill.encryption.x3dh

import org.message.trill.encryption.keys.KeyManager
import org.message.trill.encryption.utils.EncryptionUtils


class X3DH(private val keyManager: KeyManager) {
    fun initiate(
        recipientIdentityKey: ByteArray,
        recipientSignedPreKey: ByteArray,
        recipientOneTimePreKey: ByteArray?,
        recipientSignature: ByteArray
    ): X3DHResult {
        val identityKey = keyManager.getIdentityKey()
        val ephemeralKey = EncryptionUtils.generateKeyPair()

        if (!verifySignature(recipientIdentityKey, recipientSignedPreKey, recipientSignature)) {
            throw Exception("Signature verification failed")
        }

        val dh1 = EncryptionUtils.dh(identityKey.privateKey, recipientSignedPreKey)
        val dh2 = EncryptionUtils.dh(ephemeralKey.first, recipientIdentityKey)
        val dh3 = EncryptionUtils.dh(ephemeralKey.first, recipientSignedPreKey)
        val dh4 = recipientOneTimePreKey?.let { EncryptionUtils.dh(ephemeralKey.first, it) }

        val km = if (dh4 != null) dh1 + dh2 + dh3 + dh4 else dh1 + dh2 + dh3
        val sk =
            EncryptionUtils.hkdf(ByteArray(32) { 0xFF.toByte() }, km, "TRILL".toByteArray(), 32)

        val ad = identityKey.publicKey + recipientIdentityKey

        return X3DHResult(sk, ad, ephemeralKey.second)
    }

    fun receive(
        senderIdentityKey: ByteArray,
        senderEphemeralKey: ByteArray
    ): X3DHResult {
        val identityKey = keyManager.getIdentityKey()
        val signedPreKey = keyManager.getSignedPreKey()
        val oneTimePreKey = keyManager.getOnetimePreKey()

        val dh1 = EncryptionUtils.dh(identityKey.privateKey, senderIdentityKey)
        val dh2 = EncryptionUtils.dh(senderEphemeralKey, identityKey.privateKey)
        val dh3 = EncryptionUtils.dh(signedPreKey.preKey.privateKey, senderEphemeralKey)
        val dh4 = oneTimePreKey.privateKey.let { EncryptionUtils.dh(it, senderEphemeralKey) }

        val km = dh1 + dh2 + dh3 + dh4
        val sk =
            EncryptionUtils.hkdf(ByteArray(32) { 0xFF.toByte() }, km, "TRILL".toByteArray(), 32)

        val ad = senderIdentityKey + identityKey.publicKey

        keyManager.deleteOnetimePreKey(oneTimePreKey.id)

        return X3DHResult(sk, ad, senderEphemeralKey)
    }

    private fun verifySignature(pubKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean =
        EncryptionUtils.verify(publicKey = pubKey, data = data, signature = signature)

}