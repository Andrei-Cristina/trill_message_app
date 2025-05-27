package org.message.trill

import org.message.trill.encryption.utils.models.User
import org.message.trill.messaging.models.ReceivedMessage
import org.message.trill.ui.ConversationMessage

expect class MessageClient() {
    suspend fun userLogOut()
    suspend fun loginUser(email:String, password: String):String
    suspend fun registerUser(email:String, password: String, nickname: String)
    suspend fun registerDevice(email: String, nickname: String)
    suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: String)
    suspend fun receiveMessages(email:String): List<ReceivedMessage>
    suspend fun searchUsersByEmail(email: String): List<User>
    suspend fun getRecentConversationPartners(currentUserEmail: String): List<String>
    suspend fun loadMessagesForConversation(currentUserEmail: String, contactEmail: String): List<ConversationMessage>
    suspend fun getLocalUserNickname(email: String): String?
}