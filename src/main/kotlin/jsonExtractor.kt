import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File

fun jsonExtractor(htmlFile: File): JSONObject? {
    // Start recursive processing from the document body
    return processElementRecursively(Jsoup.parse(htmlFile, "UTF-8").body())
}

private fun processElementRecursively(element: Element?): JSONObject? {
    if (element == null) return null

    // Check if element has data-component attribute
    val componentName = element.attr("data-component")

    if (componentName.isNotEmpty()) {
        // Create root object with component name as key
        val rootObject = JSONObject()
        val componentObject = JSONObject()
        rootObject.put(componentName, componentObject)

        // Create ancestor stack for tracking parents
        val ancestorStack = mutableListOf<Pair<String, JSONObject>>()
        ancestorStack.add(Pair(componentName, componentObject))

        // Process children of this component element
        processChildren(element, componentObject, ancestorStack)

        return rootObject
    }

    // If no data-component found, continue searching in children
    for (child in element.children()) {
        val result = processElementRecursively(child)
        if (result != null) {
            return result
        }
    }

    return null
}

private fun processChildren(element: Element, currentValue: JSONObject, ancestorStack: MutableList<Pair<String, JSONObject>>) {
    for (child in element.children()) {
        val componentName = child.attr("data-component")
        val textContentAttr = child.attr("data-text-content")
        val collectionAttr = child.attr("data-collection")

        child.attributes().forEach { attribute ->
            if (attribute.key.startsWith("data-attribute-")) {
                println(attribute.key)
                println(attribute.value)
                parseAndAddProperty(attribute.value, ancestorStack)
            }
        }

        // Handle property-containing attributes
        if (textContentAttr.isNotEmpty()) {
            parseAndAddProperty(textContentAttr, ancestorStack)
        }

        // Handle data-collection attribute
        if (collectionAttr.isNotEmpty()) {
            // Find first child with data-collection-item
            val collectionItem = child.children().firstOrNull { it.hasAttr("data-collection-item") }
            if (collectionItem != null) {
                val collectionItemName = collectionItem.attr("data-collection-item")

                if (collectionItemName.isNotEmpty()) {
                    // Treat this as a component definition
                    if (!currentValue.has("components")) {
                        currentValue.put("components", JSONObject())
                    }
                    val componentsObject = currentValue.getJSONObject("components")
                    val collectionItemObject = JSONObject()
                    val parts = collectionAttr.split(".", limit = 2)
                    componentsObject.put(parts[1], collectionItemObject)
                    collectionItemObject.put("itemName", collectionItemName)
                    collectionItemObject.put("array", true)

                    // Add this collection item to ancestor stack
                    ancestorStack.add(Pair(collectionItemName, collectionItemObject))

                    // Handle data-text-content attribute on the collection item itself
                    val collectionItemTextContent = collectionItem.attr("data-text-content")
                    if (collectionItemTextContent.isNotEmpty()) {
                        parseAndAddProperty(collectionItemTextContent, ancestorStack)
                    }

                    // Process the collection item's children
                    processChildren(collectionItem, collectionItemObject, ancestorStack)

                    // Remove from ancestor stack when done processing
                    ancestorStack.removeAt(ancestorStack.size - 1)
                }
            }

            // Continue processing other children (but ignore other data-collection-item elements)
            for (otherChild in child.children()) {
                if (!otherChild.hasAttr("data-collection-item")) {
                    processChildren(otherChild, currentValue, ancestorStack)
                }
            }
        } else if (componentName.isNotEmpty()) {
            // Found another data-component
            if (!currentValue.has("components")) {
                currentValue.put("components", JSONObject())
            }

            val componentsObject = currentValue.getJSONObject("components")
            val childComponentObject = JSONObject()
            componentsObject.put(componentName, childComponentObject)

            // Add this component to ancestor stack
            ancestorStack.add(Pair(componentName, childComponentObject))

            // Recursively process this child component
            processChildren(child, childComponentObject, ancestorStack)

            // Remove from ancestor stack when done processing
            ancestorStack.removeAt(ancestorStack.size - 1)
        } else {
            // No data-component or data-collection, continue searching in children
            processChildren(child, currentValue, ancestorStack)
        }
    }
}
private fun parseAndAddProperty(attributeValue: String, ancestorStack: List<Pair<String, JSONObject>>) {
    // Extract value from ${} if present
    val processedValue = if (attributeValue.matches(Regex("\\$\\{.*}"))) {
        // Extract content between ${ and }
        val x = attributeValue.substring(2, attributeValue.length - 1)
        println("X")
        println(x)
        x
    } else {
        attributeValue
    }

    // Parse attribute value (expecting format like "objectName.propertyName")
    val parts = processedValue.split(".", limit = 2)
    if (parts.size != 2) {
        println("Warning: Invalid attribute format: $processedValue (expected format: objectName.propertyName)")
        return
    }

    val objectName = parts[0].trim()
    val propertyName = parts[1].trim()

    // Look up the object name among ancestors (from most recent to oldest)
    for (i in ancestorStack.indices.reversed()) {
        val (ancestorName, ancestorObject) = ancestorStack[i]

        if (ancestorName == objectName) {
            // Found matching ancestor, add property
            if (!ancestorObject.has("properties")) {
                ancestorObject.put("properties", JSONObject())
            }

            val propertiesObj = ancestorObject.getJSONObject("properties")
            propertiesObj.put(propertyName, "string")

            println("Added property '$propertyName' (type: String) to component '$objectName'")
            return
        }
    }

    println("Warning: Object '$objectName' not found among ancestors for property '$propertyName'")
}

fun main() {
    val htmlFile = File("pandemic.html")
    val outputFile = File("data.json")

    try {
        val result = jsonExtractor(htmlFile)
        if (result != null) {
            outputFile.writeText(result.toString(4))
            println("Data successfully saved to data.json")
        } else {
            println("No data-component elements found")
        }
    } catch (e: Exception) {
        println("Error parsing HTML file: ${e.message}")
        e.printStackTrace()
    }
}
