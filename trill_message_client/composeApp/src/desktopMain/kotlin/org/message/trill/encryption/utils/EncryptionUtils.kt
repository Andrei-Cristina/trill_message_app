package org.message.trill.encryption.utils

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.utils.Key
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Actual implementation of CryptoUtils for JVM platforms (Android and Desktop).
 * This object provides cryptographic utilities using libsodium via lazysodium-java,
 * supporting operations like key generation, encryption, and signing for the Signal Protocol.
 */
actual object EncryptionUtils {
    private val sodium = LazySodiumJava(SodiumJava())

    actual fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val keyPair = sodium.cryptoBoxKeypair()
        return keyPair.secretKey.asBytes to keyPair.publicKey.asBytes
    }

    actual fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val sharedSecret = ByteArray(32)

        if (!sodium.cryptoScalarMult(sharedSecret, privateKey, publicKey)) {
            throw Exception("Diffie-Hellman computation failed")
        }

        return sharedSecret
    }

    actual fun sha256(input: ByteArray): ByteArray {
        return sodium.cryptoHashSha256(input.toString()).toByteArray()
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArray(32)

        if (!sodium.cryptoAuthHMACSha256(out, data, data.size.toLong(), key)) {
            throw Exception("HMAC-SHA256 computation failed")
        }

        return out
    }

    actual fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        // Extract phase: PRK = HMAC-SHA256(salt, IKM)
        val prk = hmacSha256(salt, ikm)
        val hashLen = 32
        val n = (length + hashLen - 1) / hashLen

        if (n > 255) throw Exception("Output length too large for HKDF (max 8160 bytes)")

        val okm = ByteArray(n * hashLen)
        var t = ByteArray(0)
        // Expand phase
        for (i in 1..n) {
            val input = t + info + byteArrayOf(i.toByte())
            t = hmacSha256(prk, input)
            t.copyInto(okm, (i - 1) * hashLen)
        }
        return okm.copyOf(length)
    }

    actual fun encrypt(key: ByteArray, plaintext: ByteArray, ad: ByteArray): ByteArray {
        if (key.size != 64) throw Exception("Key must be 64 bytes for AES-256 CBC + HMAC-SHA256")

        // Split the key into encryption and MAC keys
        val encKey = key.copyOfRange(0, 32) // AES-256 key
        val macKey = key.copyOfRange(32, 64) // HMAC-SHA256 key

        // Generate a random IV(initialization vector) for AES-CBC
        val iv = ByteArray(16)
        sodium.randomBytesBuf(iv.size).copyInto(iv)

        // Encrypt with AES-256 CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext)

        // Compute HMAC-SHA256 over AD, IV, and ciphertext
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        mac.update(ad)
        mac.update(iv)
        mac.update(ciphertext)
        val hmac = mac.doFinal()

        // Return IV + ciphertext + HMAC
        return iv + ciphertext + hmac
    }

    actual fun decrypt(key: ByteArray, ciphertext: ByteArray, ad: ByteArray): ByteArray {
        if (key.size != 64) throw Exception("Key must be 64 bytes for AES-256 CBC + HMAC-SHA256")
        if (ciphertext.size < 16 + 32) throw Exception("Ciphertext too short (missing IV or HMAC)")

        // Split the key into encryption and MAC keys
        val encKey = key.copyOfRange(0, 32)
        val macKey = key.copyOfRange(32, 64)

        // Extract IV, ciphertext, and HMAC
        val iv = ciphertext.copyOfRange(0, 16)
        val actualCiphertext = ciphertext.copyOfRange(16, ciphertext.size - 32)
        val receivedHmac = ciphertext.copyOfRange(ciphertext.size - 32, ciphertext.size)

        // Verify HMAC
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        mac.update(ad)
        mac.update(iv)
        mac.update(actualCiphertext)
        val computedHmac = mac.doFinal()

        if (!computedHmac.contentEquals(receivedHmac)) throw Exception("HMAC verification failed")

        // Decrypt with AES-256 CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))

        return cipher.doFinal(actualCiphertext)
    }

    actual fun sign(privateKey: ByteArray, data: ByteArray): ByteArray {
        return sodium.cryptoSignDetached(data.toString(), Key.fromBytes(privateKey)).toByteArray()
    }

    actual fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean =
        sodium.cryptoSignVerifyDetached(
            signature.toString(),
            data.toString(),
            Key.fromBytes(publicKey)
        )

}