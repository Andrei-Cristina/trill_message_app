package data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bson.Document

@Serializable
data class UserModel(
    val email: String,
    val password: String,
    val nickname: String,
    val isOnline: Boolean,
    val lastOnline: String
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromDocument(document: Document): UserModel = json.decodeFromString(document.toJson())
    }

    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    fun toApiUser(): User {
        return User(
            email = this.email,
            nickname = this.nickname,
            isOnline = this.isOnline,
            lastOnline = this.lastOnline
        )
    }
}
