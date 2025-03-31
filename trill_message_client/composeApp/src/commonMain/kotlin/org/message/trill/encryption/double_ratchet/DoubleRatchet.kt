package org.message.trill.encryption.double_ratchet

import org.message.trill.messaging.models.MessageContent
import org.message.trill.messaging.models.Header
import org.message.trill.encryption.utils.EncryptionUtils



class DoubleRatchet(private val state: RatchetState) {
    fun encrypt(plaintext: ByteArray, ad: ByteArray): MessageContent {
        val (ck, mk) = kdfCk(state.cks!!)
        state.cks = ck

        val header = Header(state.dhs.second, state.pn, state.ns)
        state.ns++

        val ciphertext = EncryptionUtils.encrypt(mk, plaintext, ad + header.toByteArray())

        return MessageContent(header, ciphertext)
    }

    fun decrypt(message: MessageContent, ad: ByteArray): ByteArray {
        val skippedKey = trySkippedKeys(message, ad)

        if (skippedKey != null) return skippedKey

        if (!message.header.dh.contentEquals(state.dhr)) {
            skipMessageKeys(message.header.pn)
            dhRatchetStep(message.header)
        }

        skipMessageKeys(message.header.n)

        val (ck, mk) = kdfCk(state.ckr!!)
        state.ckr = ck
        state.nr++

        return EncryptionUtils.decrypt(mk, message.ciphertext, ad + message.header.toByteArray())
    }

    private fun kdfRk(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        val output = EncryptionUtils.hkdf(rk, dhOut, "TRILL".toByteArray(), 64)

        return output.copyOfRange(0, 32) to output.copyOfRange(32, 64)
    }

    private fun kdfCk(ck: ByteArray): Pair<ByteArray, ByteArray> {
        val output = EncryptionUtils.hkdf(ck, "CK".toByteArray(), "TRILL".toByteArray(), 64)

        return output.copyOfRange(0, 32) to output.copyOfRange(32, 64)
    }

    private fun trySkippedKeys(message: MessageContent, ad: ByteArray): ByteArray? {
        val key = "${message.header.dh.toHex()}_${message.header.n}"

        return state.skipped[key]?.let { mk ->
            state.skipped.remove(key)
            EncryptionUtils.decrypt(mk, message.ciphertext, ad + message.header.toByteArray())
        }
    }

    private fun skipMessageKeys(until: Int) {
        if (state.nr + RatchetState.MAX_SKIP < until) throw Exception("Too many skipped messages")

        while (state.ckr != null && state.nr < until) {
            val (ck, mk) = kdfCk(state.ckr!!)

            state.skipped["${state.dhr!!.toHex()}_${state.nr}"] = mk
            state.ckr = ck
            state.nr++
        }
    }

    private fun dhRatchetStep(header: Header) {
        state.pn = state.ns
        state.ns = 0
        state.nr = 0
        state.dhr = header.dh

        var rk = kdfRk(state.rk, EncryptionUtils.dh(state.dhs.first, state.dhr!!))
        state.rk = rk.first
        state.ckr = rk.second

        val newDhs = EncryptionUtils.generateKeyPair()

        rk = kdfRk(state.rk, EncryptionUtils.dh(newDhs.first, state.dhr!!))
        state.rk = rk.first
        state.cks = rk.second
        state.dhs = newDhs
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun Header.toByteArray(): ByteArray = dh + pn.toByteArray() + n.toByteArray()
    private fun Int.toByteArray(): ByteArray = ByteArray(4) { (this shr (24 - it * 8)).toByte() }
}