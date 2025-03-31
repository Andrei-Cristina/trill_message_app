package org.message.trill.encryption.x3dh

data class X3DHResult(
    val sk: ByteArray,
    val ad: ByteArray,
    val ek: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as X3DHResult

        if (!sk.contentEquals(other.sk)) return false
        if (!ad.contentEquals(other.ad)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sk.contentHashCode()
        result = 31 * result + ad.contentHashCode()
        return result
    }
}
