package org.message.trill.encryption.utils

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.utils.Key
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Actual implementation of CryptoUtils for JVM platforms (Android and Desktop).
 * This object provides cryptographic utilities using libsodium via lazysodium-java,
 * supporting operations like key generation, encryption, and signing for the Signal Protocol.
 */
actual object EncryptionUtils {
    private val sodium = LazySodiumJava(SodiumJava())
    private val secureRandom = SecureRandom()
    private val X25519_P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    private val X25519_A = BigInteger.valueOf(486662)
    private val ED25519_P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    private val ED25519_L = BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)
    private val ED25519_D = BigInteger("52036cee2b6ffe738cc740797779e89800700a4d4141d8ab75eb4dca135978a3", 16)
    private val ED25519_BASEPOINT = Ed25519Point(
        BigInteger("216936d3cd6e53fec0a4e231fdd6dc5c692cc7609525a7b2c9562d608f25d51a", 16),
        BigInteger("6666666666666666666666666666666666666666666666666666666666666658", 16),
        BigInteger.ONE,
        BigInteger("67875f0fd78b766566ea4e8e64abe9a66e30e2b0f1b0b456891a80a02162b5e", 16)
    )
    private val ED25519_COFACTOR =
        byteArrayOf(8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    private data class Ed25519Point(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    actual fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val keyPair = sodium.cryptoBoxKeypair()
        println("generateKeyPair: privateKey=${keyPair.secretKey.asBytes.encodeToBase64()}, publicKey=${keyPair.publicKey.asBytes.encodeToBase64()}")

        return keyPair.secretKey.asBytes to keyPair.publicKey.asBytes
    }

    actual fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        println("dh: privateKey=${privateKey.encodeToBase64()}, publicKey=${publicKey.encodeToBase64()}")
        val sharedSecret = ByteArray(32)
        if (!sodium.cryptoScalarMult(sharedSecret, privateKey, publicKey)) {
            println("Diffie-Hellman computation failed")
            throw Exception("Diffie-Hellman computation failed")
        }
        println("dh: sharedSecret=${sharedSecret.encodeToBase64()}")
        return sharedSecret
    }

    actual fun sha256(input: ByteArray): ByteArray {
        println("sha256: input=${input.encodeToBase64()}")
        val hash = sodium.cryptoHashSha256(input.toString()).toByteArray()
        println("sha256: hash=${hash.encodeToBase64()}")

        return hash
    }

//    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
//        println("hmacSha256: key=${key.encodeToBase64()}, data=${data.encodeToBase64()}")
//        val out = ByteArray(32)
//        if (!sodium.cryptoAuthHMACSha256(out, data, data.size.toLong(), key)) {
//            println("HMAC-SHA256 computation failed")
//            throw Exception("HMAC-SHA256 computation failed")
//        }
//        println("hmacSha256: output=${out.encodeToBase64()}")
//        return out
//    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    actual fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        println("hkdf: salt=${salt.encodeToBase64()}, ikm=${ikm.encodeToBase64()}, info=${info.decodeToString()}, length=$length")
        // Extract phase: PRK = HMAC-SHA256(salt, IKM)
        val prk = hmacSha256(salt, ikm)
        println("hkdf: prk=${prk.encodeToBase64()}")
        val hashLen = 32
        val n = (length + hashLen - 1) / hashLen

        if (n > 255) {
            println("Output length too large for HKDF (max 8160 bytes)")
            throw Exception("Output length too large for HKDF (max 8160 bytes)")
        }

        val okm = ByteArray(n * hashLen)
        var t = ByteArray(0)
        // Expand phase
        for (i in 1..n) {
            val input = t + info + byteArrayOf(i.toByte())
            println("hkdf: iteration $i, input=${input.encodeToBase64()}")
            t = hmacSha256(prk, input)
            println("hkdf: t=${t.encodeToBase64()}")
            t.copyInto(okm, (i - 1) * hashLen)
        }
        val result = okm.copyOf(length)
        println("hkdf: output=${result.encodeToBase64()}")
        return result
    }

    actual fun encrypt(key: ByteArray, plaintext: ByteArray, ad: ByteArray): ByteArray {
        println("encrypt: key=${key.encodeToBase64()}, plaintext=${plaintext.decodeToString()}, ad=${ad.encodeToBase64()}")
        if (key.size != 64) {
            println("Key must be 64 bytes for AES-256 CBC + HMAC-SHA256, got ${key.size}")
            throw Exception("Key must be 64 bytes for AES-256 CBC + HMAC-SHA256")
        }

        // Split the key into encryption and MAC keys
        val encKey = key.copyOfRange(0, 32)
        val macKey = key.copyOfRange(32, 64)
        println("encrypt: encKey=${encKey.encodeToBase64()}, macKey=${macKey.encodeToBase64()}")

        // Generate a random IV
        val iv = ByteArray(16)
        sodium.randomBytesBuf(iv.size).copyInto(iv)
        println("encrypt: iv=${iv.encodeToBase64()}")

        // Encrypt with AES-256 CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext)
        println("encrypt: ciphertext=${ciphertext.encodeToBase64()}")

        // Compute HMAC-SHA256 over AD, IV, and ciphertext
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        mac.update(ad)
        mac.update(iv)
        mac.update(ciphertext)
        val hmac = mac.doFinal()
        println("encrypt: hmac=${hmac.encodeToBase64()}")

        // Return IV + ciphertext + HMAC
        val result = iv + ciphertext + hmac
        println("encrypt: output=${result.encodeToBase64()}, size=${result.size}")

        return result
    }

    actual fun decrypt(key: ByteArray, ciphertext: ByteArray, ad: ByteArray): ByteArray {
        println("decrypt: key=${key.encodeToBase64()}, ciphertext=${ciphertext.encodeToBase64()}, ad=${ad.encodeToBase64()}")
        if (key.size != 64) {
            println("Key must be 64 bytes for AES-256 CBC + HMAC-SHA256, got ${key.size}")
            throw Exception("Key must be 64 bytes for AES-256 CBC + HMAC-SHA256")
        }
        if (ciphertext.size < 16 + 32) {
            println("Ciphertext too short (missing IV or HMAC), size=${ciphertext.size}")
            throw Exception("Ciphertext too short (missing IV or HMAC)")
        }

        // Split the key into encryption and MAC keys
        val encKey = key.copyOfRange(0, 32)
        val macKey = key.copyOfRange(32, 64)
        println("decrypt: encKey=${encKey.encodeToBase64()}, macKey=${macKey.encodeToBase64()}")

        // Extract IV, ciphertext, and HMAC
        val iv = ciphertext.copyOfRange(0, 16)
        val actualCiphertext = ciphertext.copyOfRange(16, ciphertext.size - 32)
        val receivedHmac = ciphertext.copyOfRange(ciphertext.size - 32, ciphertext.size)
        println("decrypt: iv=${iv.encodeToBase64()}, actualCiphertext=${actualCiphertext.encodeToBase64()}, receivedHmac=${receivedHmac.encodeToBase64()}")

        // Verify HMAC
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        mac.update(ad)
        mac.update(iv)
        mac.update(actualCiphertext)
        val computedHmac = mac.doFinal()
        println("decrypt: computedHmac=${computedHmac.encodeToBase64()}")

//        if (!computedHmac.contentEquals(receivedHmac)) {
//            println("HMAC verification failed")
//            throw Exception("HMAC verification failed")
//        }

        // Decrypt with AES-256 CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        val plaintext = cipher.doFinal(actualCiphertext)
        println("decrypt: plaintext=${plaintext.decodeToString()}")

        return plaintext
    }

//    actual fun sign(privateKey: ByteArray, data: ByteArray): ByteArray {
//        val signatureBase64 = sodium.cryptoSignDetached(data.encodeToBase64(), Key.fromBytes(privateKey))
//
//        return Base64.getDecoder().decode(signatureBase64)
//    }
//
    actual fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
    println("verify: publicKey=${publicKey.encodeToBase64()}, data=${data.encodeToBase64()}, signature=${signature.encodeToBase64()}")
    try {
        val result = sodium.cryptoSignVerifyDetached(signature, data, data.size, publicKey)
        println("verify: result=$result")
        return result
    } catch (e: Exception) {
        println("Signature verification failed: ${e.message}")
        return false
    }
    }


    actual fun sign(privateKey: ByteArray, data: ByteArray): ByteArray {
        if (privateKey.size != 32) throw Exception("Ed25519 private key must be 32 bytes")

        // Compute SHA-512 of private key to get scalar and prefix
        val h = sha512(privateKey)
        val a = h.copyOfRange(0, 32)
        a[0] = (a[0].toInt() and 248).toByte()
        a[31] = (a[31].toInt() and 127).toByte()
        a[31] = (a[31].toInt() or 64).toByte()

        // Compute r = SHA-512(prefix || message)
        val r = sha512(h.copyOfRange(32, 64) + data)
        val rReduced = scalarReduce(r)
        val rPoint = ed25519ScalarMultiplication(rReduced)
        val encodedR = encodePoint(rPoint)

        // Compute public key A = aB
        val publicKey = ed25519ScalarMultiplication(a)
        val encodedA = encodePoint(publicKey)

        // Compute S = r + SHA-512(R || A || message) * a (mod L)
        val hRAM = sha512(encodedR + encodedA + data)
        val hRAMReduced = scalarReduce(hRAM)
        val s = scalarAdd(rReduced, scalarMultiply(hRAMReduced, a))
        val encodedS = encodeScalar(s)

        return encodedR + encodedS
    }

//    actual fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
//        if (publicKey.size != 32 || signature.size != 64) return false
//
//        val r = signature.copyOfRange(0, 32)
//        val s = signature.copyOfRange(32, 64)
//
//        val sScalar = decodeScalar(s)
//        if (!isScalarValid(sScalar)) return false
//
//        val rPoint = try {
//            decodePoint(r)
//        } catch (e: Exception) {
//            println("Failed to receive messages for ${e.message}")
//
//            return false
//        }
//        val aPoint = try {
//            decodePoint(publicKey)
//        } catch (e: Exception) {
//            println("Failed to receive messages for ${e.message}")
//
//            return false
//        }
//
//        val h = sha512(r + publicKey + data)
//        val hReduced = scalarReduce(h)
//
//        // Verify: [8][S]B = [8][R] + [8][H]A
//        val sb = ed25519ScalarMultiplication(sScalar, ED25519_COFACTOR)
//        val rPlusHa = ed25519PointAdd(
//            ed25519PointScalarMultiplication(rPoint, ByteArray(32), ED25519_COFACTOR),
//            ed25519PointScalarMultiplication(aPoint, hReduced, ED25519_COFACTOR)
//        )
//
//        return ed25519PointEquals(sb, rPlusHa)
//    }

    private fun sha512(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-512")
        return digest.digest(input)
    }

    private fun scalarReduce(scalar: ByteArray): ByteArray {
        val l = ED25519_L
        var s = BigInteger.ZERO
        for (i in 0 until min(scalar.size, 64)) {
            s = s.add(BigInteger.valueOf((scalar[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
        }
        s = s.mod(l)

        val result = ByteArray(32)
        var temp = s
        for (i in 0 until 32) {
            result[i] = (temp and BigInteger.valueOf(255)).toByte()
            temp = temp shr 8
        }
        return result
    }

    private fun scalarAdd(a: ByteArray, b: ByteArray): ByteArray {
        var aBig = BigInteger.ZERO
        var bBig = BigInteger.ZERO
        for (i in 0 until 32) {
            aBig = aBig.add(BigInteger.valueOf((a[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
            bBig = bBig.add(BigInteger.valueOf((b[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
        }
        val sum = (aBig + bBig).mod(ED25519_L)

        val result = ByteArray(32)
        var temp = sum
        for (i in 0 until 32) {
            result[i] = (temp and BigInteger.valueOf(255)).toByte()
            temp = temp shr 8
        }
        return result
    }

    private fun scalarMultiply(a: ByteArray, b: ByteArray): ByteArray {
        var aBig = BigInteger.ZERO
        var bBig = BigInteger.ZERO
        for (i in 0 until 32) {
            aBig = aBig.add(BigInteger.valueOf((a[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
            bBig = bBig.add(BigInteger.valueOf((b[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
        }
        val product = (aBig * bBig).mod(ED25519_L)

        val result = ByteArray(32)
        var temp = product
        for (i in 0 until 32) {
            result[i] = (temp and BigInteger.valueOf(255)).toByte()
            temp = temp shr 8
        }
        return result
    }

    private fun isScalarValid(s: ByteArray): Boolean {
        var sBig = BigInteger.ZERO
        for (i in 0 until 32) {
            sBig = sBig.add(BigInteger.valueOf((s[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
        }
        return sBig < ED25519_L
    }

    private fun ed25519ScalarMultiplication(scalar: ByteArray, cofactor: ByteArray = ByteArray(32)): Ed25519Point {
        var e = BigInteger.ZERO
        for (i in 0 until 32) {
            e = e.add(BigInteger.valueOf((scalar[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
        }

        var h = BigInteger.ONE
        if (cofactor.isNotEmpty()) {
            h = BigInteger.ZERO
            for (i in 0 until min(cofactor.size, 32)) {
                h = h.add(BigInteger.valueOf((cofactor[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
            }
        }
        e *= h

        var q = Ed25519Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
        var p = ED25519_BASEPOINT

        for (i in 0 until e.bitLength()) {
            if (e.testBit(i)) {
                q = ed25519PointAdd(q, p)
            }
            p = ed25519PointDouble(p)
        }
        return q
    }

    private fun ed25519PointScalarMultiplication(
        point: Ed25519Point,
        scalar: ByteArray,
        cofactor: ByteArray
    ): Ed25519Point {
        var e = BigInteger.ZERO
        for (i in 0 until 32) {
            e = e.add(BigInteger.valueOf((scalar[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
        }

        var h = BigInteger.ONE
        if (cofactor.isNotEmpty()) {
            h = BigInteger.ZERO
            for (i in 0 until min(cofactor.size, 32)) {
                h = h.add(BigInteger.valueOf((cofactor[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
            }
        }
        e *= h

        var q = Ed25519Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
        var p = point

        for (i in 0 until e.bitLength()) {
            if (e.testBit(i)) {
                q = ed25519PointAdd(q, p)
            }
            p = ed25519PointDouble(p)
        }
        return q
    }

    private fun ed25519PointAdd(p: Ed25519Point, q: Ed25519Point): Ed25519Point {
        val a = (p.y - p.x) * (q.y - q.x) % ED25519_P
        val b = (p.y + p.x) * (q.y + q.x) % ED25519_P
        val c = BigInteger.TWO * p.t * q.t * ED25519_D % ED25519_P
        val d = BigInteger.TWO * p.z * q.z % ED25519_P
        val e = b - a
        val f = d - c
        val g = d + c
        val h = b + a
        val x3 = e * f % ED25519_P
        val y3 = g * h % ED25519_P
        val t3 = e * h % ED25519_P
        val z3 = f * g % ED25519_P
        return Ed25519Point(x3, y3, z3, t3)
    }

    private fun ed25519PointDouble(p: Ed25519Point): Ed25519Point {
        val a = p.x * p.x % ED25519_P
        val b = p.y * p.y % ED25519_P
        val c = BigInteger.TWO * p.z * p.z % ED25519_P
        val d = (-a) % ED25519_P
        val e = (p.x + p.y) * (p.x + p.y) - a - b % ED25519_P
        val g = d + b
        val f = g - c
        val h = d - b
        val x3 = e * f % ED25519_P
        val y3 = g * h % ED25519_P
        val t3 = e * h % ED25519_P
        val z3 = f * g % ED25519_P
        return Ed25519Point(x3, y3, z3, t3)
    }

    private fun ed25519PointSubtract(p: Ed25519Point, q: Ed25519Point): Ed25519Point {
        val negatedQ = Ed25519Point(q.x, (-q.y).mod(ED25519_P), q.z, (-q.t).mod(ED25519_P))
        return ed25519PointAdd(p, negatedQ)
    }

    private fun ed25519PointEquals(p: Ed25519Point, q: Ed25519Point): Boolean {
        val x1z2 = p.x * q.z % ED25519_P
        val x2z1 = q.x * p.z % ED25519_P
        val y1z2 = p.y * q.z % ED25519_P
        val y2z1 = q.y * p.z % ED25519_P
        return x1z2 == x2z1 && y1z2 == y2z1
    }

    private fun encodePoint(point: Ed25519Point): ByteArray {
        val zInv = point.z.modInverse(ED25519_P)
        val x = (point.x * zInv).mod(ED25519_P)
        val y = (point.y * zInv).mod(ED25519_P)

        val yBytes = ByteArray(32)
        var temp = y
        for (i in 0 until 32) {
            yBytes[i] = (temp and BigInteger.valueOf(255)).toByte()
            temp = temp shr 8
        }

        if (x.testBit(0)) {
            yBytes[31] = (yBytes[31].toInt() or 0x80).toByte()
        }
        return yBytes
    }

    private fun decodePoint(encoded: ByteArray): Ed25519Point {
        if (encoded.size != 32) throw Exception("Invalid point encoding")

        val yBytes = encoded.copyOf()
        val sign = (yBytes[31].toInt() shr 7) and 1
        yBytes[31] = (yBytes[31].toInt() and 0x7f).toByte()

        var y = BigInteger.ZERO
        for (i in 0 until 32) {
            y = y.add(BigInteger.valueOf((yBytes[i].toInt() and 0xff).toLong()).shiftLeft(8 * i))
        }

        // Compute x from y^2 = x^2 + ax + b
        val y2 = y * y % ED25519_P
        val u = (y2 - BigInteger.ONE) % ED25519_P
        val v = (ED25519_D * y2 + BigInteger.ONE) % ED25519_P
        var x = sqrtMod(u * v.modInverse(ED25519_P), ED25519_P)

        if (x == BigInteger.ZERO && u != BigInteger.ZERO) {
            throw Exception("Invalid point: not on curve")
        }

        if (x.testBit(0) != (sign == 1)) {
            x = (-x).mod(ED25519_P)
        }

        return Ed25519Point(x, y, BigInteger.ONE, x * y % ED25519_P)
    }

    private fun encodeScalar(scalar: ByteArray): ByteArray {
        return scalar.copyOf(32)
    }

    private fun decodeScalar(encoded: ByteArray): ByteArray {
        if (encoded.size != 32) throw Exception("Invalid scalar encoding")
        return encoded.copyOf(32)
    }

    private fun sqrtMod(a: BigInteger, p: BigInteger): BigInteger {
        if (a == BigInteger.ZERO) return BigInteger.ZERO

        // For p = 2^255 - 19, use Tonelli-Shanks with specific exponent
        val q = (p - BigInteger.ONE) / BigInteger.TWO
        var z = BigInteger.ZERO
        for (i in 2 until 100) {
            z = BigInteger.valueOf(i.toLong())
            if (z.modPow(q, p) != BigInteger.ONE) break
        }

        var m = q
        var c = z.modPow(m, p)
        var t = a.modPow(m, p)
        var r = a.modPow((m + BigInteger.ONE) / BigInteger.TWO, p)

        while (t != BigInteger.ONE) {
            var i = 0
            var temp = t
            while (temp != BigInteger.ONE) {
                temp = temp * temp % p
                i++
            }
            val b = c.modPow(BigInteger.ONE.shiftLeft(m.toInt() - i - 1), p)
            r = r * b % p
            c = b * b % p
            t = t * c % p
            m = BigInteger.valueOf(i.toLong())
        }

        return r
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
}