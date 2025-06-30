package org.message.trill

import kotlinx.coroutines.flow.SharedFlow
import org.message.trill.encryption.utils.models.User
import org.message.trill.messaging.models.ConversationMessage
import org.message.trill.messaging.models.FilePointer
import org.message.trill.messaging.models.ReceivedMessage

expect class MessageClient() {
    val newMessageForUiFlow: SharedFlow<ReceivedMessage>

    suspend fun userLogOut()
    suspend fun loginUser(email:String, password: String):String
    suspend fun registerUser(email:String, password: String, nickname: String)
    suspend fun registerDevice(email: String, nickname: String)
    suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: String)
    suspend fun sendFile(senderId: String, recipientUserId: String, filePath: String)
    suspend fun downloadAndDecryptFile(filePointer: FilePointer)
    suspend fun receiveMessages(email:String): List<ReceivedMessage>
    suspend fun searchUsersByEmail(email: String): List<User>
    suspend fun getRecentConversationPartners(currentUserEmail: String): List<String>
    suspend fun loadMessagesForConversation(currentUserEmail: String, contactEmail: String): List<ConversationMessage>
    suspend fun getLocalUserNickname(email: String): String?
}