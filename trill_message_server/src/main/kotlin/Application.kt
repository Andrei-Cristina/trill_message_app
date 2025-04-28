import api.routing.configureRouting
import api.websocket.configureSockets
import data.connectToMongoDB
import data.repositories.DeviceRepository
import data.repositories.MessageRepository
import data.repositories.UserRepository
import utils.AuthUtils
import io.ktor.server.application.*
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

    install(Koin) {
        slf4jLogger()
        modules(
            module {
                single {
                    connectToMongoDB()
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
            }
        )
    }

    configureSockets()
    configureRouting()
}
