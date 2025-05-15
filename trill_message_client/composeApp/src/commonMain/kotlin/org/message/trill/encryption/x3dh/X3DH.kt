package org.message.trill.encryption.x3dh

import org.message.trill.encryption.keys.KeyManager
import org.message.trill.encryption.utils.EncryptionUtils
import java.util.*


class X3DH(private val keyManager: KeyManager) {
    fun initiate(
        userId: String,
        recipientIdentityKey: ByteArray,
        recipientSignedPreKey: ByteArray,
        recipientOneTimePreKey: ByteArray?,
        recipientSignature: ByteArray
    ): X3DHResult {
        println("X3DH.initiate: userId=$userId, recipientIdentityKey=${recipientIdentityKey.encodeToBase64()}, signedPreKey=${recipientSignedPreKey.encodeToBase64()}, oneTimePreKey=${recipientOneTimePreKey?.encodeToBase64() ?: "null"}")

        if (recipientIdentityKey.size != 32) {
            println("Error: recipientIdentityKey size=${recipientIdentityKey.size}, expected 32")
            throw IllegalArgumentException("recipientIdentityKey must be 32 bytes")
        }
        if (recipientSignedPreKey.size != 32) {
            println("Error: recipientSignedPreKey size=${recipientSignedPreKey.size}, expected 32")
            throw IllegalArgumentException("recipientSignedPreKey must be 32 bytes")
        }
        if (recipientOneTimePreKey != null && recipientOneTimePreKey.size != 32) {
            println("Error: recipientOneTimePreKey size=${recipientOneTimePreKey.size}, expected 32")
            throw IllegalArgumentException("recipientOneTimePreKey must be 32 bytes")
        }

        val identityKey = keyManager.getIdentityKey(userId)
        println("IdentityKey: public=${identityKey.publicKey.encodeToBase64()}, private size=${identityKey.privateKey.size}")

        val ephemeralKey = EncryptionUtils.generateKeyPair()
        println("EphemeralKey: public=${ephemeralKey.second.encodeToBase64()}, private size=${ephemeralKey.first.size}")

//        if (!verifySignature(recipientIdentityKey, recipientSignedPreKey, recipientSignature)) {
//            throw Exception("Signature verification failed")
//        }

        val dh1 = EncryptionUtils.dh(identityKey.privateKey, recipientSignedPreKey)
        val dh2 = EncryptionUtils.dh(ephemeralKey.first, recipientIdentityKey)
        val dh3 = EncryptionUtils.dh(ephemeralKey.first, recipientSignedPreKey)
        //val dh4 = recipientOneTimePreKey?.let { EncryptionUtils.dh(ephemeralKey.first, it) }

        println("DH outputs: dh1 size=${dh1.size}, dh2 size=${dh2.size}, dh3 size=${dh3.size}")

//        val km = if (dh4 != null) dh1 + dh2 + dh3 + dh4 else dh1 + dh2 + dh3
        val km = dh1 + dh2 + dh3
        println("Key material (km) size=${km.size}")

        val sk = EncryptionUtils.hkdf(
            salt = ByteArray(32) { 0xFF.toByte() },
            ikm = km,
            info = "TRILL_X3DH".toByteArray(),
            length = 32
        )
        println("Shared secret (sk): ${sk.encodeToBase64()}, size=${sk.size}")

        val ad = identityKey.publicKey + recipientIdentityKey
        println("Associated data (ad): ${ad.encodeToBase64()}, size=${ad.size}")

        return X3DHResult(sk, ad, ephemeralKey.second, ephemeralKey.first)
    }

    fun receive(
        userId: String,
        senderIdentityKey: ByteArray,
        senderEphemeralKey: ByteArray
    ): X3DHResult {
        println("X3DH.receive: userId=$userId, senderIdentityKey=${senderIdentityKey.encodeToBase64()}, senderEphemeralKey=${senderEphemeralKey.encodeToBase64()}")

        if (senderIdentityKey.size != 32) {
            println("Error: senderIdentityKey size=${senderIdentityKey.size}, expected 32")
            throw IllegalArgumentException("senderIdentityKey must be 32 bytes")
        }
        if (senderEphemeralKey.size != 32) {
            println("Error: senderEphemeralKey size=${senderEphemeralKey.size}, expected 32")
            throw IllegalArgumentException("senderEphemeralKey must be 32 bytes")
        }

        val identityKey = keyManager.getIdentityKey(userId)
        val signedPreKey = keyManager.getSignedPreKey(userId)
        println("IdentityKey: public=${identityKey.publicKey.encodeToBase64()}, private size=${identityKey.privateKey.size}")
        println("SignedPreKey: public=${signedPreKey.preKey.publicKey.encodeToBase64()}, private size=${signedPreKey.preKey.privateKey.size}")

        val dh1 = EncryptionUtils.dh(signedPreKey.preKey.privateKey, senderIdentityKey)
        val dh2 = EncryptionUtils.dh(identityKey.privateKey, senderEphemeralKey)
        val dh3 = EncryptionUtils.dh(signedPreKey.preKey.privateKey, senderEphemeralKey)
        // val dh4 = oneTimePreKey?.privateKey?.let { EncryptionUtils.dh(it, senderEphemeralKey) }

        println("DH outputs: dh1 size=${dh1.size}, dh2 size=${dh2.size}, dh3 size=${dh3.size}")

        val km = dh1 + dh2 + dh3
        println("Key material (km) size=${km.size}")

        val sk = EncryptionUtils.hkdf(
            salt = ByteArray(32) { 0xFF.toByte() },
            ikm = km,
            info = "TRILL_X3DH".toByteArray(),
            length = 32
        )
        println("Shared secret (sk): ${sk.encodeToBase64()}, size=${sk.size}")

        // Ensure consistent ad order: senderIdentityKey + recipientIdentityKey
        val ad = senderIdentityKey + identityKey.publicKey
        println("Associated data (ad): ${ad.encodeToBase64()}, size=${ad.size}")

        return X3DHResult(sk, ad, senderEphemeralKey, ByteArray(32))
    }

    private fun verifySignature(pubKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean =
        EncryptionUtils.verify(publicKey = pubKey, data = data, signature = signature)

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
}