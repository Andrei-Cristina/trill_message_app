package com.trill.message.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UserRegisterRequest(
    val email: String,
    val nickname: String
)
