import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.text.isBlank

suspend fun renderEndpoint(call: RoutingCall) {
    // Log the request
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    println("[$timestamp] ${call.request.httpMethod.value} ${call.request.path()}")

    try {
        val jsonContent = if (call.request.contentType().match(ContentType.Application.FormUrlEncoded)) {
            // Handle form data
            val parameters = call.receiveParameters()
            parameters["jsonData"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Error: Missing jsonData parameter in form")
                return
            }
        } else {
            // Handle raw JSON in request body (existing behavior)
            call.receiveText()
        }

        if (jsonContent.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Error: Missing JSON data")
            return
        }

        // Check if HTML file exists
        val htmlFile = File(HTML_FILE_PATH)
        if (!htmlFile.exists()) {
            call.respond(HttpStatusCode.InternalServerError, "Error: HTML file not found at $HTML_FILE_PATH")
            return
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

fun renderHtml(htmlFile: File, rootData: Any): String {
    // Parse the HTML file
    val doc: Document = Jsoup.parse(htmlFile, "UTF-8")

    // Create a context map to track current data objects
    val contextStack = mutableListOf<Pair<String, Any>>()
    contextStack.add("root" to rootData)

    // Start processing from the document body
    processElementForRendering(doc.body(), contextStack)

    return doc.html()
}

private fun processElementForRendering(element: Element?, contextStack: MutableList<Pair<String, Any>>) {
    if (element == null) return

    val componentName = element.attr("data-component")
    val textContentAttr = element.attr("data-text-content")
    val collectionAttr = element.attr("data-collection")

    // Handle data-text-content attribute first
    if (textContentAttr.isNotEmpty()) {
        println("Found text content attribute: $textContentAttr on element: ${element.tagName()}")
        val textValue = resolveExpression(textContentAttr, contextStack)
        element.text(textValue)
    }

    // Handle data-attribute- prefixed attributes
    val attributesToProcess = element.attributes().asList()
        .filter { it.key.startsWith("data-attribute-") }

    for (attr in attributesToProcess) {
        val attributeName = attr.key.removePrefix("data-attribute-")
        val value = attr.value

        println("Processing data-attribute: $attributeName with value: $value")

        val expression = extractExpression(value)
        if (expression != value) {
            val variableValue = resolveExpression(expression, contextStack)
            val finalValue = applyTemplate(value, variableValue)
            element.attr(attributeName, finalValue)
            println("Set attribute $attributeName to: $finalValue")
        } else {
            // No expression found, use value as-is
            element.attr(attributeName, value)
            println("Set attribute $attributeName to literal value: $value")
        }
    }

    // Handle data-collection (check this before data-component)
    if (collectionAttr.isNotEmpty()) {
        handleCollectionRendering(element, collectionAttr, contextStack)
        return // Don't process as regular element after handling collection
    }

    // Handle data-component
    if (componentName.isNotEmpty()) {
        // Find matching data object from context
        val dataObject = findDataObjectForComponent(componentName, contextStack)
        if (dataObject != null) {
            // Push this component's data onto the context stack
            contextStack.add(componentName to dataObject)

            // Process children with this new context
            processChildrenForRendering(element, contextStack)

            // Pop the context when done
            contextStack.removeAt(contextStack.size - 1)
        } else {
            // Still process children even if no data object found
            processChildrenForRendering(element, contextStack)
        }
    } else {
        // Regular element, just process children
        processChildrenForRendering(element, contextStack)
    }
}

private fun processChildrenForRendering(element: Element, contextStack: MutableList<Pair<String, Any>>) {
    for (child in element.children()) {
        processElementForRendering(child, contextStack)
    }
}

private fun handleCollectionRendering(element: Element, collectionAttr: String, contextStack: MutableList<Pair<String, Any>>) {
    println("Processing collection: $collectionAttr")
    println("Current context stack: ${contextStack.map { it.first }}")

    // Find the collection data
    val collectionData = resolveCollectionData(collectionAttr, contextStack)
    println("Collection data found: ${collectionData != null}, type: ${collectionData?.javaClass?.simpleName}")

    if (collectionData is List<*>) {
        println("Collection size: ${collectionData.size}")

        // Find the template element (first child with data-collection-item)
        val templateElement = element.children().firstOrNull { it.hasAttr("data-collection-item") }
        element.children().clear()

        if (templateElement != null) {
            val collectionItemName = templateElement.attr("data-collection-item")
            println("Template found for collection item: $collectionItemName")

            // Remove the template element from DOM
            templateElement.remove()

            // Track number of elements created
            var elementsCreated = 0

            // Create elements for each item in the collection
            collectionData.forEachIndexed { index, item ->
                if (item != null) {
                    println("Processing collection item $index: ${item.javaClass.simpleName}")

                    // Clone the template element
                    val clonedElement = templateElement.clone()

                    // Add the item to context stack
                    contextStack.add(collectionItemName to item)
                    println("Added to context: $collectionItemName -> ${item.javaClass.simpleName}")

                    // Process the cloned element with the item data
                    processElementForRendering(clonedElement, contextStack)

                    // Remove the item from context stack
                    contextStack.removeAt(contextStack.size - 1)

                    // Add the processed element to the parent
                    element.appendChild(clonedElement)

                    elementsCreated++
                }
            }

            // Assert that the number of elements created matches the collection size
            val nrItems = collectionData.size
            assert(element.children().size == nrItems) {
                "Mismatch: Created $elementsCreated elements but expected $nrItems (items in collection of size ${collectionData.size})"
            }

        } else {
            println("No template element found with data-collection-item attribute")
        }
    } else {
        println("Collection data is not a List or is null")
    }

    // Process other children that are not collection items
    for (child in element.children()) {
        if (!child.hasAttr("data-collection-item")) {
            processElementForRendering(child, contextStack)
        }
    }
}

private fun findDataObjectForComponent(componentName: String, contextStack: List<Pair<String, Any>>): Any? {
    // Look for the component in the current context stack
    for ((name, obj) in contextStack.reversed()) {
        // Check if this object has a property matching the component name
        val property = getPropertyValue(obj, componentName)
        if (property != null) {
            return property
        }
    }
    return null
}

private fun resolveExpression(expression: String, contextStack: List<Pair<String, Any>>): String {
    println("Resolving expression: $expression")
    println("Current context: ${contextStack.map { "${it.first}:${it.second.javaClass.simpleName}" }}")

    // Parse attribute value - "objectName.propertyName"
    val parts = expression.split(".", limit = 2)

    if (parts.size == 2) {
        // Format: "objectName.propertyName"
        val objectName = parts[0].trim()
        val propertyName = parts[1].trim()

        // Find the object in context stack
        for ((contextName, contextObj) in contextStack.reversed()) {
            if (contextName == objectName) {
                println("Found matching object: $contextName")
                val propertyValue = getPropertyValue(contextObj, propertyName)
                println("Property value for $propertyName: $propertyValue")
                return propertyValue.toString()
            }
        }
        throw Exception("Warning: Could not resolve $expression - object '$objectName' not found in context")
    } else {
        throw Exception("Illegal format ${expression}")
    }
}

private fun resolveCollectionData(collectionAttr: String, contextStack: List<Pair<String, Any>>): Any? {
    // Parse collection attribute (could be "propertyName" or "objectName.propertyName")
    val parts = collectionAttr.split(".", limit = 2)

    if (parts.size == 2) {
        // Format: "objectName.propertyName"
        val objectName = parts[0].trim()
        val propertyName = parts[1].trim()

        // Find the object in context stack
        for ((contextName, contextObj) in contextStack.reversed()) {
            if (contextName == objectName) {
                return getPropertyValue(contextObj, propertyName)
            }
        }
    } else {
        // Format: "propertyName" - look in current context
        val propertyName = parts[0].trim()
        for ((_, contextObj) in contextStack.reversed()) {
            val property = getPropertyValue(contextObj, propertyName)
            if (property != null) {
                return property
            }
        }
    }

    return null
}

private fun getPropertyValue(obj: Any, propertyName: String): Any? {
    return try {
        val kClass = obj::class
        val property = kClass.memberProperties.find { it.name == propertyName } as? KProperty1<Any, Any?>
        if (property != null) {
            property.isAccessible = true
            property.get(obj)
        } else {
            null
        }
    } catch (e: Exception) {
        println("Error accessing property $propertyName: ${e.message}")
        null
    }
}
