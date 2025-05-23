import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.http.content.*
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Hardcoded path to the HTML file
const val HTML_FILE_PATH = "/Users/mdemare/iCloud/proj/template-z/pandemic.html"
const val FORM_HTML_PATH = "/Users/mdemare/iCloud/proj/template-z/form.html"
const val FORMS_FOLDER = "/Users/mdemare/iCloud/proj/template-z/forms"
const val PUBLIC_FOLDER = "/Users/mdemare/projects/contract_to_cure/public"
const val ROOT_KT_PATH = "src/main/kotlin/Root.kt"

fun main(args: Array<String>) {
    println("Running basic server...")
    runBasicServer()
}

fun runBasicServer() {
    embeddedServer(Netty, port = 3333) {
        routing {
            staticFiles("/", File(PUBLIC_FOLDER))

            post("/render") {
                renderEndpoint(call)
            }

            get("/form") {
                renderFormEndpoint(call)
            }

            get("/index") {
                renderIndex(call)
            }

            get("/forms/{filename}") {
                renderGetFormEndpoint(call)
            }

            post("/save") {
                renderSaveEndpoint(call)
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
