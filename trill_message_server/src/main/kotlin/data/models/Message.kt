package com.trill.message.data.models

import io.ktor.util.date.*
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val sender: String,
    val receiver: String,
    val content: String,
    val timestamp: GMTDate
)
