package nl.mdemare

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.formUrlEncode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.jsoup.nodes.Document
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Hardcoded path to the HTML file
const val HTML_FILE_PATH = "/Users/mdemare/iCloud/proj/template-z/game.html"

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

                val (statusCode, errorMessage: String?, doc: Document?) = parseHtml(jsonParam, htmlFile)
                if (doc == null) {
                    call.respond(statusCode, errorMessage!!)
                } else {
                    call.respondText(doc.outerHtml(), ContentType.Text.Html)
                }
            }

            // Add a simple status endpoint
            get("/") {
                call.respond(HttpStatusCode.OK, "Server is running")
            }
        }
    }.start(wait = true)
}
