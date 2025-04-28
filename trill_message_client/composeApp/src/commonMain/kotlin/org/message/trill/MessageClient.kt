package org.message.trill

expect class MessageClient(userId: String) {
    suspend fun loginUser(email:String, nickname: String):String
    suspend fun registerUser(email:String, nickname: String)
    suspend fun registerDevice(email: String, nickname: String)
    suspend fun sendMessage(recipientUserId: String, plaintext: String)
    suspend fun receiveMessages(): List<String>
}