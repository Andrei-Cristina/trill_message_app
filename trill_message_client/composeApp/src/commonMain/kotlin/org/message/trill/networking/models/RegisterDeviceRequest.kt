package org.message.trill.networking.models

import kotlinx.serialization.Serializable
import org.message.trill.encryption.utils.ByteArrayListSerializer
import org.message.trill.encryption.utils.ByteArraySerializer

@Serializable
data class RegisterDeviceRequest(
    val userEmail: String,

    @Serializable(with = ByteArraySerializer::class)
    val identityKey: ByteArray,

    @Serializable(with = ByteArraySerializer::class)
    val signedPreKey: ByteArray,

    @Serializable(with = ByteArraySerializer::class)
    val preKeySignature: ByteArray,

    @Serializable(with = ByteArrayListSerializer::class)
    val onetimePreKeys: List<ByteArray>
)
