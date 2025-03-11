package com.trill.message.api.routing.routes

import com.trill.message.data.models.Device
import com.trill.message.data.models.DeviceKeyBundle
import com.trill.message.data.models.DeviceRegistrationBundle
import com.trill.message.data.repositories.DeviceRepository
import com.trill.message.data.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import org.koin.ktor.ext.inject

fun Route.deviceRoutes() {
    val deviceRepository: DeviceRepository by inject()
    val userRepository: UserRepository by inject()

    route("/devices") {
        get("/all/keys") {
            val recipientEmail = call.receive<String>()

            val recipientId = userRepository.getIdByEmail(recipientEmail).fold(
                onSuccess = { it },
                onFailure = { e -> return@get call.respond(HttpStatusCode.NotFound, "${e.message}") }
            )

            deviceRepository.getAllDevices(recipientId).fold(
                onSuccess = { devices ->
                    val bundleList = mutableListOf<DeviceKeyBundle>()

                    devices.forEach {
                        val oneTimePreKey = it.onetimePreKeys.first()
                        deviceRepository.deleteOneTimePreKey(it.identityKey, oneTimePreKey)

                        bundleList.add(
                            DeviceKeyBundle(
                                it.identityKey,
                                it.signedPreKey,
                                it.preKeySignature,
                                oneTimePreKey
                            )
                        )
                    }

                    call.respond(HttpStatusCode.OK, bundleList)
                },
                onFailure = { e ->
                    call.application.environment.log.info(e.message ?: "Unknown error")
                }
            )
        }

        get("/keys") {
            val recipientEmail = call.receive<String>()

            val recipientId = userRepository.getIdByEmail(recipientEmail).fold(
                onSuccess = { it },
                onFailure = { e -> return@get call.respond(HttpStatusCode.NotFound, "${e.message}") }
            )

            deviceRepository.getOnlineDevices(recipientId).onSuccess { devices ->
                val device = devices.firstOrNull { it.onetimePreKeys.isNotEmpty() && it.isPrimary }
                    ?: devices.firstOrNull { it.onetimePreKeys.isNotEmpty() }

                device?.let {
                    val oneTimePreKey = it.onetimePreKeys.first()
                    deviceRepository.deleteOneTimePreKey(it.identityKey, oneTimePreKey)
                    call.respond(
                        HttpStatusCode.OK,
                        DeviceKeyBundle(
                            it.identityKey,
                            it.signedPreKey,
                            it.preKeySignature,
                            oneTimePreKey
                        )
                    )
                }
            }.onFailure { e ->
                call.application.environment.log.info(e.message ?: "Unknown error")
            }


            val primaryDevice = deviceRepository.getPrimaryDevice(recipientId)

            primaryDevice.fold(
                onSuccess = {
                    if (it.onetimePreKeys.isNotEmpty()) {
                        val oneTimePreKey = it.onetimePreKeys.first()

                        deviceRepository.deleteOneTimePreKey(it.identityKey, oneTimePreKey)

                        call.respond(
                            HttpStatusCode.OK,
                            DeviceKeyBundle(it.identityKey, it.signedPreKey, it.preKeySignature, oneTimePreKey)
                        )
                    }
                },
                onFailure = { e -> return@get call.respond(HttpStatusCode.NotFound, "${e.message}") }
            )
        }

        post {
            val registerBundle = call.receive<DeviceRegistrationBundle>()

            val userId = userRepository.getIdByEmail(registerBundle.userEmail).fold(
                onSuccess = { it },
                onFailure = { e -> return@post call.respond(HttpStatusCode.NotFound, "${e.message}") }
            )

            deviceRepository.create(
                Device(
                    userId,
                    registerBundle.identityKey,
                    registerBundle.signedPreKey,
                    registerBundle.preKeySignature,
                    registerBundle.onetimePreKeys,
                    isPrimary = false,
                    isOnline = false,
                    lastOnline = GMTDate()
                )
            ).fold(
                onSuccess = { id -> call.respond(HttpStatusCode.Created, mapOf("deviceId" to id)) },
                onFailure = { e ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Failed to create device: ${e.message}"
                    )
                }
            )
        }
    }
}