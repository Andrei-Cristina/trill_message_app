package api.routing

import api.routing.routes.*
//import com.trill.message.api.websocket.routes.messageRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    //install(SSE)

    routing {
        authRoutes()
        deviceRoutes()
        userRoutes()
        messageRoutes()
        contentRoutes()
    }

}
