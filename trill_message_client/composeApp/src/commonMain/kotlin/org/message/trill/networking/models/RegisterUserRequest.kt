package org.message.trill.networking.models

import kotlinx.serialization.Serializable

@Serializable
class RegisterUserRequest(
    val email: String,
    val nickname: String
)