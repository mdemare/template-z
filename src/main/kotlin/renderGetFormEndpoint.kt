import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.io.File

suspend fun renderGetFormEndpoint(call: RoutingCall) {
    val filename = call.parameters["filename"]

    if (filename == null) {
        call.respond(HttpStatusCode.BadRequest, "Error: Missing filename parameter")
        return
    }

    val formsDir = File(FORMS_FOLDER)
    val file = if (filename == "latest") {
        // Find the file with the latest modification date
        val files = formsDir.listFiles { file -> file.isFile }
        if (files.isNullOrEmpty()) {
            call.respond(HttpStatusCode.NotFound, "Error: No files found in forms directory")
            return
        }
        files.maxByOrNull { it.lastModified() }!!
    } else {
        // Validate filename to prevent directory traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            call.respond(HttpStatusCode.BadRequest, "Error: Invalid filename")
            return
        }
        File(formsDir, filename)
    }

    if (!file.exists() || !file.isFile) {
        call.respond(HttpStatusCode.NotFound, "Error: File not found")
        return
    }

    try {
        val jsonContent = file.readText()
        // Validate it's valid JSON
        Json.parseToJsonElement(jsonContent)
        call.respondText(jsonContent, ContentType.Application.Json)
    } catch (e: Exception) {
        call.respond(HttpStatusCode.InternalServerError, "Error reading file: ${e.message}")
    }
}
