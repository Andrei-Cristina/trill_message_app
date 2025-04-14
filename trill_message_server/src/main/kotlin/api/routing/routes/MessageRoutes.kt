package api.routing.routes

import data.models.Message
import data.models.MessageRequest
import data.repositories.DeviceRepository
import data.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
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
            call.application.environment.log.info("Received message request with {} messages", request.messages.size)

            for (messageWrapper in request.messages) {
                val message = messageWrapper.message
                call.application.environment.log.debug(
                    "Processing message from {} (device: {}) to {} (device: {})",
                    message.senderId, message.senderDeviceId, message.recipientId, message.recipientDeviceId
                )

                val senderIdResult = userRepository.getIdByEmail(message.senderId)
                val recipientIdResult = userRepository.getIdByEmail(message.recipientId)

                if (senderIdResult.isFailure) {
                    call.application.environment.log.warn("Sender not found: {}", message.senderId)
                    call.respond(HttpStatusCode.NotFound, "Sender not found: ${message.senderId}")
                    return@post
                }

                if (recipientIdResult.isFailure) {
                    call.application.environment.log.warn("Recipient not found: {}", message.recipientId)
                    call.respond(HttpStatusCode.NotFound, "Recipient not found: ${message.recipientId}")
                    return@post
                }

                val senderId = senderIdResult.getOrThrow()
                val recipientId = recipientIdResult.getOrThrow()

                val senderDevices = deviceRepository.getAllDevices(senderId).getOrElse { emptyList() }
                val recipientDevices = deviceRepository.getAllDevices(recipientId).getOrElse { emptyList() }

                if (senderDevices.none { it.identityKey.toString() == message.senderDeviceId }) {
                    call.application.environment.log.warn("Sender device not found: {} for sender: {}", message.senderDeviceId, message.senderId)
                    call.respond(HttpStatusCode.NotFound, "Sender device not found: ${message.senderDeviceId}")
                    return@post
                }
                if (recipientDevices.none { it.identityKey.toString() == message.recipientDeviceId }) {
                    call.application.environment.log.warn("Recipient device not found: {} for recipient: {}", message.recipientDeviceId, message.recipientId)
                    call.respond(HttpStatusCode.NotFound, "Recipient device not found: ${message.recipientDeviceId}")
                    return@post
                }

                call.application.environment.log.info(
                    "Received message: " +
                            "from ${message.senderId} (device: ${message.senderDeviceId}) " +
                            "to ${message.recipientId} (device: ${message.recipientDeviceId}), " +
                            "content=${message.content}, timestamp=${message.timestamp}"
                )
            }

            call.respond(HttpStatusCode.OK, "Messages received successfully")
            call.application.environment.log.info("Successfully processed {} messages", request.messages.size)
        }

        get("/{userId}/{deviceId}") {
            val userId = call.parameters["userId"]!!
            val deviceId = call.parameters["deviceId"]!!
            call.application.environment.log.info("Fetching messages for user: {} and device: {}", userId, deviceId)

            val recipientId = userRepository.getIdByEmail(userId).fold(
                onSuccess = { it },
                onFailure = { e ->
                    call.application.environment.log.warn("User not found: {}. Error: {}", userId, e.message)
                    return@get call.respond(HttpStatusCode.NotFound, "User not found: $userId")
                }
            )

            val messages = listOf<Message>()
            call.respond(HttpStatusCode.OK, messages)
            call.application.environment.log.info("Returned {} messages for user ID: {} and device: {}", messages.size, recipientId, deviceId)
        }
    }
}