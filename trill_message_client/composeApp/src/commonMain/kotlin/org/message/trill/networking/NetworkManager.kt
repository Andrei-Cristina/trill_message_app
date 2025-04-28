package org.message.trill.networking

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.keys.PrekeyBundle
import org.message.trill.encryption.keys.SignedPreKey
import org.message.trill.messaging.models.Message
import org.message.trill.networking.models.DeviceRegistrationBundle
import org.message.trill.networking.models.LoginRequest
import org.message.trill.networking.models.RegisterUserRequest
import org.slf4j.LoggerFactory
import java.util.Base64

class NetworkManager {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
    }
    private val baseUrl = "http://0.0.0.0:8080"

    suspend fun login(
        userEmail: String,
        nickname: String,
        identityKey: ByteArray
    ): String? {
        val loginRequest = LoginRequest(
            email = userEmail,
            nickname = nickname,
            identityKey = identityKey
        )

        val response = client.post("$baseUrl/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.bodyAsText()
                Json.decodeFromString<Map<String, String>>(body)["deviceId"]
                    ?: throw Exception("Device ID not returned: $body")
            }
            HttpStatusCode.NotFound -> null
            HttpStatusCode.Unauthorized -> throw Exception("Invalid email or nickname: ${response.bodyAsText()}")
            else -> throw Exception("Unexpected response: ${response.status} - ${response.bodyAsText()}")
        }
    }

    suspend fun registerUser(email: String, nickname: String) {
        client.post("$baseUrl/users") {
            contentType(ContentType.Application.Json)
            setBody(RegisterUserRequest(
                email = email,
                nickname = nickname
            ))
        }
    }

    suspend fun registerDevice(userEmail: String, identityKey: ByteArray, signedPreKey: SignedPreKey, oneTimePreKeys: List<PreKey>): String {
        val logger = LoggerFactory.getLogger("Client")

        val bundle = DeviceRegistrationBundle(
            userEmail = userEmail,
            identityKey = identityKey,
            signedPreKey = signedPreKey.preKey.publicKey,
            preKeySignature = signedPreKey.signature,
            onetimePreKeys = oneTimePreKeys.map { it.publicKey }
        )

        val response: HttpResponse = client.post("$baseUrl/devices") {
            contentType(ContentType.Application.Json)
            setBody(bundle)
        }

        val jsonPayload = Json.encodeToString(bundle)
        logger.info("Sending JSON to server: $jsonPayload")

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
            setBody(userId)
        }

        return Json.decodeFromString<PrekeyBundle>(response.bodyAsText())
    }

    suspend fun fetchUserDevices(userId: String): Map<String, ByteArray> {
        val response = client.get("$baseUrl/devices/all/keys") {
            contentType(ContentType.Application.Json)
            setBody(userId)
        }
        val bundles = Json.decodeFromString<List<PrekeyBundle>>(response.bodyAsText())

        return bundles.associate { it.identityKey.toString() to it.identityKey }
    }

    suspend fun sendMessages(
        messages: List<Message>
    ): NetworkResponse {
        val response = client.post("$baseUrl/messages") {
            contentType(ContentType.Application.Json)
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
        val response = client.get("$baseUrl/messages/$userId/$deviceId")

        return Json.decodeFromString(response.bodyAsText())
    }

    private fun ByteArray.encodeToBase64(): String = Base64.getEncoder().encodeToString(this)
    private fun String.decodeFromBase64(): ByteArray = Base64.getDecoder().decode(this)

}