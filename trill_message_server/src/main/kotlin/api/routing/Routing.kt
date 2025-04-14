package api.routing

import api.routing.routes.authRoutes
import api.routing.routes.deviceRoutes
import api.routing.routes.messageRoutes
//import com.trill.message.api.websocket.routes.messageRoutes
import api.routing.routes.userRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    //install(SSE)

    routing {
        authRoutes()
        deviceRoutes()
        userRoutes()
        messageRoutes()
    }

}
