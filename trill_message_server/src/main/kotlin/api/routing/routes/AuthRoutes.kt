package com.trill.message.api.routing.routes

import com.trill.message.data.models.AuthRequest
import com.trill.message.data.models.AuthVerifyRequest
import com.trill.message.data.repositories.UserRepository
import com.trill.message.utils.AuthUtils
import io.ktor.http.*
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

            userRepository.getByEmail(authRequest.email).fold(
                onSuccess = {
                    authUtils.sendOtp(authRequest.email)

                    call.respond(HttpStatusCode.OK, "OTP sent to ${authRequest.email}")
                },
                onFailure = { e -> call.respond(HttpStatusCode.NotFound, "${e.message}") }
            )
        }

        post("/verify-otp") {
            val request = call.receive<AuthVerifyRequest>()

            try {
                if (authUtils.verifyOtp(request.email, request.otpCode)) {
                    authUtils.deleteOtp(request.email)

                    call.respond(HttpStatusCode.OK, "OTP verified successfully")
                } else {
                    authUtils.deleteOtp(request.email)

                    call.respond(HttpStatusCode.Unauthorized, "Invalid OTP")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Verification failed: ${e.message}")
            }
        }
    }
}