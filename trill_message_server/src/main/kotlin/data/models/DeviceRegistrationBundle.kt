package data.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationBundle(
    val userEmail: String,
    val identityKey: ByteArray,
    val signedPreKey: ByteArray,
    val preKeySignature: ByteArray,
    val onetimePreKeys: List<ByteArray>
)
