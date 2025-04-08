package org.message.trill.encryption.double_ratchet

import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.x3dh.X3DHResult
import org.message.trill.encryption.utils.EncryptionUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Data class representing the state of the Double Ratchet algorithm.
 * This state is maintained by both parties in a secure communication session.
 */
data class RatchetState(
    /**
     * Diffie-Hellman key pair for the sending chain (dhs).
     * The first element is the private key, and the second is the public key.
     */
    var dhs: Pair<ByteArray, ByteArray>,

    /**
     * Diffie-Hellman public key of the received message's sender (dhr).
     * This is used for ratcheting forward when a new public key is received.
     */
    var dhr: ByteArray?,

    /**
     * Root key (rk) used for deriving new chain keys, initially SK from X3DH.
     */
    var rk: ByteArray,

    /**
     * Chain key for sending messages (cks).
     * This is used to derive message keys for encryption.
     */
    var cks: ByteArray?,

    /**
     * Chain key for receiving messages (ckr).
     * This is used to derive message keys for decryption.
     */
    var ckr: ByteArray?,

    /**
     * Message number for sent messages (ns).
     */
    var ns: Int = 0,

    /**
     * Message number for received messages (nr).
     */
    var nr: Int = 0,

    /**
     * Previous chain length (pn), indicating the number of messages sent before a key change.
     */
    var pn: Int = 0,

    /**
     * associated data from the X3DH agreement
     */
    val ad: ByteArray,

    /**
     * Skipped message keys for handling out-of-order messages.
     * Maps message identifiers to the corresponding message keys.
     */
    val skipped: MutableMap<String, ByteArray> = mutableMapOf()
) {
    companion object {
        /**
         * The maximum number of skipped message keys to store before discarding old ones.
         */
        const val MAX_SKIP = 1000

        fun initAsSender(result: X3DHResult, receiverPublicKey: ByteArray):RatchetState {
            val dhs = EncryptionUtils.generateKeyPair()
            val dh_out = EncryptionUtils.dh(dhs.first, receiverPublicKey)
            val hkdf_result = EncryptionUtils.hkdf(salt = result.sk, ikm=dh_out, info = "TRILL".toByteArray(), length = 64)

            return RatchetState(
                dhs = dhs,
                dhr = receiverPublicKey,
                rk = hkdf_result.copyOfRange(0, 32),
                cks = hkdf_result.copyOfRange(32, 64),
                ckr = null,
                ad = result.ad
            )
        }

        fun initAsReceiver(result: X3DHResult, receiverKeyPair: PreKey): RatchetState = RatchetState(
            dhs = receiverKeyPair.privateKey to receiverKeyPair.publicKey,
            dhr = null,
            rk = result.sk,
            cks = null,
            ckr = null,
            ad = result.ad
        )

        fun fromByteArray(data: ByteArray): RatchetState {
            val byteStream = ByteArrayInputStream(data)
            ObjectInputStream(byteStream).use { ois ->
                val dhsFirst = ois.readObject() as ByteArray
                val dhsSecond = ois.readObject() as ByteArray
                val dhr = ois.readObject() as ByteArray?
                val rk = ois.readObject() as ByteArray
                val cks = ois.readObject() as ByteArray?
                val ckr = ois.readObject() as ByteArray?
                val ns = ois.readInt()
                val nr = ois.readInt()
                val pn = ois.readInt()
                val ad = ois.readObject() as ByteArray
                val skipped = ois.readObject() as MutableMap<String, ByteArray>

                return RatchetState(
                    dhs = Pair(dhsFirst, dhsSecond),
                    dhr = dhr,
                    rk = rk,
                    cks = cks,
                    ckr = ckr,
                    ns = ns,
                    nr = nr,
                    pn = pn,
                    ad = ad,
                    skipped = skipped
                )
            }
        }
    }

    fun toByteArray(): ByteArray {
        val byteStream = ByteArrayOutputStream()
        ObjectOutputStream(byteStream).use { oos ->
            oos.writeObject(dhs.first)
            oos.writeObject(dhs.second)
            oos.writeObject(dhr)
            oos.writeObject(rk)
            oos.writeObject(cks)
            oos.writeObject(ckr)
            oos.writeInt(ns)
            oos.writeInt(nr)
            oos.writeInt(pn)
            oos.writeObject(ad)
            oos.writeObject(skipped)
        }
        return byteStream.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RatchetState

        if (dhs != other.dhs) return false

        if (dhr != null) {
            if (other.dhr == null) return false
            if (!dhr.contentEquals(other.dhr)) return false
        } else if (other.dhr != null) return false

        if (!rk.contentEquals(other.rk)) return false

        if (cks != null) {
            if (other.cks == null) return false
            if (!cks.contentEquals(other.cks)) return false
        } else if (other.cks != null) return false

        if (ckr != null) {
            if (other.ckr == null) return false
            if (!ckr.contentEquals(other.ckr)) return false
        } else if (other.ckr != null) return false

        if (ns != other.ns) return false
        if (nr != other.nr) return false
        if (pn != other.pn) return false
        if (skipped != other.skipped) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dhs.hashCode()

        result = 31 * result + (dhr?.contentHashCode() ?: 0)
        result = 31 * result + rk.contentHashCode()
        result = 31 * result + (cks?.contentHashCode() ?: 0)
        result = 31 * result + (ckr?.contentHashCode() ?: 0)
        result = 31 * result + ns
        result = 31 * result + nr
        result = 31 * result + pn
        result = 31 * result + skipped.hashCode()

        return result
    }
}
