package api.routing.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import data.models.AuthRequest
import data.models.AuthVerifyRequest
import data.models.RefreshTokenRequest
import data.models.UserPrincipal
import data.repositories.DeviceRepository
import data.repositories.UserRepository
import utils.AuthUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.*


fun Route.authRoutes(){
    val userRepository: UserRepository by inject()
    val authUtils: AuthUtils by inject()
    val deviceRepository: DeviceRepository by inject()
    val jwtSecret = application.environment.config.property("jwt.secret").getString()
    val jwtIssuer = application.environment.config.property("jwt.issuer").getString()
    val jwtAudience = application.environment.config.property("jwt.audience").getString()
    val jwtExpirationTime = application.environment.config.property("jwt.expirationTime").getString().toLong()

    route("/auth") {

        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()

            deviceRepository.findByRefreshToken(request.refreshToken).fold(
                onSuccess = { device ->
                    if (device.refreshTokenExpiresAt == null || device.refreshTokenExpiresAt < System.currentTimeMillis()) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Refresh token expired"))
                        return@post
                    }

                    val newAccessToken = JWT.create()
                        .withAudience(jwtAudience)
                        .withIssuer(jwtIssuer)
                        .withClaim("userEmail", device.userId)
                        .withExpiresAt(Date(System.currentTimeMillis() + jwtExpirationTime))
                        .sign(Algorithm.HMAC256(jwtSecret))

                    val newRefreshToken = AuthUtils.generateRefreshToken(device.userId, device.identityKey)
                    val newRefreshTokenExpiresAt = System.currentTimeMillis() + 7*24*60*60*1000

                    deviceRepository.updateRefreshToken(device.identityKey, newRefreshToken, newRefreshTokenExpiresAt)

                    call.respond(HttpStatusCode.OK, mapOf(
                        "accessToken" to newAccessToken,
                        "refreshToken" to newRefreshToken
                    ))
                },
                onFailure = {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid refresh token"))
                }
            )
        }

        authenticate("auth-jwt") {
            post("/logout") {
                val principal = call.principal<UserPrincipal>()!!
                val deviceId = call.request.queryParameters["deviceId"]
                if (deviceId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Device ID is required")
                    return@post
                }

                deviceRepository.clearRefreshToken(deviceId).fold(
                    onSuccess = { call.respond(HttpStatusCode.NoContent, "Logged out successfully") },
                    onFailure = { call.respond(HttpStatusCode.InternalServerError, "Logout failed") }
                )
            }
        }
    }
}