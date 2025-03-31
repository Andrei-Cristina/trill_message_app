package org.message.trill.networking

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.message.trill.encryption.keys.PreKey
import org.message.trill.encryption.keys.PrekeyBundle
import org.message.trill.encryption.keys.SignedPreKey
import org.message.trill.messaging.models.Message
import java.util.Base64

class NetworkManager {
    private val client = HttpClient() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    private val baseUrl = "http://0.0.0.0:8080"

    suspend fun registerUser(email: String, nickname: String) {
        client.post("$baseUrl/users") {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "email" to email,
                "nickname" to nickname
            ))
        }
    }

    suspend fun registerDevice(userEmail: String, identityKey: ByteArray, signedPreKey: SignedPreKey, oneTimePreKeys: List<PreKey>): String {
        val response = client.post("$baseUrl/devices") {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "userEmail" to userEmail,
                "identityKey" to identityKey.encodeToBase64(),
                "signedPreKey" to signedPreKey.preKey.publicKey.encodeToBase64(),
                "preKeySignature" to signedPreKey.signature.encodeToBase64(),
                "onetimePreKeys" to oneTimePreKeys.map { it.publicKey.encodeToBase64() }
            ))
        }
        return Json.decodeFromString<Map<String, String>>(response.bodyAsText())["deviceId"]
            ?: throw Exception("Device ID not returned")
    }

    suspend fun fetchPrekeyBundle(userId: String, deviceId: String): PrekeyBundle {
        val response = client.get("$baseUrl/devices/keys") {
            contentType(ContentType.Application.Json)
            setBody(userId)
        }
        val bundle = Json.decodeFromString<PrekeyBundle>(response.bodyAsText())

        return bundle
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