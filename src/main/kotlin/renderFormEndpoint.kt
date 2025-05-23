import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

suspend fun renderFormEndpoint(call: RoutingCall) {
    // Log the request
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    println("[$timestamp] ${call.request.httpMethod.value} ${call.request.path()}")

    // Check if form HTML file exists
    val formFile = File(FORM_HTML_PATH)
    if (!formFile.exists()) {
        call.respond(HttpStatusCode.InternalServerError, "Error: Form HTML file not found at $FORM_HTML_PATH")
        return
    }

    // Read the form HTML content
    var htmlContent = formFile.readText()

    // Try to read and process Root.kt file
    val rootKtFile = File(ROOT_KT_PATH)
    if (rootKtFile.exists()) {
        try {
            val rootKtContent = processRootKtContent(rootKtFile.readText())
            // Replace the default content in the textarea with the processed Root.kt content
            htmlContent = htmlContent.replace(
                "DATACLASSES",
                rootKtContent
            )
            println("Successfully loaded and processed Root.kt content")
        } catch (e: Exception) {
            println("Warning: Could not process Root.kt file: ${e.message}")
            // Continue with default content if Root.kt processing fails
        }
    } else {
        println("Warning: Root.kt file not found at $ROOT_KT_PATH - using default content")
    }

    call.respondText(htmlContent, ContentType.Text.Html)
}


fun processRootKtContent(rootKtContent: String): String {
    // Split the content into lines
    val lines = rootKtContent.lines()

    // Filter out import statements and @Serializable annotations
    val filteredLines = lines.filter { line ->
        val trimmedLine = line.trim()
        // Skip import statements
        !trimmedLine.startsWith("import ") &&
                // Skip @Serializable annotations
                !trimmedLine.startsWith("@Serializable") &&
                // Skip empty lines at the beginning/end
                !(trimmedLine.isEmpty() && (lines.indexOf(line) == 0 || lines.indexOf(line) == lines.size - 1))
    }

    // Join the filtered lines back together
    return filteredLines.joinToString("\n").trim()
}
