package data.repositories

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import data.models.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.bson.Document
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class MessageRepository : KoinComponent {
    private val database: MongoDatabase by inject()
    private val collection: MongoCollection<Document>
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        database.createCollection("messages")
        collection = database.getCollection("messages")
    }

    suspend fun create(message: Message): Result<String> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            val doc = Document.parse(Json.encodeToString(Message.serializer(), message))
            collection.insertOne(doc)
            doc["_id"].toString()
        }
    }

    suspend fun getByRecipient(userId: String, deviceId: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            val filter = Filters.and(
                Filters.eq("recipientId", userId),
                Filters.eq("recipientDeviceId", deviceId)
            )

            val messages = collection.find(filter).map { doc ->
                json.decodeFromString(Message.serializer(), doc.toJson())
            }.toList()

            collection.deleteMany(filter)
            messages
        }
    }
}