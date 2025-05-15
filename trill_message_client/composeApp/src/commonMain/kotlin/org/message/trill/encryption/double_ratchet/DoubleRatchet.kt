package org.message.trill.encryption.double_ratchet

import org.message.trill.messaging.models.MessageContent
import org.message.trill.messaging.models.Header
import org.message.trill.encryption.utils.EncryptionUtils
import java.util.*


class DoubleRatchet(
    private val state: RatchetState
) {
    fun encrypt(plaintext: ByteArray, ad: ByteArray): Pair<Header, ByteArray> {
        println("DoubleRatchet.encrypt: plaintext=${plaintext.decodeToString()}, ad=${ad.encodeToBase64()}, cks=${state.cks?.encodeToBase64() ?: "null"}, ns=${state.ns}, nr=${state.nr}, dhr=${state.dhr?.encodeToBase64() ?: "null"}")

        if (state.dhr != null && !state.dhr.contentEquals(state.lastDhr) && state.ns == 0) {
            println("DoubleRatchet.encrypt: Performing DH ratchet step due to new dhr")
            senderDhRatchetStep()
            state.lastDhr = state.dhr
            println("DoubleRatchet.encrypt: After DH ratchet - dhs.public=${state.dhs.second.encodeToBase64()}, cks=${state.cks?.encodeToBase64() ?: "null"}")
        }

        val (ck, mk) = kdfCk(state.cks ?: throw IllegalStateException("Sending chain key is null"))
        println("DoubleRatchet.encrypt: new chainKey=${ck.encodeToBase64()}, messageKey=${mk.encodeToBase64()}")
        state.cks = ck

        val header = Header(state.dhs.second, state.pn, state.ns)
        println("DoubleRatchet.encrypt: header.dh=${header.dh.encodeToBase64()}, header.pn=${header.pn}, header.n=${header.n}")
        state.ns++

        val fullAd = ad + header.toByteArray()
        println("DoubleRatchet.encrypt: full associated data=${fullAd.encodeToBase64()}")

        val ciphertext = EncryptionUtils.encrypt(mk, plaintext, fullAd)
        println("DoubleRatchet.encrypt: ciphertext=${ciphertext.encodeToBase64()}")

        return header to ciphertext
    }


    fun decrypt(message: MessageContent, ad: ByteArray): ByteArray {
        println("DoubleRatchet.decrypt: header.dh=${message.header.dh.encodeToBase64()}, header.n=${message.header.n}, header.pn=${message.header.pn}, ciphertext=${message.ciphertext.encodeToBase64()}, ad=${ad.encodeToBase64()}, ckr=${state.ckr?.encodeToBase64() ?: "null"}, dhr=${state.dhr?.encodeToBase64() ?: "null"}, ns=${state.ns}, nr=${state.nr}, pn=${state.pn}")

        val skippedKey = trySkippedKeys(message, ad)
        if (skippedKey != null) {
            println("DoubleRatchet.decrypt: Using skipped key for ${message.header.dh.toHex()}_${message.header.n}")
            return skippedKey
        }

        if (!message.header.dh.contentEquals(state.dhr)) {
            println("DoubleRatchet.decrypt: Performing DH ratchet step due to new header.dh")
            skipMessageKeys(message.header.pn)
            dhRatchetStep(message.header)
            println("DoubleRatchet.decrypt: After DH ratchet - dhs.public=${state.dhs.second.encodeToBase64()}, dhr=${state.dhr?.encodeToBase64() ?: "null"}, rk=${state.rk.encodeToBase64()}, cks=${state.cks?.encodeToBase64() ?: "null"}, ckr=${state.ckr?.encodeToBase64() ?: "null"}")
        }

        skipMessageKeys(message.header.n)
        val (ck, mk) = kdfCk(state.ckr!!)
        println("DoubleRatchet.decrypt: new chainKey=${ck.encodeToBase64()}, messageKey=${mk.encodeToBase64()}")
        state.ckr = ck
        state.nr++

        val fullAd = ad + message.header.toByteArray()
        println("DoubleRatchet.decrypt: full associated data=${fullAd.encodeToBase64()}")

        val plaintext = EncryptionUtils.decrypt(mk, message.ciphertext, fullAd)
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

    private fun senderDhRatchetStep() {
        println("senderDhRatchetStep: current dhr=${state.dhr?.encodeToBase64() ?: "null"}, cks=${state.cks?.encodeToBase64() ?: "null"}")
        val newDhs = EncryptionUtils.generateKeyPair()
        println("senderDhRatchetStep: new dhs.public=${newDhs.second.encodeToBase64()}")
        val dhSend = EncryptionUtils.dh(newDhs.first, state.dhr!!)
        println("senderDhRatchetStep: dhSend=${dhSend.encodeToBase64()}")
        val (newRk, newCks) = kdfRk(state.rk, dhSend)
        println("senderDhRatchetStep: new rk=${newRk.encodeToBase64()}, new cks=${newCks.encodeToBase64()}")
        state.rk = newRk
        state.cks = newCks
        state.dhs = newDhs
    }

    private fun dhRatchetStep(header: Header) {
        println("dhRatchetStep: header.dh=${header.dh.encodeToBase64()}, header.pn=${header.pn}, header.n=${header.n}")
        state.pn = state.ns
        state.ns = 0
        state.nr = 0
        state.dhr = header.dh
        println("dhRatchetStep: Updated state.dhr to ${state.dhr?.encodeToBase64() ?: "null"}")

        val dhRecvOutput = EncryptionUtils.dh(state.dhs.first, state.dhr!!)
        val (newRkAfterRecv, newCkr) = kdfRk(state.rk, dhRecvOutput)
        state.rk = newRkAfterRecv
        state.ckr = newCkr
        println("dhRatchetStep: After CKr derivation - new rk=${state.rk.encodeToBase64()}, ckr=${state.ckr?.encodeToBase64() ?: "null"}")

        val newLocalDhs = EncryptionUtils.generateKeyPair()
        val dhSendOutput = EncryptionUtils.dh(newLocalDhs.first, state.dhr!!)
        val (newRkAfterSendSetup, newCks) = kdfRk(state.rk, dhSendOutput)
        state.rk = newRkAfterSendSetup
        state.cks = newCks
        state.dhs = newLocalDhs
        println("dhRatchetStep: After CKs derivation - new dhs.public=${state.dhs.second.encodeToBase64()}, rk=${state.rk.encodeToBase64()}, cks=${state.cks?.encodeToBase64() ?: "null"}")

        state.lastDhr = state.dhr
        println("dhRatchetStep: Updated lastDhr to ${state.lastDhr?.encodeToBase64() ?: "null"}")
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun Header.toByteArray(): ByteArray = dh + pn.toByteArray() + n.toByteArray()
    private fun Int.toByteArray(): ByteArray = ByteArray(4) { (this shr (24 - it * 8)).toByte() }
}