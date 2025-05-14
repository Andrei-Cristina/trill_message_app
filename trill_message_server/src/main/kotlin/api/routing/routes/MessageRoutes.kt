package api.routing.routes

import data.models.Message
import data.models.MessageRequest
import data.repositories.DeviceRepository
import data.repositories.MessageRepository
import data.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.*

fun Route.messageRoutes() {
    val userRepository: UserRepository by inject()
    val deviceRepository: DeviceRepository by inject()
    val messageRepository: MessageRepository by inject()

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

                if (!userRepository.getByEmail(message.senderId).isSuccess) {
                    call.application.environment.log.warn("Sender not found: {}", message.senderId)
                    call.respond(HttpStatusCode.NotFound, "Sender not found: ${message.senderId}")
                    return@post
                }

                if (!userRepository.getByEmail(message.recipientId).isSuccess) {
                    call.application.environment.log.warn("Recipient not found: {}", message.recipientId)
                    call.respond(HttpStatusCode.NotFound, "Recipient not found: ${message.recipientId}")
                    return@post
                }

                try {
                    Base64.getDecoder().decode(message.senderDeviceId)
                    Base64.getDecoder().decode(message.recipientDeviceId)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid device ID format")
                    return@post
                }

                val senderDevices = deviceRepository.getAllDevices(message.senderId).getOrElse { emptyList() }
                val recipientDevices = deviceRepository.getAllDevices(message.recipientId).getOrElse { emptyList() }

                if (senderDevices.none { it.identityKey == message.senderDeviceId }) {
                    call.application.environment.log.warn("Sender device not found: {} for sender: {}", message.senderDeviceId, message.senderId)
                    call.respond(HttpStatusCode.NotFound, "Sender device not found: ${message.senderDeviceId}")
                    return@post
                }
                if (recipientDevices.none { it.identityKey == message.recipientDeviceId }) {
                    call.application.environment.log.warn("Recipient device not found: {} for recipient: {}", message.recipientDeviceId, message.recipientId)
                    call.respond(HttpStatusCode.NotFound, "Recipient device not found: ${message.recipientDeviceId}")
                    return@post
                }

                messageRepository.create(message).fold(
                    onSuccess = { id ->
                        call.application.environment.log.info("Stored message with ID: $id")
                    },
                    onFailure = { e ->
                        call.application.environment.log.error("Failed to store message: $e")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to store message")
                        return@post
                    }
                )
            }

            call.application.environment.log.info("Successfully processed {} messages", request.messages.size)
            call.respond(HttpStatusCode.OK, "Messages received successfully")
        }

        get("/{userId}/{deviceId}") {
            val userId = call.parameters["userId"]!!
            val deviceId = call.parameters["deviceId"]!!
            call.application.environment.log.info("Fetching messages for user: {} and device: {}", userId, deviceId)

            if (!userRepository.getByEmail(userId).isSuccess) {
                call.application.environment.log.warn("User not found: {}", userId)
                call.respond(HttpStatusCode.NotFound, "User not found: $userId")
                return@get
            }

            try {
                Base64.getDecoder().decode(deviceId)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid device ID format")
                return@get
            }

            messageRepository.getByRecipient(userId, deviceId).fold(
                onSuccess = { messages ->
                    call.application.environment.log.info("Returned {} messages for user: {} and device: {}", messages.size, userId, deviceId)
                    call.respond(HttpStatusCode.OK, messages)
                },
                onFailure = { e ->
                    call.application.environment.log.error("Failed to fetch messages: $e")
                    call.respond(HttpStatusCode.InternalServerError, "Error fetching messages: $e")
                }
            )
        }

        get {
            call.application.environment.log.warn("Unmatched GET request to /messages: path={}", call.request.path())
            call.respond(HttpStatusCode.BadRequest, "Invalid endpoint. Use /messages/{userId}/{deviceId}")
        }
    }
}