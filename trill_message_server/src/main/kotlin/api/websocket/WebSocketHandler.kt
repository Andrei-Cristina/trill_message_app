package com.trill.message.api.websocket

import com.trill.message.data.models.Message
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.concurrent.ConcurrentHashMap

object WebSocketHandler {
    private val sessions = ConcurrentHashMap<String, WebSocketServerSession>()
    private val offlineMessages = ConcurrentHashMap<String, MutableList<String>>()

    suspend fun addSession(deviceId: String, session: WebSocketServerSession) {
        sessions[deviceId] = session
        val messages = offlineMessages.remove(deviceId)
        messages?.forEach { message ->
            session.send(Frame.Text(message))
        }
    }

    fun removeSession(deviceId: String) {
        sessions.remove(deviceId)
    }
    //TODO: Implement proper message storage into redis cache
    suspend fun sendMessage(receiverDeviceId: String, message: String) {
        val session = sessions[receiverDeviceId]
        if (session != null) {
            session.send(Frame.Text(message))
        } else {
            offlineMessages.computeIfAbsent(receiverDeviceId) { mutableListOf() }.add(message)
        }
    }
}