package com.trill.message.data.models

import kotlinx.serialization.Serializable

@Serializable
data class DeviceKeyBundle(
    val identityKey: ByteArray,
    val signedPreKey: ByteArray,
    val signature: ByteArray,
    val onetimePreKey: ByteArray
)
