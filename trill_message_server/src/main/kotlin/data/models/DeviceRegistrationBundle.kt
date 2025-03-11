package com.trill.message.data.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistrationBundle(
    val userEmail: String,
    val identityKey: String,
    val signedPreKey: String,
    val preKeySignature: String,
    val onetimePreKeys: List<String>
)
