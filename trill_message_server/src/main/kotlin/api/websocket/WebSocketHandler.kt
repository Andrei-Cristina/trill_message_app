package api.websocket

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.ktor.websocket.CloseReason.Codes.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.koin.core.component.KoinComponent
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import org.koin.core.component.inject
import org.slf4j.Logger


data class WebSocketConnection(
    val userEmail: String,
    val deviceId: String,
    val session: DefaultWebSocketServerSession
)

class WebSocketHandler : KoinComponent {
    val logger: Logger = LoggerFactory.getLogger(javaClass)
    val json: Json by inject()

    val activeConnections = ConcurrentHashMap<String, WebSocketConnection>()

    fun registerConnection(userEmail: String, deviceId: String, session: DefaultWebSocketServerSession) {
        val connection = WebSocketConnection(userEmail, deviceId, session)
        activeConnections[deviceId] = connection
        logger.info("WebSocket connection registered for user: $userEmail, device: $deviceId. Total active: ${activeConnections.size}")
    }

    fun unregisterConnection(deviceId: String) {
        val removedConnection = activeConnections.remove(deviceId)
        if (removedConnection != null) {
            logger.info("WebSocket connection unregistered for device: $deviceId. Total active: ${activeConnections.size}")
        } else {
            logger.debug("Attempted to unregister non-existent or already removed WebSocket connection for device: $deviceId")
        }
    }

    fun getConnectionByDeviceId(deviceId: String): WebSocketConnection? {
        return activeConnections[deviceId]
    }

    suspend inline fun <reified T : Any> sendMessageToDevice(deviceId: String, message: T) {
        val connection = activeConnections[deviceId]
        if (connection != null && connection.session.isActive) {
            try {
                val serializer = json.serializersModule.serializer<T>()
                val messageJson = json.encodeToString(serializer, message)
                connection.session.send(Frame.Text(messageJson))
                logger.info("Message of type ${T::class.simpleName} sent via WebSocket to device: $deviceId")
            } catch (e: Exception) {
                logger.error("Error sending message to device $deviceId via WebSocket: ${e.message}", e)
                if (e is ClosedReceiveChannelException || e.cause is ClosedReceiveChannelException || e is java.io.IOException) {
                    logger.warn("Removing problematic session for device $deviceId due to send error.")
                    unregisterConnection(deviceId)
                    connection.session.close(CloseReason(INTERNAL_ERROR, "Send error"))
                }
            }
        } else {
            if (connection == null) {
                logger.info("No active WebSocket session for device: $deviceId. Message will be stored for later retrieval.")
            } else {
                logger.warn("WebSocket session for device: $deviceId is inactive. Message will be stored.")
            }
        }
    }
}