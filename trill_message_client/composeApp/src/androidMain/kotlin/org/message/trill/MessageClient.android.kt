package org.message.trill

actual class MessageClient actual constructor() {
    actual suspend fun registerUser(email: String, nickname: String) {
    }

    actual suspend fun registerDevice(email: String, nickname: String) {
    }

    actual suspend fun sendMessage(recipientUserId: String, plaintext: String) {
    }

    actual suspend fun receiveMessages(): List<String> {
        TODO("Not yet implemented")
    }

    actual suspend fun loginUser(email: String, nickname: String):String {
    }
}