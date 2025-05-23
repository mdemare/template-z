import io.ktor.http.ContentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

suspend fun renderIndex(call: RoutingCall) {
    // Log the request
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    println("[$timestamp] ${call.request.httpMethod.value} ${call.request.path()}")

    // Check if forms directory exists
    val formsDir = File(FORMS_FOLDER)
    if (!formsDir.exists()) {
        call.respondText(generateIndexHtml(emptyList()), ContentType.Text.Html)
        return
    }

    // Get all JSON files in the forms directory
    val jsonFiles = formsDir.listFiles { file ->
        file.isFile && file.extension.lowercase() == "json"
    }?.map { it.name }?.sorted() ?: emptyList()

    val html = generateIndexHtml(jsonFiles)
    call.respondText(html, ContentType.Text.Html)
}
