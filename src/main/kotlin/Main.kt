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
const val HTML_FILE_PATH = "/Users/mdemare/iCloud/proj/template-z/game.html"
const val FORM_HTML_PATH = "/Users/mdemare/iCloud/proj/template-z/form.html"
const val FORMS_FOLDER = "/Users/mdemare/iCloud/proj/template-z/forms"
const val PUBLIC_FOLDER = "/Users/mdemare/iCloud/proj/template-z/public"

fun main(args: Array<String>) {
    println("Running basic server...")
    runBasicServer()
}

fun runBasicServer() {
    embeddedServer(Netty, port = 3333) {
        routing {
            staticFiles("/public", File(PUBLIC_FOLDER))

            post("/render") {
                // Log the request
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                println("[$timestamp] ${call.request.httpMethod.value} ${call.request.path()}")

                try {
                    val jsonContent = if (call.request.contentType().match(ContentType.Application.FormUrlEncoded)) {
                        // Handle form data
                        val parameters = call.receiveParameters()
                        parameters["jsonData"] ?: run {
                            call.respond(HttpStatusCode.BadRequest, "Error: Missing jsonData parameter in form")
                            return@post
                        }
                    } else {
                        // Handle raw JSON in request body (existing behavior)
                        call.receiveText()
                    }

                    if (jsonContent.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, "Error: Missing JSON data")
                        return@post
                    }

                    // Check if HTML file exists
                    val htmlFile = File(HTML_FILE_PATH)
                    if (!htmlFile.exists()) {
                        call.respond(HttpStatusCode.InternalServerError, "Error: HTML file not found at $HTML_FILE_PATH")
                        return@post
                    }

                    // Parse and render
                    val root = Json.decodeFromString<Root>(jsonContent)
                    val html = renderHtml(htmlFile, root)
                    call.respondText(html, ContentType.Text.Html)

                } catch (e: Exception) {
                    println("Error rendering: ${e.message}")
                    call.respond(HttpStatusCode.BadRequest, "Error: Invalid JSON format - ${e.message}")
                }
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

            get("/index") {
                // Log the request
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                println("[$timestamp] ${call.request.httpMethod.value} ${call.request.path()}")

                // Check if forms directory exists
                val formsDir = File(FORMS_FOLDER)
                if (!formsDir.exists()) {
                    call.respondText(generateIndexHtml(emptyList()), ContentType.Text.Html)
                    return@get
                }

                // Get all JSON files in the forms directory
                val jsonFiles = formsDir.listFiles { file ->
                    file.isFile && file.extension.lowercase() == "json"
                }?.map { it.name }?.sorted() ?: emptyList()

                val html = generateIndexHtml(jsonFiles)
                call.respondText(html, ContentType.Text.Html)
            }

            get("/forms/{filename}") {
                // Log the request
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val filename = call.parameters["filename"]
                println("[$timestamp] ${call.request.httpMethod.value} ${call.request.path()}")

                if (filename == null) {
                    call.respond(HttpStatusCode.BadRequest, "Error: Missing filename parameter")
                    return@get
                }

                // Validate filename to prevent directory traversal
                if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                    call.respond(HttpStatusCode.BadRequest, "Error: Invalid filename")
                    return@get
                }

                val formsDir = File(FORMS_FOLDER)
                val file = File(formsDir, filename)

                if (!file.exists() || !file.isFile) {
                    call.respond(HttpStatusCode.NotFound, "Error: File not found")
                    return@get
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

fun generateIndexHtml(jsonFiles: List<String>): String {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Saved Forms</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    max-width: 800px;
                    margin: 0 auto;
                    padding: 20px;
                    background-color: #f5f5f5;
                }
                .container {
                    background: white;
                    padding: 30px;
                    border-radius: 8px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                }
                h1 {
                    color: #333;
                    text-align: center;
                    margin-bottom: 30px;
                }
                .file-list {
                    list-style: none;
                    padding: 0;
                }
                .file-item {
                    margin: 10px 0;
                    padding: 15px;
                    background: #f8f9fa;
                    border: 1px solid #e9ecef;
                    border-radius: 4px;
                    cursor: pointer;
                    transition: background-color 0.2s;
                }
                .file-item:hover {
                    background: #e9ecef;
                }
                .file-item:active {
                    background: #dee2e6;
                }
                .no-files {
                    text-align: center;
                    color: #666;
                    font-style: italic;
                    padding: 40px;
                }
                .loading {
                    display: none;
                    text-align: center;
                    color: #007bff;
                    margin-top: 20px;
                }
                .error {
                    color: #dc3545;
                    text-align: center;
                    margin-top: 20px;
                    padding: 10px;
                    background: #f8d7da;
                    border: 1px solid #f5c6cb;
                    border-radius: 4px;
                }
                #renderForm {
                    display: none;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>Saved Forms</h1>
                ${if (jsonFiles.isEmpty()) {
        "<div class=\"no-files\">No forms found in the forms directory.</div>"
    } else {
        "<ul class=\"file-list\">" +
                jsonFiles.joinToString("") { filename ->
                    "<li class=\"file-item\" onclick=\"loadAndRenderForm('$filename')\">$filename</li>"
                } +
                "</ul>"
    }}
                <div class="loading" id="loading">Loading form data...</div>
                <div class="error" id="error" style="display: none;"></div>
            </div>

            <!-- Hidden form for POST to /render -->
            <form id="renderForm" action="/render" method="POST" target="_self">
                <input type="hidden" name="jsonData" id="jsonDataInput">
            </form>

            <script>
                async function loadAndRenderForm(filename) {
                    const loadingEl = document.getElementById('loading');
                    const errorEl = document.getElementById('error');
                    const renderForm = document.getElementById('renderForm');
                    const jsonDataInput = document.getElementById('jsonDataInput');
                    
                    // Show loading, hide error
                    loadingEl.style.display = 'block';
                    errorEl.style.display = 'none';
                    
                    try {
                        // Fetch the JSON file
                        const response = await fetch('/forms/' + filename);
                        if (!response.ok) {
                            throw new Error('Failed to load form: ' + response.statusText);
                        }
                        
                        const jsonData = await response.text();
                        
                        // Set the JSON data in the hidden input and submit the form
                        jsonDataInput.value = jsonData;
                        renderForm.submit();
                        
                    } catch (error) {
                        console.error('Error:', error);
                        errorEl.textContent = 'Error: ' + error.message;
                        errorEl.style.display = 'block';
                        loadingEl.style.display = 'none';
                    }
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}

fun generateRandomId(): String {
    val chars = "0123456789abcdef"
    return (1..8).map { chars.random() }.joinToString("")
}
