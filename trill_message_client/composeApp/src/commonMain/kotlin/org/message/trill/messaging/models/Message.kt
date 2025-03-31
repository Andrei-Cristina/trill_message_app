package org.message.trill.messaging.models

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val senderId: String,
    val senderDeviceId: String,
    val recipientId: String,
    val recipientDeviceId: String,
    val content: MessageContent,
    val timestamp: String
)
