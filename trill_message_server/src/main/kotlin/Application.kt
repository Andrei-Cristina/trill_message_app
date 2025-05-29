import api.routing.configureRouting
import api.websocket.WebSocketHandler
import api.websocket.configureSockets
import api.websocket.routes.configureWebSocketRouting
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import data.connectToMongoDB
import data.models.UserPrincipal
import data.repositories.DeviceRepository
import data.repositories.MessageRepository
import data.repositories.UserRepository
import io.ktor.http.*
import io.ktor.http.auth.*
import utils.AuthUtils
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureAdministration()
    configureSerialization()
    //configureMonitoring()

    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val myRealm = environment.config.property("jwt.realm").getString()


    install(Authentication) {
        jwt("auth-jwt") {
            realm = myRealm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .build()
            )
            authHeader { call ->
                call.request.queryParameters["token"]?.let { tokenFromQuery ->
                    HttpAuthHeader.Single("Bearer", tokenFromQuery)
                } ?: call.request.parseAuthorizationHeader()
            }
            validate { credential ->
                credential.payload.getClaim("userEmail").asString()?.let { userEmail ->
                    UserPrincipal(userEmail)
                }
            }
            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }

    install(Koin) {
        slf4jLogger()
        modules(
            module {
                single {
                    connectToMongoDB()
                }

                single {
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = true
                    }
                }

                single<UserRepository> {
                    UserRepository()
                }
                single<DeviceRepository> {
                    DeviceRepository()
                }
                single<AuthUtils> {
                    AuthUtils()
                }
                single<MessageRepository> {
                    MessageRepository()
                }
                single<WebSocketHandler> {
                    WebSocketHandler()
                }
            }
        )
    }

    configureSockets()
    configureWebSocketRouting()
    configureRouting()
}
