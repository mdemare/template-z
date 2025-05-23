package nl.mdemare

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.formUrlEncode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Hardcoded path to the HTML file
const val HTML_FILE_PATH = "/Users/mdemare/iCloud/proj/template-z/game.html"
const val FORM_HTML_PATH = "/Users/mdemare/iCloud/proj/template-z/form.html"
const val FORMS_FOLDER = "/Users/mdemare/iCloud/proj/template-z/forms"

fun main(args: Array<String>) {
    println("Running basic server...")
    runBasicServer()
}

fun runBasicServer() {
    embeddedServer(Netty, port = 3333) {
        routing {
            get("/render") {
                // Log the request
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val queryString = call.request.queryParameters.formUrlEncode().let {
                    if (it.isNotEmpty()) "?$it" else ""
                }
                println("[$timestamp] ${call.request.httpMethod.value} ${call.request.path()}$queryString")

                // Get the JSON parameter
                val jsonParam = call.request.queryParameters["json"]
                if (jsonParam == null) {
                    call.respond(HttpStatusCode.BadRequest, "Error: Missing 'json' query parameter")
                    return@get
                }

                // Check if HTML file exists
                val htmlFile = File(HTML_FILE_PATH)
                if (!htmlFile.exists()) {
                    call.respond(HttpStatusCode.InternalServerError, "Error: HTML file not found at $HTML_FILE_PATH")
                    return@get
                }
                val root = Json.decodeFromString<Root>(jsonParam)
                val html = renderHtml(htmlFile, root)
                call.respondText(html, ContentType.Text.Html)
            }

            get("/form") {
                // Log the request
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                println("[$timestamp] ${call.request.httpMethod.value} ${call.request.path()}")

                // Check if form HTML file exists
                val formFile = File(FORM_HTML_PATH)
                if (!formFile.exists()) {
                    call.respond(HttpStatusCode.InternalServerError, "Error: Form HTML file not found at $FORM_HTML_PATH")
                    return@get
                }

                // Serve the form HTML file
                val htmlContent = formFile.readText()
                call.respondText(htmlContent, ContentType.Text.Html)
            }

            post("/save") {
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
                        return@post
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

            // Add a simple status endpoint
            get("/") {
                call.respond(HttpStatusCode.OK, "Server is running")
            }
        }
    }.start(wait = true)
}

fun generateRandomId(): String {
    val chars = "0123456789abcdef"
    return (1..8).map { chars.random() }.joinToString("")
}
