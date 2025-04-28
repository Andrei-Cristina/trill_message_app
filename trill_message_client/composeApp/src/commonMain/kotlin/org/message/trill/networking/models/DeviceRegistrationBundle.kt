package org.message.trill.networking.models

import kotlinx.serialization.Serializable
import org.message.trill.encryption.utils.ByteArrayListSerializer
import org.message.trill.encryption.utils.ByteArraySerializer

@Serializable
data class DeviceRegistrationBundle(
    val userEmail: String,

    @Serializable(with = ByteArraySerializer::class)
    val identityKey: ByteArray,

    @Serializable(with = ByteArraySerializer::class)
    val signedPreKey: ByteArray,

    @Serializable(with = ByteArraySerializer::class)
    val preKeySignature: ByteArray,

    @Serializable(with = ByteArrayListSerializer::class)
    val onetimePreKeys: List<ByteArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeviceRegistrationBundle

        if (userEmail != other.userEmail) return false
        if (!identityKey.contentEquals(other.identityKey)) return false
        if (!signedPreKey.contentEquals(other.signedPreKey)) return false
        if (!preKeySignature.contentEquals(other.preKeySignature)) return false
        if (onetimePreKeys != other.onetimePreKeys) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userEmail.hashCode()
        result = 31 * result + identityKey.contentHashCode()
        result = 31 * result + signedPreKey.contentHashCode()
        result = 31 * result + preKeySignature.contentHashCode()
        result = 31 * result + onetimePreKeys.hashCode()
        return result
    }
}
