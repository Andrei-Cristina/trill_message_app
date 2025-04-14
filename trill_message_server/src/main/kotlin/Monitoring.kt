//package com.trill.message
//
//import com.mongodb.client.*
//import com.ucasoft.ktor.simpleCache.SimpleCache
//import com.ucasoft.ktor.simpleCache.cacheOutput
//import com.ucasoft.ktor.simpleRedisCache.*
//import io.ktor.http.*
//import io.ktor.serialization.kotlinx.json.*
//import io.ktor.server.application.*
//import io.ktor.server.config.*
//import io.ktor.server.plugins.calllogging.*
//import io.ktor.server.plugins.contentnegotiation.*
//import io.ktor.server.plugins.openapi.*
//import io.ktor.server.plugins.swagger.*
//import io.ktor.server.request.*
//import io.ktor.server.response.*
//import io.ktor.server.routing.*
//import io.ktor.server.websocket.*
//import io.ktor.websocket.*
//import java.sql.Connection
//import java.sql.DriverManager
//import java.time.Duration
//import kotlin.random.Random
//import kotlin.time.Duration.Companion.seconds
//import org.slf4j.event.*
//
//fun Application.configureMonitoring() {
//    install(CallLogging) {
//        level = Level.INFO
//        filter { call -> call.request.path().startsWith("/") }
//    }
//}
