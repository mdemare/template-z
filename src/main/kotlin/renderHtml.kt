package nl.mdemare

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@Serializable
data class PlayerHand(val color: String)
@Serializable
data class Player(val roleName: String, val playerHands: List<PlayerHand>)
@Serializable
data class Panel(val speed: String)
@Serializable
data class Root(val players: List<Player>, val panel: Panel)

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
        val textValue = resolveTextContent(textContentAttr, contextStack)
        if (textValue != null) {
            println("Setting text content to: $textValue")
            element.text(textValue)
        } else {
            println("Failed to resolve text content for: $textContentAttr")
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
                    println("Added cloned element to parent")

                    elementsCreated++
                }
            }

            // Assert that the number of elements created matches the collection size
            val nrItems = collectionData.size
            assert(element.children().size == nrItems) {
                "Mismatch: Created $elementsCreated elements but expected $nrItems (items in collection of size ${collectionData.size})"
            }
            println("Assertion passed: Created $elementsCreated elements for $nrItems items")

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

private fun resolveTextContent(textContentAttr: String, contextStack: List<Pair<String, Any>>): String? {
    println("Resolving text content: $textContentAttr")
    println("Current context: ${contextStack.map { "${it.first}:${it.second.javaClass.simpleName}" }}")

    // Parse attribute value - could be "propertyName" or "objectName.propertyName"
    val parts = textContentAttr.split(".", limit = 2)

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
                return propertyValue?.toString()
            }
        }

        println("Warning: Could not resolve $textContentAttr - object '$objectName' not found in context")
    } else {
        // Format: "propertyName" - look for property in current context (most recent object)
        val propertyName = parts[0].trim()

        // Search from most recent context backwards
        for ((contextName, contextObj) in contextStack.reversed()) {
            val propertyValue = getPropertyValue(contextObj, propertyName)
            if (propertyValue != null) {
                println("Found property '$propertyName' in object '$contextName': $propertyValue")
                return propertyValue.toString()
            }
        }

        println("Warning: Could not find property '$propertyName' in any context object")
    }

    return null
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

// Example usage
fun main() {
    // Sample data
    val playerHands = listOf(PlayerHand("red"), PlayerHand("blue"))
    val players = listOf(
        Player("Warrior", playerHands),
        Player("Mage", playerHands)
    )
    val panel = Panel("Fast")
    val rootData = Root(players, panel)

    // Render HTML
    val htmlFile = File("game.html")
    if (htmlFile.exists()) {
        try {
            val renderedHtml = renderHtml(htmlFile, rootData)
            println("Rendered HTML:")
            println(renderedHtml)

            // Optionally save to file
            File("rendered_game.html").writeText(renderedHtml)
        } catch (e: Exception) {
            println("Error rendering HTML: ${e.message}")
            e.printStackTrace()
        }
    } else {
        println("HTML file not found: ${htmlFile.path}")
    }
}
