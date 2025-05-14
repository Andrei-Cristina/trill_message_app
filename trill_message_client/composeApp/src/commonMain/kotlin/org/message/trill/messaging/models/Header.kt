package org.message.trill.messaging.models

import kotlinx.serialization.Serializable

@Serializable
data class Header(
    val dh: ByteArray,
    val pn: Int,
    val n: Int,
    var ek: ByteArray = byteArrayOf(),
) {
    fun toByteArray(): ByteArray = dh + pn.toByteArray() + n.toByteArray() + ek

    private fun Int.toByteArray(): ByteArray = ByteArray(32) { (this shr (24 - it * 8)).toByte() }

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

