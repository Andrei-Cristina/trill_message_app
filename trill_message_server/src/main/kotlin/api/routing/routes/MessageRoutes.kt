package com.trill.message.api.routing.routes

import com.trill.message.data.models.Message
import com.trill.message.data.models.MessageRequest
import com.trill.message.data.repositories.DeviceRepository
import com.trill.message.data.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.messageRoutes() {
    val userRepository: UserRepository by inject()
    val deviceRepository: DeviceRepository by inject()

    route("/messages") {
        post {
            val request = call.receive<MessageRequest>()

            for (messageWrapper in request.messages) {
                val message = messageWrapper.message

                val senderIdResult = userRepository.getIdByEmail(message.senderId)
                val recipientIdResult = userRepository.getIdByEmail(message.recipientId)

                if (senderIdResult.isFailure) {
                    call.respond(HttpStatusCode.NotFound, "Sender not found: ${message.senderId}")
                    return@post
                }

                if (recipientIdResult.isFailure) {
                    call.respond(HttpStatusCode.NotFound, "Recipient not found: ${message.recipientId}")
                    return@post
                }

                val senderId = senderIdResult.getOrThrow()
                val recipientId = recipientIdResult.getOrThrow()

                val senderDevices = deviceRepository.getAllDevices(senderId).getOrElse { emptyList() }
                val recipientDevices = deviceRepository.getAllDevices(recipientId).getOrElse { emptyList() }

                if (senderDevices.none { it.identityKey.toString() == message.senderDeviceId }) {
                    call.respond(HttpStatusCode.NotFound, "Sender device not found: ${message.senderDeviceId}")
                    return@post
                }
                if (recipientDevices.none { it.identityKey.toString() == message.recipientDeviceId }) {
                    call.respond(HttpStatusCode.NotFound, "Recipient device not found: ${message.recipientDeviceId}")
                    return@post
                }

                call.application.environment.log.info("Received message from ${message.senderId} to ${message.recipientId}")
            }

            call.respond(HttpStatusCode.OK, "Messages received successfully")
        }

        get("/{userId}/{deviceId}") {
            val userId = call.parameters["userId"]!!
            val deviceId = call.parameters["deviceId"]!!

            val recipientId = userRepository.getIdByEmail(userId).fold(
                onSuccess = { it },
                onFailure = { return@get call.respond(HttpStatusCode.NotFound, "User not found: $userId") }
            )

            val messages = listOf<Message>()

            call.respond(HttpStatusCode.OK, messages)
        }
    }
}