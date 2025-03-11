package com.trill.message

import com.trill.message.api.routing.configureRouting
import com.trill.message.api.websocket.configureSockets
import com.trill.message.data.connectToMongoDB
import com.trill.message.data.repositories.DeviceRepository
import com.trill.message.data.repositories.UserRepository
import com.trill.message.utils.AuthUtils
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

    install(Koin) {
        slf4jLogger()
        modules(
            module{
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
            }
        )
    }

    configureRouting()
    configureSockets()
}
