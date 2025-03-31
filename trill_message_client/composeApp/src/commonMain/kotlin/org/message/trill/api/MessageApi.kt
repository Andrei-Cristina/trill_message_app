package org.message.trill.api

import org.message.trill.networking.NetworkResponse

interface MessageApi {
    suspend fun sendMessages(userId: String, messages: List<Pair<String, ByteArray>>): NetworkResponse
}