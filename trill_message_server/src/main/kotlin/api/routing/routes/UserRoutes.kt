package api.routing.routes

import data.models.User
import data.models.UserRegisterRequest
import data.repositories.DeviceRepository
import data.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val userRepository: UserRepository by application.inject()
    val deviceRepository: DeviceRepository by application.inject()

    route("/users") {
        get {
            val email = try {
                call.receive<String>()
            } catch (e: Exception) {
                call.application.environment.log.warn("Invalid request body for GET /users: {}", e.message)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing email in request body"))
                return@get
            }
            call.application.environment.log.info("Fetching user by email: {}", email)

            if (email.isBlank()) {
                call.application.environment.log.warn("Empty email provided for GET /users")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email cannot be empty"))
                return@get
            }

            userRepository.getByEmail(email).fold(
                onSuccess = { user ->
                    call.respond(HttpStatusCode.OK, user)
                    call.application.environment.log.info("Successfully fetched user with email: {}", email)
                },
                onFailure = { e ->
                    call.application.environment.log.warn("User not found for email: {}. Error: {}", email, e.message)
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            )
        }

        get("/search") {
            val nickname = try {
                call.receive<String>()
            } catch (e: Exception) {
                call.application.environment.log.warn("Invalid request body for GET /users/search: {}", e.message)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing nickname in request body"))
                return@get
            }
            call.application.environment.log.info("Searching users by nickname: {}", nickname)

            if (nickname.isBlank()) {
                call.application.environment.log.warn("Empty nickname provided for GET /users/search")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Nickname cannot be empty"))
                return@get
            }

            userRepository.getByNickName(nickname).fold(
                onSuccess = { users ->
                    call.respond(HttpStatusCode.OK, users)
                    call.application.environment.log.info("Found {} users with nickname: {}", users.size, nickname)
                },
                onFailure = { e ->
                    call.application.environment.log.warn("No users found for nickname: {}. Error: {}", nickname, e.message)
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "No users found"))
                }
            )
        }

        post {
            val userRequest = try {
                call.receive<UserRegisterRequest>()
            } catch (e: Exception) {
                call.application.environment.log.warn("Invalid request body for POST /users: {}", e.message)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing user data"))
                return@post
            }
            call.application.environment.log.info("Registering new user with email: {} and nickname: {}", userRequest.email, userRequest.nickname)

            if (userRequest.email.isBlank() || userRequest.nickname.isBlank()) {
                call.application.environment.log.warn("Invalid user data: email or nickname is empty")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email and nickname cannot be empty"))
                return@post
            }

            if (!userRequest.email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"))) {
                call.application.environment.log.warn("Invalid email format: {}", userRequest.email)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid email format"))
                return@post
            }

            userRepository.getByEmail(userRequest.email).fold(
                onSuccess = {
                    call.application.environment.log.warn("User already exists with email: {}", userRequest.email)
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "User with this email already exists"))
                    return@post
                },
                onFailure = {  }
            )

            userRepository.create(
                User(
                    email = userRequest.email,
                    nickname = userRequest.nickname,
                    isOnline = false,
                    lastOnline = toString()
                )
            ).fold(
                onSuccess = {
                    call.respond(HttpStatusCode.Created, mapOf("message" to "User created successfully"))
                    call.application.environment.log.info("Successfully created user with email: {}", userRequest.email)
                },
                onFailure = { e ->
                    call.application.environment.log.error("Failed to create user with email: {}. Error: {}", userRequest.email, e.message, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create user"))
                }
            )
        }
    }
}