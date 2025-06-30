package org.message.trill.messaging.models

data class ConversationMessage(val id: String, val content: String, val isSent: Boolean, val timestamp: String, val filePointer: FilePointer? = null)
