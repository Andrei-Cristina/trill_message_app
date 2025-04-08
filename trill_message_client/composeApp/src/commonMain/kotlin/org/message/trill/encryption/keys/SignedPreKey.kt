package org.message.trill.encryption.keys

import org.message.trill.encryption.utils.EncryptionUtils
import kotlinx.serialization.Serializable
import org.message.trill.encryption.utils.ByteArraySerializer

@Serializable
data class SignedPreKey(
    val preKey: PreKey,

    @Serializable(with = ByteArraySerializer::class)
    val signature: ByteArray
) {
    companion object{
        fun generate(): SignedPreKey {
            val preKey = PreKey.generate(0)
            val signature = EncryptionUtils.sign(privateKey = preKey.privateKey, data = preKey.publicKey)

            return SignedPreKey(preKey = preKey, signature = signature)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedPreKey

        if (preKey != other.preKey) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = preKey.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
