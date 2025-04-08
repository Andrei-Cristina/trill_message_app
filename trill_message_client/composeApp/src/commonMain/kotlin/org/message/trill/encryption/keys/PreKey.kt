package org.message.trill.encryption.keys

import org.message.trill.encryption.utils.EncryptionUtils
import kotlinx.serialization.Serializable
import org.message.trill.encryption.utils.ByteArraySerializer

@Serializable
data class PreKey(
    val id: Int,

    @Serializable(with = ByteArraySerializer::class)
    val publicKey: ByteArray,

    @Serializable(with = ByteArraySerializer::class)
    val privateKey: ByteArray
) {
    companion object {
        fun generate(id: Int): PreKey {
            val (privateKey, publicKey) = EncryptionUtils.generateKeyPair()
            return PreKey(id, publicKey, privateKey)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PreKey

        if (id != other.id) return false
        if (!publicKey.contentEquals(other.publicKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}


