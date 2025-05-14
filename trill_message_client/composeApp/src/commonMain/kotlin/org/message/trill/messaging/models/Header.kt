package org.message.trill.messaging.models

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Header(
    val dh: ByteArray,
    val pn: Int,
    val n: Int,
    var ek: ByteArray = byteArrayOf(),
) {
    fun toByteArray(): ByteArray {
        val pnBytes = ByteArray(4) { (pn shr (24 - it * 8)).toByte() }
        val nBytes = ByteArray(4) { (n shr (24 - it * 8)).toByte() }
        val result = dh + pnBytes + nBytes + ek
        println("Header.toByteArray: dh=${dh.encodeToBase64()}, pnBytes=${pnBytes.encodeToBase64()}, nBytes=${nBytes.encodeToBase64()}, ek=${ek.encodeToBase64()}, result=${result.encodeToBase64()}")
        return result
    }

    private fun Int.toByteArray(): ByteArray = ByteArray(32) { (this shr (24 - it * 8)).toByte() }
    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Header

        if (!dh.contentEquals(other.dh)) return false
        if (pn != other.pn) return false
        if (n != other.n) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dh.contentHashCode()
        result = 31 * result + pn
        result = 31 * result + n
        return result
    }
}

