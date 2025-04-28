package org.message.trill.networking.models

import kotlinx.serialization.Serializable
import org.message.trill.encryption.utils.ByteArraySerializer

@Serializable
data class LoginRequest(
    val email: String,
    val nickname: String,
    @Serializable(with = ByteArraySerializer::class)
    val identityKey: ByteArray
)
