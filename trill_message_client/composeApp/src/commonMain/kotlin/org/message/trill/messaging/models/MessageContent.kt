package org.message.trill.messaging.models

import kotlinx.serialization.Serializable


//@Serializable
//sealed class MessageContent {
//    abstract val ciphertext: ByteArray
//
//    @Serializable
//    data class InitialMessage(
//        val senderIdentityKey: ByteArray,
//        val ephemeralKey: ByteArray,
//        override val ciphertext: ByteArray
//    ) : MessageContent()
//
//    @Serializable
//    data class RegularMessage(
//        val header: Header,
//        override val ciphertext: ByteArray
//    ) : MessageContent()
//}

@Serializable
data class MessageContent(
    val header: Header,
    val ciphertext: ByteArray
) {
    companion object {
        fun fromByteArray(bytes: ByteArray): MessageContent {
            val dh = bytes.copyOfRange(0, 32)
            val pn = bytes.copyOfRange(32, 64).toString().toInt()
            val n = bytes.copyOfRange(64, 96).toString().toInt()
            val ciphertext = bytes.copyOfRange(96, bytes.size)
            return MessageContent(Header(dh, pn, n), ciphertext)
        }
    }

    fun toByteArray(): ByteArray = header.toByteArray() + ciphertext

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


