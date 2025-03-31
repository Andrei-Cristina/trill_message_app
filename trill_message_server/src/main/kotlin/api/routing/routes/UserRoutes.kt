package com.trill.message.api.routing.routes

import com.trill.message.data.models.User
import com.trill.message.data.models.UserRegisterRequest
import com.trill.message.data.repositories.DeviceRepository
import com.trill.message.data.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val userRepository: UserRepository by inject()
    val deviceRepository: DeviceRepository by inject()

    route("/users") {
        get {
            val email = call.receive<String>()

            userRepository.getByEmail(email).fold(
                onSuccess = { user -> call.respond(HttpStatusCode.OK, user) },
                onFailure = { e -> call.respond(HttpStatusCode.NotFound, "User not found: ${e.message}") }
            )
        }

        get("/search") {
            val nickname = call.receive<String>()

            userRepository.getByNickName(nickname).fold(
                onSuccess = { users -> call.respond(HttpStatusCode.OK, users) },
                onFailure = { e -> call.respond(HttpStatusCode.NotFound, "User not found: ${e.message}") }
            )
        }

        post {
            val user = call.receive<UserRegisterRequest>()

            userRepository.create(User(user.email, user.nickname, isOnline = false, lastOnline = GMTDate().toString())).fold(
                onSuccess = { call.respond(HttpStatusCode.Created, "Successful") },
                onFailure = { e ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Failed to create user: ${e.message}"
                    )
                }
            )
        }
    }
}