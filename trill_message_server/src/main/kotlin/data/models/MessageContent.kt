package data.models

import kotlinx.serialization.Serializable

@Serializable
data class MessageContent(
    val header: Header,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageContent

        if (header != other.header) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}
