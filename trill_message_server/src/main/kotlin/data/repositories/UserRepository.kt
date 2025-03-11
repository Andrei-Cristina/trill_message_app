package com.trill.message.data.repositories

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.trill.message.data.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.bson.Document
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.regex.Pattern

class UserRepository : KoinComponent {
    private val database: MongoDatabase by inject()
    private var collection: MongoCollection<Document>

    init {
        database.createCollection("users")
        collection = database.getCollection("users")
    }

    suspend fun create(item: User): Result<String> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            val doc = item.toDocument()
            collection.insertOne(doc)
            doc["_id"].toString()
        }
    }

    suspend fun getAll(): Result<List<User>> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.find().map { document -> User.fromDocument(document) }.toList()
        }
    }

    suspend fun getById(id: String): Result<User> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.find(Filters.eq("_id", ObjectId(id)))
                .first()
                ?.let { document ->
                    Json.decodeFromString<User>(document.toJson())
                } ?: throw NoSuchElementException("User with id $id not found")
        }
    }

    suspend fun getByEmail(email: String): Result<User> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.find(Filters.eq("email", email))
                .first()
                ?.let { document ->
                    Json.decodeFromString<User>(document.toJson())
                } ?: throw NoSuchElementException("User with email $email not found")
        }
    }

    suspend fun getIdByEmail(email: String): Result<String> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.find(Filters.eq("email", email))
                .first()
                ?.let { document ->
                    document["_id"].toString()
                } ?: throw NoSuchElementException("User with email $email not found")
        }
    }

    suspend fun getByNickName(nickname: String): Result<List<User>> = withContext(Dispatchers.IO) {
        runCatching {
            val regex = Pattern.compile(".*${Pattern.quote(nickname)}.*", Pattern.CASE_INSENSITIVE)
            collection.find(Filters.regex("nickname", regex))
                .map { document ->
                    Json.decodeFromString<User>(document.toJson())
                }
                .toList()
        }
    }

    suspend fun update(id: String, item: User): Result<Document?> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.findOneAndUpdate(
                Filters.eq("_id", ObjectId(id)),
                Document("\$set", item.toDocument()),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            )
        }
    }

    suspend fun delete(id: String): Result<Document?> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.findOneAndDelete(Filters.eq("_id", ObjectId(id)))
        }
    }
}