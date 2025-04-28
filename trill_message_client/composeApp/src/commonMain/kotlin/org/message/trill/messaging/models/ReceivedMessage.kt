package org.message.trill.messaging.models

data class ReceivedMessage(val senderId: String, val content: String, val timestamp: String)
