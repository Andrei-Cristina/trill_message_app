package org.message.trill

import org.message.trill.messaging.models.ReceivedMessage
import org.message.trill.ui.ConversationMessage

expect class MessageClient() {
    suspend fun loginUser(email:String, nickname: String):String
    suspend fun registerUser(email:String, nickname: String)
    suspend fun registerDevice(email: String, nickname: String)
    suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: String)
    suspend fun receiveMessages(email:String): List<ReceivedMessage>
    suspend fun searchUsersByEmail(email: String): List<String>
    suspend fun getRecentConversationPartners(currentUserEmail: String): List<String>
    suspend fun loadMessagesForConversation(currentUserEmail: String, contactEmail: String): List<ConversationMessage>
    suspend fun getLocalUserNickname(email: String): String?
}