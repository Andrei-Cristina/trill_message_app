package com.trill.message.data.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceKeyBundle(
    val identityKey: String,
    val signedPreKey: String,
    val preKeySignature: String,
    val onetimePreKey: String
)
