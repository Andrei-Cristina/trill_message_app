package data.models

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val email: String
    //TODO: Add password field and use for jwt
)
