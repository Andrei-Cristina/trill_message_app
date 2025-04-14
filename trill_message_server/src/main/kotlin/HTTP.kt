import com.mongodb.client.*
import com.ucasoft.ktor.simpleCache.SimpleCache
import com.ucasoft.ktor.simpleCache.cacheOutput
import com.ucasoft.ktor.simpleRedisCache.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import org.slf4j.event.*

fun Application.configureHTTP() {
    routing {
        openAPI(path = "openapi")
    }
    install(SimpleCache) {
        redisCache {
            invalidateAt = 10.seconds
            host = "localhost"
            port = 6379
        }
    }
    routing {
        swaggerUI(path = "openapi")
    }
/*
    routing {
        cacheOutput(2.seconds) {
            get("/short") {
                call.respond(Random.nextInt().toString())
            }
        }
        cacheOutput {
            get("/default") {
                call.respond(Random.nextInt().toString())
            }
        }
    }

 */
}
