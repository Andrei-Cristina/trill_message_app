package com.trill.message.api.routing

import com.trill.message.api.routing.routes.authRoutes
import com.trill.message.api.routing.routes.deviceRoutes
import com.trill.message.api.websocket.routes.messageRoutes
import com.trill.message.api.routing.routes.userRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*

fun Application.configureRouting() {
    install(SSE)

    routing {
        authRoutes()
        deviceRoutes()
        userRoutes()
        messageRoutes()
    }

}
