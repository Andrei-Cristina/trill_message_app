package org.message.trill.encryption.keys
import kotlinx.serialization.Serializable
import org.message.trill.encryption.utils.ByteArraySerializer

@Serializable
data class PrekeyBundle(
    @Serializable(with = ByteArraySerializer::class)
    val identityKey: ByteArray,

    @Serializable(with = ByteArraySerializer::class)
    val signedPreKey: ByteArray,

    @Serializable(with = ByteArraySerializer::class)
    val signature: ByteArray,

    @Serializable(with = ByteArraySerializer::class)
    val oneTimePreKey: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PrekeyBundle

        if (!identityKey.contentEquals(other.identityKey)) return false
        if (!signedPreKey.contentEquals(other.signedPreKey)) return false
        if (oneTimePreKey != null) {
            if (other.oneTimePreKey == null) return false
            if (!oneTimePreKey.contentEquals(other.oneTimePreKey)) return false
        } else if (other.oneTimePreKey != null) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = identityKey.contentHashCode()
        result = 31 * result + signedPreKey.contentHashCode()
        result = 31 * result + (oneTimePreKey?.contentHashCode() ?: 0)
        result = 31 * result + signature.contentHashCode()
        return result
    }
}