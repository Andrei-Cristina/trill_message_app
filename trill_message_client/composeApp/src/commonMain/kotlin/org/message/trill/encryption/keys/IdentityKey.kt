package org.message.trill.encryption.keys

import org.message.trill.encryption.utils.EncryptionUtils
import kotlinx.serialization.Serializable
import org.message.trill.encryption.utils.ByteArraySerializer

@Serializable
data class IdentityKey(
    @Serializable(with = ByteArraySerializer::class)
    val publicKey: ByteArray,

    @Serializable(with = ByteArraySerializer::class)
    val privateKey: ByteArray
) {
    companion object {
        fun generate(): IdentityKey {
            val (privateKey, publicKey) = EncryptionUtils.generateKeyPair()
            return IdentityKey(publicKey, privateKey)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdentityKey

        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!privateKey.contentEquals(other.privateKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}
