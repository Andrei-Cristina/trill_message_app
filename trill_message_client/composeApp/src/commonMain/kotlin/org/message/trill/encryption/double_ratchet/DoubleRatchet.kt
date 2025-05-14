package org.message.trill.encryption.double_ratchet

import org.message.trill.messaging.models.MessageContent
import org.message.trill.messaging.models.Header
import org.message.trill.encryption.utils.EncryptionUtils
import java.util.*


class DoubleRatchet(
    private val state: RatchetState
) {
    fun encrypt(plaintext: ByteArray, ad: ByteArray): Pair<Header, ByteArray> {
        println("DoubleRatchet.encrypt: plaintext=${plaintext.decodeToString()}, ad=${ad.encodeToBase64()}, cks=${state.cks?.encodeToBase64() ?: "null"}")
        val (ck, mk) = kdfCk(state.cks!!)
        println("DoubleRatchet.encrypt: new chainKey=${ck.encodeToBase64()}, messageKey=${mk.encodeToBase64()}")
        state.cks = ck

        val header = Header(state.dhs.second, state.pn, state.ns)
        println("DoubleRatchet.encrypt: header.dh=${header.dh.encodeToBase64()}, header.pn=${header.pn}, header.n=${header.n}")
        state.ns++

        val ciphertext = EncryptionUtils.encrypt(mk, plaintext, ad + header.toByteArray())
        println("DoubleRatchet.encrypt: ciphertext=${ciphertext.encodeToBase64()}")
        return header to ciphertext
    }

    fun decrypt(message: MessageContent, ad: ByteArray): ByteArray {
        println("DoubleRatchet.decrypt: header.dh=${message.header.dh.encodeToBase64()}, header.n=${message.header.n}, header.pn=${message.header.pn}, ciphertext=${message.ciphertext.encodeToBase64()}, ad=${ad.encodeToBase64()}, ckr=${state.ckr?.encodeToBase64() ?: "null"}, dhr=${state.dhr?.encodeToBase64() ?: "null"}")

        val skippedKey = trySkippedKeys(message, ad)
        if (skippedKey != null) {
            println("DoubleRatchet.decrypt: Using skipped key for ${message.header.dh.toHex()}_${message.header.n}")
            return skippedKey
        }

        if (!message.header.dh.contentEquals(state.dhr)) {
            println("DoubleRatchet.decrypt: Performing DH ratchet step due to new header.dh")
            skipMessageKeys(message.header.pn)
            dhRatchetStep(message.header)
        }

        skipMessageKeys(message.header.n)
        val (ck, mk) = kdfCk(state.ckr!!)
        println("DoubleRatchet.decrypt: new chainKey=${ck.encodeToBase64()}, messageKey=${mk.encodeToBase64()}")
        state.ckr = ck
        state.nr++

        val plaintext = EncryptionUtils.decrypt(mk, message.ciphertext, ad + message.header.toByteArray())
        println("DoubleRatchet.decrypt: plaintext=${plaintext.decodeToString()}")
        return plaintext
    }

    private fun kdfRk(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        println("kdfRk: rk=${rk.encodeToBase64()}, dhOut=${dhOut.encodeToBase64()}")
        val output = EncryptionUtils.hkdf(rk, dhOut, "TRILL".toByteArray(), 64)
        println("kdfRk: output=${output.encodeToBase64()}, rk=${output.copyOfRange(0, 32).encodeToBase64()}, ckr/cks=${output.copyOfRange(32, 64).encodeToBase64()}")
        return output.copyOfRange(0, 32) to output.copyOfRange(32, 64)
    }

    private fun kdfCk(ck: ByteArray): Pair<ByteArray, ByteArray> {
        println("kdfCk: ck=${ck.encodeToBase64()}")
        val output = EncryptionUtils.hkdf(ck, "CK".toByteArray(), "TRILL_CK".toByteArray(), 96)
        println("kdfCk: output=${output.encodeToBase64()}, chainKey=${output.copyOfRange(0, 32).encodeToBase64()}, messageKey=${output.copyOfRange(32, 96).encodeToBase64()}")
        return output.copyOfRange(0, 32) to output.copyOfRange(32, 96)
    }

    private fun trySkippedKeys(message: MessageContent, ad: ByteArray): ByteArray? {
        val key = "${message.header.dh.toHex()}_${message.header.n}"
        println("trySkippedKeys: checking for key=$key")
        return state.skipped[key]?.let { mk ->
            println("trySkippedKeys: found skipped messageKey=${mk.encodeToBase64()}")
            state.skipped.remove(key)
            EncryptionUtils.decrypt(mk, message.ciphertext, ad + message.header.toByteArray())
        }
    }

    private fun skipMessageKeys(until: Int) {
        println("skipMessageKeys: until=$until, current nr=${state.nr}")
        if (state.nr + RatchetState.MAX_SKIP < until) {
            println("Too many skipped messages: nr=${state.nr}, until=$until")
            throw Exception("Too many skipped messages")
        }

        while (state.ckr != null && state.nr < until) {
            val (ck, mk) = kdfCk(state.ckr!!)
            println("skipMessageKeys: storing skipped key for ${state.dhr!!.toHex()}_${state.nr}, messageKey=${mk.encodeToBase64()}")
            state.skipped["${state.dhr!!.toHex()}_${state.nr}"] = mk
            state.ckr = ck
            state.nr++
        }
    }

    private fun dhRatchetStep(header: Header) {
        println("dhRatchetStep: header.dh=${header.dh.encodeToBase64()}, header.pn=${header.pn}, header.n=${header.n}")
        state.pn = state.ns
        state.ns = 0
        state.nr = 0
        state.dhr = header.dh

        val rk = kdfRk(state.rk, EncryptionUtils.dh(state.dhs.first, state.dhr!!))
        state.rk = rk.first
        state.ckr = rk.second
        println("dhRatchetStep: new rk=${state.rk.encodeToBase64()}, ckr=${state.ckr?.encodeToBase64() ?: "null"}")

        val newDhs = EncryptionUtils.generateKeyPair()
        val rk2 = kdfRk(state.rk, EncryptionUtils.dh(newDhs.first, state.dhr!!))
        state.rk = rk2.first
        state.cks = rk2.second
        state.dhs = newDhs
        println("dhRatchetStep: new dhs.public=${state.dhs.second.encodeToBase64()}, rk=${state.rk.encodeToBase64()}, cks=${state.cks?.encodeToBase64() ?: "null"}")
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun Header.toByteArray(): ByteArray = dh + pn.toByteArray() + n.toByteArray()
    private fun Int.toByteArray(): ByteArray = ByteArray(4) { (this shr (24 - it * 8)).toByte() }
}