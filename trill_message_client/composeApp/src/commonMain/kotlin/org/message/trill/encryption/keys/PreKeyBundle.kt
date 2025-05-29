package org.message.trill.encryption.keys
import kotlinx.serialization.Serializable
import org.message.trill.encryption.utils.ByteArraySerializer

@Serializable
data class PrekeyBundle(
    val identityKey: String,

    val signedPreKey: String,

    val signature: String,

    val oneTimePreKey: String
) {
}