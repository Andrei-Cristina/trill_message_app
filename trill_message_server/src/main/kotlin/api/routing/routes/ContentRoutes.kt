package api.routing.routes

import data.models.UploadInfo
import data.models.UploadRequest
import data.models.UserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.util.*

fun Route.contentRoutes() {
    val uploadDir = File("storage").apply { mkdirs() }

    authenticate("auth-jwt") {
        route("/files") {

            post("/upload-request") {
                val principal = call.principal<UserPrincipal>()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val request = call.receive<UploadRequest>()
                val fileId = UUID.randomUUID().toString()

                call.application.log.info("User ${principal.userEmail} requested upload for file ${request.fileName} ($fileId)")

                val uploadUrl = "${application.environment.config.property("ktor.deployment.public_url").getString()}/files/storage/$fileId"

                call.respond(UploadInfo(fileId, uploadUrl))
            }

            put("/storage/{fileId}") {
                val fileId = call.parameters["fileId"] ?: return@put call.respond(HttpStatusCode.BadRequest)

                try {
                    val fileBytes = call.receive<ByteArray>()
                    val fileToSave = File(uploadDir, fileId)
                    fileToSave.writeBytes(fileBytes)
                    call.application.log.info("Successfully received and stored file $fileId (${fileBytes.size} bytes)")
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.application.log.error("Failed to store file $fileId", e)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            get("/download/{fileId}") {
                val principal = call.principal<UserPrincipal>()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                val fileId = call.parameters["fileId"] ?: return@get call.respond(HttpStatusCode.BadRequest)

                val fileToServe = File(uploadDir, fileId)
                if (fileToServe.exists()) {
                    call.respondFile(fileToServe)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}