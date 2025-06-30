package data

import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import io.ktor.server.application.*
import io.ktor.server.config.*


fun Application.connectToMongoDB(): MongoDatabase {
    val config = environment.config
    val uri = config.tryGetString("db.mongo.url") ?: run {
        val user = config.tryGetString("db.mongo.user")
        val password = config.tryGetString("db.mongo.password")
        val host = config.tryGetString("db.mongo.host") ?: "127.0.0.1"
        val port = config.tryGetString("db.mongo.port") ?: "27017"
        val maxPoolSize = config.tryGetString("db.mongo.maxPoolSize")?.toInt() ?: 20
        val credentials = user?.let { u -> password?.let { p -> "$u:$p@" } }.orEmpty()

        "mongodb://$credentials$host:$port/?maxPoolSize=$maxPoolSize&w=majority"
    }
    val databaseName = config.tryGetString("db.mongo.database.name") ?: "default"

    val mongoClient = MongoClients.create(uri)
    val database = mongoClient.getDatabase(databaseName)

    environment.monitor.subscribe(ApplicationStopped) {
        mongoClient.close()
    }

    return database
}