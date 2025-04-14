package data.models

import kotlinx.serialization.Serializable

@Serializable
data class AuthVerifyRequest(
    val email: String,
    val otpCode: String
)
