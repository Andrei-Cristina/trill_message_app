package api.routing.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import data.models.EmailSearchRequest
import data.models.LoginRequest
import data.models.User
import data.models.UserRegisterRequest
import data.repositories.DeviceRepository
import data.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import org.koin.ktor.ext.inject
import java.util.*

fun Route.userRoutes() {
    val userRepository: UserRepository by application.inject()
    val deviceRepository: DeviceRepository by application.inject()

    val jwtSecret = application.environment.config.property("jwt.secret").getString()
    val jwtIssuer = application.environment.config.property("jwt.issuer").getString()
    val jwtAudience = application.environment.config.property("jwt.audience").getString()
    val jwtExpirationTime = application.environment.config.property("jwt.expirationTime").getString().toLong()

    route("/login") {
        post {
            val loginRequest = try {
                call.receive<LoginRequest>()
            } catch (e: Exception) {
                call.application.environment.log.warn("Invalid login request: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid login data"))
                return@post
            }

            val userId = userRepository.getByEmail(loginRequest.email).fold(
                onSuccess = { it },
                onFailure = { e ->
                    call.application.environment.log.warn("Login failed for email: ${loginRequest.email}. Error: ${e.message}")
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid email"))
                    return@post
                }
            )

            call.application.environment.log.info("$loginRequest")

            val deviceId = deviceRepository.getById(loginRequest.identityKey).fold(
                onSuccess = { device -> device.identityKey },
                onFailure = { e ->
                    call.application.environment.log.info("Device not found for user ID: $userId, identityKey: ${loginRequest.identityKey}")
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Device not registered"))
                    return@post
                }
            )

            val token = JWT.create()
                .withAudience(jwtAudience)
                .withIssuer(jwtIssuer)
                .withClaim("userEmail", loginRequest.email)
                .withExpiresAt(Date(System.currentTimeMillis() + jwtExpirationTime))
                .sign(Algorithm.HMAC256(jwtSecret))

            call.application.environment.log.info("Successfully logged in user with email: ${loginRequest.email}, deviceId: $deviceId")
            call.respond(HttpStatusCode.OK, mapOf("token" to token, "deviceId" to deviceId))
        }
    }

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
                    call.application.environment.log.info("Successfully fetched user with email: {}", email)
                    call.respond(HttpStatusCode.OK, user)
                },
                onFailure = { e ->
                    call.application.environment.log.warn("User not found for email: {}. Error: {}", email, e.message)
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            )
        }

        post("/search") {
            val request = try {
                call.receive<EmailSearchRequest>()
            } catch (e: Exception) {
                call.application.environment.log.warn("Invalid request body for POST /search: {}", e.message)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or missing email in request body"))
                return@post
            }

            val email = request.email.trim()
            call.application.environment.log.info("Searching users by email: {}", email)

            if (email.isBlank()) {
                call.application.environment.log.warn("Empty email provided for POST /search")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email cannot be empty"))
                return@post
            }

            userRepository.getByEmail(email).fold(
                onSuccess = { user ->
                    call.application.environment.log.info("Found user for email: {}", email)
                    call.respond(HttpStatusCode.OK, user)
                },
                onFailure = { e ->
                    call.application.environment.log.error("Failed to search users: {}", e.message, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error searching users: ${e.message}"))
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
                    lastOnline = GMTDate().toString()
                )
            ).fold(
                onSuccess = {
                    call.application.environment.log.info("Successfully created user with email: {}", userRequest.email)
                    call.respond(HttpStatusCode.Created, mapOf("message" to "User created successfully"))
                },
                onFailure = { e ->
                    call.application.environment.log.error("Failed to create user with email: {}. Error: {}", userRequest.email, e.message, e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create user"))
                }
            )
        }
    }
}