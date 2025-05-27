package api.routing.routes

import com.trill.message.data.models.Device
import data.models.DeviceKeyBundle
import data.models.DeviceRegistrationBundle
import data.repositories.DeviceRepository
import data.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.date.*
import org.koin.ktor.ext.inject
import java.util.*

fun Route.deviceRoutes() {
    val deviceRepository: DeviceRepository by inject()
    val userRepository: UserRepository by inject()

    authenticate("auth-jwt") {
        route("/devices") {
            get("/all/keys") {
                val recipientEmail = try {
                    call.receive<String>()
                } catch (e: Exception) {
                    call.application.environment.log.warn("Invalid request body for /all/keys: {}", e.message)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid or missing email in request body")
                    )
                    return@get
                }
                call.application.environment.log.info("Fetching all device keys for email: {}", recipientEmail)

                val recipientId = userRepository.getIdByEmail(recipientEmail).fold(
                    onSuccess = { it },
                    onFailure = { e ->
                        call.application.environment.log.warn(
                            "User not found for email: {}. Error: {}",
                            recipientEmail,
                            e.message
                        )
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                        return@get
                    }
                )

                deviceRepository.getAllDevices(recipientEmail).fold(
                    onSuccess = getAllDevicesFold@{ devices ->
                        if (devices.isEmpty()) {
                            call.application.environment.log.warn("No devices found for user ID: {}", recipientId)
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "No devices found"))
                            return@getAllDevicesFold
                        }

                        call.application.environment.log.debug(
                            "Found {} devices for user ID: {}",
                            devices.size,
                            recipientId
                        )
                        val bundleList = mutableListOf<DeviceKeyBundle>()

                        devices.forEach { device ->
                            if (device.onetimePreKeys.isNotEmpty()) {
                                val oneTimePreKey = device.onetimePreKeys.first()

                                deviceRepository.deleteOneTimePreKey(device.identityKey, oneTimePreKey).fold(
                                    onSuccess = {
                                        bundleList.add(
                                            DeviceKeyBundle(
                                                device.identityKey,
                                                device.signedPreKey,
                                                device.preKeySignature,
                                                oneTimePreKey
                                            )
                                        )
                                    },
                                    onFailure = { e ->
                                        call.application.environment.log.error(
                                            "Failed to delete one-time pre-key for device: {}. Error: {}",
                                            device.identityKey,
                                            e.message,
                                            e
                                        )
                                        call.respond(
                                            HttpStatusCode.InternalServerError,
                                            mapOf("error" to "Failed to process device keys")
                                        )
                                        return@getAllDevicesFold
                                    }
                                )
                            } else {
                                call.application.environment.log.debug(
                                    "Skipping device {} with no one-time pre-keys",
                                    device.identityKey
                                )
                            }
                        }

                        if (bundleList.isEmpty()) {
                            call.application.environment.log.warn(
                                "No devices with one-time pre-keys found for user ID: {}",
                                recipientId
                            )
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "No devices with one-time pre-keys found")
                            )
                            return@getAllDevicesFold
                        }

                        call.application.environment.log.info(
                            "Successfully returned {} device key bundles for email: {}",
                            bundleList.size,
                            recipientEmail
                        )
                        call.application.environment.log.info("Returned device key bundles: {}", bundleList)
                        call.respond(HttpStatusCode.OK, bundleList)
                    },
                    onFailure = { e ->
                        call.application.environment.log.error(
                            "Failed to fetch devices for user ID: {}. Error: {}",
                            recipientId,
                            e.message,
                            e
                        )
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to fetch devices"))
                    }
                )
            }

            get("/keys") {
                val recipientEmail = try {
                    call.receive<String>()
                } catch (e: Exception) {
                    call.application.environment.log.warn("Invalid request body for /keys: {}", e.message)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid or missing email in request body")
                    )
                    return@get
                }
                call.application.environment.log.info("Fetching device keys for email: {}", recipientEmail)

                val recipientId = userRepository.getIdByEmail(recipientEmail).fold(
                    onSuccess = { it },
                    onFailure = { e ->
                        call.application.environment.log.warn(
                            "User not found for email: {}. Error: {}",
                            recipientEmail,
                            e.message
                        )
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                        return@get
                    }
                )

                deviceRepository.getOnlineDevices(recipientEmail).fold(
                    onSuccess = { devices ->
                        if (devices.isEmpty()) {
                            call.application.environment.log.warn(
                                "No online devices found for user ID: {}",
                                recipientId
                            )
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "No online devices found"))
                            return@fold
                        }

                        call.application.environment.log.info(
                            "Found {} online devices for user ID: {}",
                            devices.size,
                            recipientId
                        )
                        val device = devices.firstOrNull { it.onetimePreKeys.isNotEmpty() && it.isPrimary }
                            ?: devices.firstOrNull { it.onetimePreKeys.isNotEmpty() }

                        device?.let { d ->
                            val oneTimePreKey = d.onetimePreKeys.first()
                            call.respond(
                                HttpStatusCode.OK,
                                DeviceKeyBundle(
                                    d.identityKey,
                                    d.signedPreKey,
                                    d.preKeySignature,
                                    oneTimePreKey
                                )
                            )
                            call.application.environment.log.info(
                                "Returned key bundle for device with identity key: {} for email: {}",
                                d.identityKey,
                                recipientEmail
                            )

//                        deviceRepository.deleteOneTimePreKey(d.identityKey, oneTimePreKey).fold(
//                            onSuccess = {
//                                call.respond(
//                                    HttpStatusCode.OK,
//                                    DeviceKeyBundle(
//                                        d.identityKey,
//                                        d.signedPreKey,
//                                        d.preKeySignature,
//                                        oneTimePreKey
//                                    )
//                                )
//                                call.application.environment.log.info("Returned key bundle for device with identity key: {} for email: {}", d.identityKey, recipientEmail)
//                            },
//                            onFailure = { e ->
//                                call.application.environment.log.error("Failed to delete one-time pre-key for device: {}. Error: {}", d.identityKey, e.message, e)
//                                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to process device keys"))
//                            }
//                        )
                        } ?: run {
                            call.application.environment.log.warn(
                                "No suitable device found with one-time pre-keys for user ID: {}",
                                recipientId
                            )
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "No suitable device found"))
                        }
                    },
                    onFailure = { e ->
                        call.application.environment.log.error(
                            "Failed to fetch online devices for user ID: {}. Error: {}",
                            recipientId,
                            e.message,
                            e
                        )
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to fetch devices"))
                    }
                )
            }

            post {
                val registerBundle = try {
                    call.receive<DeviceRegistrationBundle>()
                } catch (e: Exception) {
                    call.application.environment.log.error("Invalid device registration request: {${e.message}}", e)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid or missing device registration data")
                    )
                    return@post
                }
                call.application.environment.log.info("Registering new device for email: {}", registerBundle.userEmail)


                if (registerBundle.userEmail.isBlank() || registerBundle.onetimePreKeys.isEmpty() ||
                    registerBundle.identityKey.size != 32 || registerBundle.signedPreKey.size != 32 ||
                    registerBundle.preKeySignature.size != 64 || registerBundle.onetimePreKeys.any { it.size != 32 }
                ) {
                    call.application.environment.log.warn(
                        "Invalid device registration data for email: {}",
                        registerBundle.userEmail
                    )
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing or invalid device registration fields")
                    )
                    return@post
                }

                val userId = userRepository.getIdByEmail(registerBundle.userEmail).fold(
                    onSuccess = { it },
                    onFailure = { e ->
                        call.application.environment.log.warn(
                            "User not found for email: {}. Error: {}",
                            registerBundle.userEmail,
                            e.message
                        )
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                        return@post
                    }
                )

                call.application.environment.log.info(
                    "Creating device with identity key {}",
                    registerBundle.identityKey.toString()
                )

                val deviceId = Base64.getEncoder().encodeToString(registerBundle.identityKey)
                deviceRepository.create(
                    Device(
                        userId = registerBundle.userEmail,
                        identityKey = deviceId,
                        signedPreKey = Base64.getEncoder().encodeToString(registerBundle.signedPreKey),
                        preKeySignature = Base64.getEncoder().encodeToString(registerBundle.preKeySignature),
                        onetimePreKeys = registerBundle.onetimePreKeys.map { Base64.getEncoder().encodeToString(it) },
                        isPrimary = false,
                        isOnline = false,
                        lastOnline = GMTDate().toString()
                    )
                ).fold(
                    onSuccess = { id ->
                        call.application.environment.log.info(
                            "Successfully created device with ID: {} for user ID: {}",
                            id,
                            userId
                        )
                        call.respond(HttpStatusCode.Created, mapOf("deviceId" to id))
                    },
                    onFailure = { e ->
                        call.application.environment.log.error(
                            "Failed to create device for user ID: {}. Error: {}",
                            userId,
                            e.message,
                            e
                        )
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create device"))
                    }
                )
            }
        }
    }
}
