package org.message.trill.messaging.models

data class LocalDbMessage(
    val id: Long,
    val senderEmail: String,
    val receiverEmail: String,
    val content: String,
    val timestamp: Long,
    val isSentByLocalUser: Boolean
)
