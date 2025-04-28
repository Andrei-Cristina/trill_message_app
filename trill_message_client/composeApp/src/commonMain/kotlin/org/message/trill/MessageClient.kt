package org.message.trill

import org.message.trill.messaging.models.ReceivedMessage

expect class MessageClient() {
    suspend fun loginUser(email:String, nickname: String):String
    suspend fun registerUser(email:String, nickname: String)
    suspend fun registerDevice(email: String, nickname: String)
    suspend fun sendMessage(senderId: String, recipientUserId: String, plaintext: String)
    suspend fun receiveMessages(email:String): List<ReceivedMessage>
    suspend fun searchUsersByEmail(email: String): List<String>
}