package com.trill.message.api.websocket.routes

import com.trill.message.api.websocket.WebSocketHandler
import com.trill.message.data.models.Message
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

fun Route.messageRoutes() {
    webSocket("/ws") {
        val deviceId = call.parameters["deviceId"] ?: run {
            close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Missing deviceId"))
            return@webSocket
        }

        try {
            WebSocketHandler.addSession(deviceId, this)

            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val messageText = frame.readText()
                    println("Received: $messageText")

                    try {
                        val message = Json.decodeFromString<Message>(messageText)
                        WebSocketHandler.sendMessage(message.recipientDeviceId, messageText)
                    } catch (e: Exception) {
                        println("Failed to parse message: $e")
                    }
                }
            }
        } catch (e: Exception) {
            close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.message ?: "Error"))
        } finally {
            WebSocketHandler.removeSession(deviceId)
        }
    }
}