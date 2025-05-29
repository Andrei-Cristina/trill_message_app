package org.message.trill.networking

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.keys.PrekeyBundle
import org.message.trill.encryption.keys.SignedPreKey
import org.message.trill.encryption.utils.models.User
import org.message.trill.messaging.models.Message
import org.message.trill.networking.models.DeviceRegistrationBundle
import org.message.trill.networking.models.EmailSearchRequest
import org.message.trill.networking.models.LoginRequest
import org.message.trill.networking.models.RegisterUserRequest
import java.net.URLEncoder
import java.util.*

class NetworkManager {
    private val clientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(clientJson)
        }
        install(WebSockets)
    }

    private val baseUrl = "http://0.0.0.0:8080"
    private val wsBaseUrl = "ws://0.0.0.0:8080"

    private var jwtToken: String? = null
    private var currentDeviceId: String? = null

    @Volatile
    private var webSocketSession: ClientWebSocketSession? = null
    private val networkManagerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _incomingMessagesFlow = MutableSharedFlow<Message>()
    val incomingMessagesFlow: SharedFlow<Message> = _incomingMessagesFlow.asSharedFlow()

    suspend fun login(
        userEmail: String,
        password: String,
        nickname: String,
        identityKey: ByteArray
    ): String? {
        val loginRequest = LoginRequest(
            email = userEmail,
            password = password,
            nickname = nickname,
            identityKey = identityKey
        )

        val response = try {
            client.post("$baseUrl/login") {
                contentType(ContentType.Application.Json)
                setBody(loginRequest)
            }
        } catch (e: Exception) {
            println("Login network request failed for $userEmail: ${e.message}")
            throw Exception("Login connection failed: ${e.message}", e)
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.bodyAsText()
                val jsonResponse = Json.decodeFromString<Map<String, String>>(body)
                this.jwtToken = jsonResponse["token"]
                if (this.jwtToken == null) {
                    throw Exception("JWT token not returned from login: $body")
                }
                this.currentDeviceId = identityKey.encodeToBase64()

                this.jwtToken?.let { token ->
                    this.currentDeviceId?.let { devId ->
                        connectWebSocket(token, devId)
                    }
                }

                jsonResponse["deviceId"]
                    ?: throw Exception("Device ID not returned: $body")
            }
            HttpStatusCode.NotFound -> {
                println("Login failed: User or device not found - ${response.bodyAsText()}")
                null
            }
            HttpStatusCode.Unauthorized -> {
                println("Login failed: Unauthorized - ${response.bodyAsText()}")
                throw Exception("Invalid email or credentials: ${response.bodyAsText()}")
            }
            else -> {
                println("Login failed: Unexpected response - ${response.status} - ${response.bodyAsText()}")
                throw Exception("Unexpected response: ${response.status} - ${response.bodyAsText()}")
            }
        }
    }

    fun logout() {
        println("Logging out user")
        disconnectWebSocket()
        jwtToken = null
        currentDeviceId = null
    }

    fun isAuthenticated(): Boolean {
        return jwtToken != null
    }

    private fun connectWebSocket(token: String, deviceId: String) {
        if (webSocketSession?.isActive == true) {
            println("WebSocket is already connected or connecting for device $deviceId.")
            return
        }
        println("Attempting to connect WebSocket for device: $deviceId with token: $token")
        networkManagerScope.launch {
            try {
                client.webSocket(
                    method = HttpMethod.Get,
                    host = "0.0.0.0",
                    port = 8080,
                    path = "/ws/chat?token=${token.encodeURLParameter()}&deviceId=${deviceId.encodeURLParameter()}"
                ) {
                    webSocketSession = this

                    println("WebSocket connection established for device: $deviceId.")

                    launch {
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    println("WebSocket received text: $text")
                                    try {
                                        val message = clientJson.decodeFromString<Message>(text)
                                        _incomingMessagesFlow.emit(message)
                                    } catch (e: Exception) {
                                        try {
                                            val errorResponse = clientJson.decodeFromString<Map<String, String>>(text)
                                            if (errorResponse.containsKey("error")) {
                                                println("Server responded with error via WebSocket: ${errorResponse["error"]}")
                                            } else {
                                                println("Error deserializing non-error message from WebSocket: ${e.message} for text: $text")
                                            }
                                        } catch (e2: Exception) {
                                            println("Error deserializing message AND error response from WebSocket: Primary error: ${e.message}, Secondary error: ${e2.message} for text: $text")
                                        }
                                    }
                                } else if (frame is Frame.Close) {
                                    println("WebSocket connection closed by server: ${frame.readReason()}")
                                    break
                                }
                            }
                        } catch (e: ClosedReceiveChannelException) {
                            println("WebSocket incoming channel closed for device $deviceId: ${e.message}")
                        } catch (e: CancellationException) {
                            println("WebSocket incoming message listener cancelled for $deviceId.")
                            throw e
                        } catch (e: Exception) {
                            println("Error in WebSocket incoming message listener for $deviceId: ${e.message}")
                        } finally {
                            println("WebSocket incoming listener finished for $deviceId.")
                        }
                    }

                    val reason = closeReason.await()
                    println("WebSocket session $deviceId completed with reason: $reason")
                }
            } catch (e: CancellationException) {
                println("WebSocket connection coroutine cancelled for device $deviceId.")
            } catch (e: Exception) {
                println("WebSocket connection failed or unhandled error for device $deviceId: ${e.javaClass.simpleName} - ${e.message}")
            } finally {
                println("Outer WebSocket session block finished for device $deviceId. Cleaning up.")
                if (webSocketSession?.coroutineContext?.isActive == true) {
                    try {
                        webSocketSession?.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client-side cleanup"))
                    } catch (ignore: Exception) {}
                }
                webSocketSession = null
            }
        }
    }

    private fun disconnectWebSocket() {
        println("Attempting to disconnect WebSocket.")
        val sessionToClose = webSocketSession
        webSocketSession = null
        networkManagerScope.launch {
            try {
                sessionToClose?.close(CloseReason(CloseReason.Codes.NORMAL, "Client logging out"))
                println("WebSocket disconnected initiated.")
            } catch (e: Exception) {
                println("Error during WebSocket disconnection: ${e.message}")
            }
        }
    }

    suspend fun sendMessagesViaWebSocket(messages: List<Message>): Boolean {
        val currentSession = webSocketSession
        if (currentSession == null || !currentSession.isActive) {
            println("Cannot send messages: WebSocket session is not active.")
            return false
        }

        println("Sending ${messages.size} messages via WebSocket.")
        var allSentSuccessfully = true
        for (message in messages) {
            try {
                val messageJson = clientJson.encodeToString(message)
                currentSession.send(Frame.Text(messageJson))
                println("Sent message to ${message.recipientId} via WebSocket.")
            } catch (e: Exception) {
                println("Error sending message to ${message.recipientId} via WebSocket: ${e.message}")
                allSentSuccessfully = false
            }
        }
        return allSentSuccessfully
    }

    suspend fun registerUser(email: String, password: String, nickname: String) {
        val response = try {
            client.post("$baseUrl/users") {
                contentType(ContentType.Application.Json)
                setBody(RegisterUserRequest(
                    email = email,
                    password = password,
                    nickname = nickname
                ))
            }
        } catch (e: Exception) {
            println("User registration network request failed for $email: ${e.message}")
            throw Exception("Registration connection failed: ${e.message}", e)
        }

        when (response.status) {
            HttpStatusCode.Created -> {
                println("User $email registration successful. Status: ${response.status}")
            }
            HttpStatusCode.Conflict -> {
                println("User registration failed for $email: Email already in use. Status: ${response.status}")
                throw Exception("Email already registered.")
            }
            HttpStatusCode.BadRequest -> {
                println("User registration failed for $email: Bad request. Status: ${response.status}")
                throw Exception("Invalid registration data.")
            }
            else -> {
                println("User registration failed for $email: Unexpected response - ${response.status} - ${response.bodyAsText()}")
                throw Exception("Unexpected response: ${response.status} - ${response.bodyAsText()}")
            }
        }
    }

    suspend fun registerDevice(userEmail: String, identityKey: ByteArray, signedPreKey: SignedPreKey, oneTimePreKeys: List<PreKey>): String {
        val bundle = DeviceRegistrationBundle(
            userEmail = userEmail,
            identityKey = identityKey,
            signedPreKey = signedPreKey.preKey.publicKey,
            preKeySignature = signedPreKey.signature,
            onetimePreKeys = oneTimePreKeys.map { it.publicKey }
        )

        val response: HttpResponse = client.post("$baseUrl/devices") {
            contentType(ContentType.Application.Json)
            //header(HttpHeaders.Authorization, "Bearer $jwtToken")
            setBody(bundle)
        }

        val jsonPayload = Json.encodeToString(bundle)
        println("Sending JSON to server: $jsonPayload")

        when (response.status) {
            HttpStatusCode.Created -> {
                val body = response.bodyAsText()
                return Json.decodeFromString<Map<String, String>>(body)["deviceId"]
                    ?: throw Exception("Device ID not returned in response: $body")
            }
            HttpStatusCode.NotFound -> throw Exception("User not found: ${response.bodyAsText()}")
            HttpStatusCode.InternalServerError -> throw Exception("Server error: ${response.bodyAsText()}")
            else -> throw Exception("Unexpected response: ${response.status} - ${response.bodyAsText()}")
        }
    }

    suspend fun fetchPrekeyBundle(userId: String, deviceId: String): PrekeyBundle {
        val response = client.get("$baseUrl/devices/keys") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $jwtToken")
            setBody(userId)
        }

        println("Response: ${response.status} for prekey bundle , body: ${response.bodyAsText()}")

        return Json.decodeFromString<PrekeyBundle>(response.bodyAsText())
    }

    suspend fun fetchUserDevices(userId: String): Map<String, ByteArray> {
        val response = client.get("$baseUrl/devices/all/keys") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $jwtToken")
            setBody(userId)
        }
        val bundles = Json.decodeFromString<List<PrekeyBundle>>(response.bodyAsText())
        return bundles.associate {
            it.identityKey to Base64.getDecoder().decode(it.identityKey)
        }
    }

    suspend fun sendMessages(
        messages: List<Message>
    ): NetworkResponse {
        println("Sending messages")
        val response = client.post("$baseUrl/messages") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $jwtToken")
            setBody(mapOf(
                "messages" to messages.map { message ->
                    mapOf(
                        "message" to message
                    )
                }
            ))
        }
        return when (response.status) {
            HttpStatusCode.OK -> NetworkResponse.Success
            HttpStatusCode.NotFound -> NetworkResponse.UserNotFound
            HttpStatusCode.Unauthorized -> {
                println("Unauthorized to send messages. Token might be invalid or expired.")
                logout()
                throw Exception("Unauthorized: Token invalid or expired")
            }
            HttpStatusCode.Conflict -> {
                val data = Json.decodeFromString<Map<String, Any>>(response.bodyAsText())
                NetworkResponse.DeviceMismatch(
                    oldDevices = (data["oldDevices"] as List<String>),
                    newDevices = (data["newDevices"] as Map<String, String>).mapValues { it.value.decodeFromBase64() }
                )
            }
            else -> throw Exception("Unexpected response: ${response.status}")
        }
    }

    suspend fun fetchMessages(userId: String, deviceId: String): List<Message> {
        if (jwtToken == null) {
            println("Not authenticated, cannot fetch messages.")
            return emptyList()
        }

        val encodedDeviceId = withContext(Dispatchers.IO) {
            URLEncoder.encode(deviceId, "UTF-8")
        }
        println("Fetching messages for $userId/$deviceId with token $jwtToken")
        try {
            val response = client.get("$baseUrl/messages/$encodedDeviceId") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $jwtToken")
            }
            println("Fetch messages response: ${response.status}, body: ${response.bodyAsText()}")
            return when (response.status) {
                HttpStatusCode.OK -> {
                    val body = response.bodyAsText()
                    if (body.isEmpty() || body == "[]") {
                        println("No messages found for $userId/$encodedDeviceId")
                        emptyList()
                    } else {
                        Json.decodeFromString<List<Message>>(body)
                    }
                }
                HttpStatusCode.Unauthorized -> {
                    println("Unauthorized to fetch messages. Token might be invalid or expired.")
                    logout()
                    emptyList()
                }
                HttpStatusCode.NotFound -> {
                    println("No messages or user/device not found for $userId/$encodedDeviceId")
                    emptyList()
                }
                else -> {
                    println("Unexpected response: ${response.status}, body: ${response.bodyAsText()}")
                    throw Exception("Unexpected response: ${response.status}")
                }
            }
        } catch (e: Exception) {
            println("Error fetching messages for $userId/$encodedDeviceId: ${e.message}")
            return emptyList()
        }
    }

    suspend fun searchUsersByEmail(email: String): List<User> {
        try {
            val response = client.post("$baseUrl/users/search") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $jwtToken")
                setBody(EmailSearchRequest(email))
            }
            val body = response.bodyAsText()
            println("Search users response: status=${response.status}, body=$body for query: $email")

            return when (response.status) {
                HttpStatusCode.OK -> {
                    if (body.isEmpty() || body == "[]") {
                        println("Empty search results for email: $email")
                        emptyList()
                    } else {
                        try {
                            Json.decodeFromString<List<User>>(body)
                        } catch (e: kotlinx.serialization.SerializationException) {
                            try {
                                val user = Json.decodeFromString<User>(body)
                                listOf(user)
                            } catch (e2: kotlinx.serialization.SerializationException) {
                                println("Failed to parse search response as List<User> or User: '$body'. Error for List: ${e.message}. Error for User: ${e2.message}")
                                emptyList()
                            }
                        }
                    }
                }
                HttpStatusCode.NotFound -> {
                    println("No users found for email: $email (404)")
                    emptyList()
                }
                else -> {
                    println("Unexpected response for search users ($email): ${response.status}, body=$body")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            println("Error during searchUsersByEmail for '$email': ${e.message}")
            return emptyList()
        }
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun String.decodeFromBase64(): ByteArray = Base64.getDecoder().decode(this)
}