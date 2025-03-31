package com.trill.message.data.models

import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(
    val messages: List<MessageWrapper>
)


