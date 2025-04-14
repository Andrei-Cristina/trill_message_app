package api.routing.routes

import data.models.AuthRequest
import data.models.AuthVerifyRequest
import data.repositories.UserRepository
import utils.AuthUtils
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject


fun Route.authRoutes(){
    val userRepository: UserRepository by inject()
    val authUtils: AuthUtils by inject()
    //TODO: implement proper jwt auth
    route("/auth") {
        post("/request-otp") {
            val authRequest = call.receive<AuthRequest>()
            call.application.environment.log.info("Received OTP request for email: {}", authRequest.email)

            userRepository.getByEmail(authRequest.email).fold(
                onSuccess = {
                    call.application.environment.log.debug("User found for email: {}", authRequest.email)
                    authUtils.sendOtp(authRequest.email)

                    call.respond(HttpStatusCode.OK, "OTP sent to ${authRequest.email}")
                    call.application.environment.log.info("OTP successfully sent to: {}", authRequest.email)
                },
                onFailure = { e ->
                    call.application.environment.log.warn("User not found for email: {}. Error: {}", authRequest.email, e.message)
                    call.respond(HttpStatusCode.NotFound, "${e.message}")
                }
            )
        }

        post("/verify-otp") {
            val request = call.receive<AuthVerifyRequest>()
            call.application.environment.log.info("Received OTP verification request for email: {}", request.email)

            try {
                if (authUtils.verifyOtp(request.email, request.otpCode)) {
                    authUtils.deleteOtp(request.email)
                    call.respond(HttpStatusCode.OK, "OTP verified successfully")
                    call.application.environment.log.info("OTP verified successfully for email: {}", request.email)
                } else {
                    authUtils.deleteOtp(request.email)
                    call.respond(HttpStatusCode.Unauthorized, "Invalid OTP")
                    call.application.environment.log.warn("Invalid OTP provided for email: {}", request.email)
                }
            } catch (e: Exception) {
                call.application.environment.log.error("OTP verification failed for email: {}. Error: {}", request.email, e.message, e)
                call.respond(HttpStatusCode.InternalServerError, "Verification failed: ${e.message}")
            }
        }
    }
}