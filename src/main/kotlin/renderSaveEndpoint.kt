import io.ktor.http.HttpStatusCode
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

suspend fun renderSaveEndpoint(call: RoutingCall) {
    // Log the request
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    println("[$timestamp] ${call.request.httpMethod.value} ${call.request.path()}")

    try {
        // Ensure forms directory exists
        val formsDir = File(FORMS_FOLDER)
        if (!formsDir.exists()) {
            formsDir.mkdirs()
            println("Created forms directory: $FORMS_FOLDER")
        }

        // Receive JSON from request body
        val jsonContent = call.receiveText()

        // Validate JSON format
        try {
            Json.parseToJsonElement(jsonContent) // Just validate, don't store parsed result
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Error: Invalid JSON format - ${e.message}")
            return
        }

        // Generate random filename
        val randomId = generateRandomId()
        val filename = "$randomId.json"
        val file = File(formsDir, filename)

        // Write JSON to file
        file.writeText(jsonContent)

        println("Saved form data to: ${file.absolutePath}")
        call.respond(HttpStatusCode.OK, "Form data saved successfully as $filename")

    } catch (e: Exception) {
        println("Error saving form data: ${e.message}")
        call.respond(HttpStatusCode.InternalServerError, "Error saving form data: ${e.message}")
    }
}
