package com.trill.message.data.repositories

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.trill.message.data.models.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.bson.Document
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DeviceRepository(): KoinComponent {
    private val database: MongoDatabase by inject()
    private var collection: MongoCollection<Document>

    init {
        database.createCollection("devices")
        collection = database.getCollection("devices")
    }

    suspend fun create(item: Device): Result<String> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            val doc = item.toDocument()
            collection.insertOne(doc)
            doc["_id"].toString()
        }
    }

    suspend fun getAll(): Result<List<Device>> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.find().map { document -> Device.fromDocument(document) }.toList()
        }
    }

    suspend fun getAllDevices(userId: String): Result<List<Device>> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.find(Filters.eq("userId", userId))
                .map { document -> Device.fromDocument(document) }.toList()
        }
    }

    suspend fun getById(id: String): Result<Device> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.find(Filters.eq("identityKey", id))
                .first()
                ?.let { document ->
                    Json.decodeFromString<Device>(document.toJson())
                } ?: throw NoSuchElementException("Device identity is invalid")
        }
    }

    suspend fun getPrimaryDevice(userId: String): Result<Device> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.find(Filters.and(Filters.eq("userId", userId), Filters.eq("isPrimary", true)))
                .first()
                ?.let { document ->
                    Json.decodeFromString<Device>(document.toJson())
                } ?: throw NoSuchElementException("Primary device not found!")
        }
    }

    suspend fun getOnlineDevices(userId: String): Result<List<Device>> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.find(Filters.and(Filters.eq("userId", userId), Filters.eq("isOnline", true)))
                .map { document -> Device.fromDocument(document) }.toList()
        }
    }

    suspend fun update(id: String, item: Device): Result<Document?> =
        kotlin.runCatching {
            withContext(Dispatchers.IO) {
                collection.findOneAndUpdate(
                    Filters.eq("identityKey", id),
                    Document("\$set", item.toDocument()),
                    FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
                )
            }
        }

    suspend fun delete(id: String): Result<Document?> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.findOneAndDelete(Filters.eq("identityKey", id))
        }
    }

    suspend fun deleteOneTimePreKey(deviceId: String, oneTimePreKey: String): Result<Document?> = withContext(Dispatchers.IO) {
        kotlin.runCatching {
            collection.findOneAndUpdate(
                Filters.eq("identityKey", deviceId),
                Document("\$pull", Document("onetimePreKeys", oneTimePreKey)),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            )
        }
    }
}