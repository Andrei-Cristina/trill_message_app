package com.trill.message.data.models

import io.ktor.util.date.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document


@Serializable
data class Device(
    val userId: String,
    val identityKey: ByteArray,
    val signedPreKey: ByteArray,
    val preKeySignature: ByteArray,
    val onetimePreKeys: List<ByteArray>,
    val isOnline: Boolean,
    val isPrimary: Boolean,
    val lastOnline: String
){
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromDocument(document: Document): Device = json.decodeFromString(document.toJson())
    }

    fun toDocument(): Document = Document.parse(Json.encodeToString(this))
}
