package data.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceKeyBundle(
    val identityKey: String,
    val signedPreKey: String,
    val signature: String,
    val onetimePreKey: String
)
