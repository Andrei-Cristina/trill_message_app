package api.websocket.routes

import api.websocket.WebSocketHandler
import data.models.Message
import data.models.UserPrincipal
import data.repositories.DeviceRepository
import data.repositories.MessageRepository
import data.repositories.UserRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.Base64

fun Application.configureWebSocketRouting() {
    val webSocketSessionManager: WebSocketHandler by inject()
    val messageRepository: MessageRepository by inject()
    val userRepository: UserRepository by inject()
    val deviceRepository: DeviceRepository by inject()
    val json: Json by inject()
    val logger = LoggerFactory.getLogger("WebSocketRoutes")

    routing {
        authenticate("auth-jwt") {
            webSocket("/ws/chat") {
                val principal = call.principal<UserPrincipal>()
                if (principal == null) {
                    logger.warn("WebSocket connection attempt without authenticated principal.")
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication failed"))
                    return@webSocket
                }
                val userEmail = principal.userEmail

                val deviceId = call.request.queryParameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    logger.warn("WebSocket connection attempt for user $userEmail without deviceId.")
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "deviceId query parameter is required"))
                    return@webSocket
                }

                logger.info("WebSocket connection attempt for user: $userEmail, device: $deviceId")
                webSocketSessionManager.registerConnection(userEmail, deviceId, this)

                try {
                    logger.info("Fetching pending messages for $userEmail, device $deviceId")
                    val pendingMessagesResult = messageRepository.getByRecipient(userEmail, deviceId)
                    pendingMessagesResult.fold(
                        onSuccess = { messages ->
                            if (messages.isNotEmpty()) {
                                logger.info("Sending ${messages.size} pending messages to $userEmail, device $deviceId")
                                for (message in messages) {
                                    try {
                                        webSocketSessionManager.sendMessageToDevice(deviceId, message)
                                    } catch (e: Exception) {
                                        logger.error("Error sending pending message to $userEmail, device $deviceId: ${e.message}", e)
                                    }
                                }
                                logger.info("Finished sending pending messages for $userEmail, device $deviceId")
                            } else {
                                logger.info("No pending messages for $userEmail, device $deviceId")
                            }
                        },
                        onFailure = { e ->
                            logger.error("Failed to fetch pending messages for $userEmail, device $deviceId: ${e.message}", e)
                        }
                    )

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            logger.debug("WebSocket received text from $userEmail, device $deviceId: $text")
                            try {
                                val receivedMessage = json.decodeFromString<Message>(text)

                                if (receivedMessage.senderId != userEmail) {
                                    logger.warn("Message senderId (${receivedMessage.senderId}) does not match authenticated user ($userEmail). Discarding.")
                                    send(Frame.Text(json.encodeToString(mapOf("error" to "Sender ID mismatch"))))
                                    continue
                                }
                                if (receivedMessage.senderDeviceId != deviceId) {
                                    logger.warn("Message senderDeviceId (${receivedMessage.senderDeviceId}) does not match connection's deviceId ($deviceId). Discarding.")
                                    send(Frame.Text(json.encodeToString(mapOf("error" to "Sender Device ID mismatch"))))
                                    continue
                                }

                                if (!userRepository.getByEmail(receivedMessage.recipientId).isSuccess) {
                                    logger.warn("Recipient not found for WS message: ${receivedMessage.recipientId}")
                                    continue
                                }
                                try {
                                    Base64.getDecoder().decode(receivedMessage.senderDeviceId)
                                    Base64.getDecoder().decode(receivedMessage.recipientDeviceId)
                                } catch (e: Exception) {
                                    logger.warn("Invalid device ID format in WS message")
                                    continue
                                }
                                val recipientDevices = deviceRepository.getAllDevices(receivedMessage.recipientId).getOrElse { emptyList() }
                                if (recipientDevices.none { it.identityKey == receivedMessage.recipientDeviceId }) {
                                    logger.warn("Recipient device not found for WS message: ${receivedMessage.recipientDeviceId} for recipient: ${receivedMessage.recipientId}")
                                    continue
                                }

                                val recipientConnection = webSocketSessionManager.getConnectionByDeviceId(receivedMessage.recipientDeviceId)
                                if (recipientConnection != null && recipientConnection.session.isActive) {
                                    logger.info("Forwarding message from ${receivedMessage.senderId} to ${receivedMessage.recipientId} (device ${receivedMessage.recipientDeviceId}) via WebSocket.")
                                    webSocketSessionManager.sendMessageToDevice(receivedMessage.recipientDeviceId, receivedMessage)
                                } else {
                                    logger.info("Recipient ${receivedMessage.recipientId} (device ${receivedMessage.recipientDeviceId}) is offline. Storing message.")
                                    messageRepository.create(receivedMessage).fold(
                                        onSuccess = { id -> logger.info("Stored offline message with ID: $id") },
                                        onFailure = { e -> logger.error("Failed to store offline message: $e") }
                                    )
                                }
                            } catch (e: kotlinx.serialization.SerializationException) {
                                logger.error("Failed to deserialize message from $userEmail, device $deviceId: $text. Error: ${e.message}")
                                send(Frame.Text(json.encodeToString(mapOf("error" to "Invalid message format"))))
                            } catch (e: Exception) {
                                logger.error("Error processing message from $userEmail, device $deviceId: ${e.message}", e)
                            }
                        }
                    }
                } catch (e: ClosedReceiveChannelException) {
                    logger.info("WebSocket connection closed for user: $userEmail, device: $deviceId. Reason: Client disconnected.")
                } catch (e: Throwable) {
                    logger.error("Error in WebSocket session for user: $userEmail, device: $deviceId: ${e.message}", e)
                    close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Server error: ${e.javaClass.simpleName}"))
                } finally {
                    logger.info("Unregistering WebSocket connection for user: $userEmail, device: $deviceId from finally block")
                    webSocketSessionManager.unregisterConnection(deviceId)
                }
            }
        }
    }
}