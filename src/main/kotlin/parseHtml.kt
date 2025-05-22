package nl.mdemare

import io.ktor.http.HttpStatusCode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import kotlin.collections.iterator

fun parseHtml(
    jsonParam: String, htmlFile: File
): Triple<HttpStatusCode, String?, Document?> {
    var statusCode = HttpStatusCode.Companion.OK
    var errorMessage: String? = null
    var doc: Document? = null
    try {
        // Parse the JSON parameter
        val jsonObj = JSONObject(jsonParam)

        // Create a map of toggleable keys to boolean values
        val toggleMap = mutableMapOf<String, Boolean>()
        for (key in jsonObj.keys()) {
            // Fixed: Check if the value is a boolean
            if (jsonObj.has(key) && jsonObj.get(key) is Boolean) {
                try {
                    toggleMap[key] = jsonObj.getBoolean(key)
                } catch (e: Exception) {
                    // Skip non-boolean values
                }
            }
        }

        // Parse the HTML file
        doc = Jsoup.parse(htmlFile, "UTF-8")

        // Find all elements with data-toggleable attribute
        val toggleableElements = doc.select("[data-toggleable]")

        // Process each toggleable element
        for (element in toggleableElements) {
            val toggleKey = element.attr("data-toggleable")

            // Check if the key exists in JSON with a false value
            if (toggleMap.containsKey(toggleKey) && !toggleMap[toggleKey]!!) {
                // Remove the element
                element.remove()
            }
        }


        val output = JSONObject("{}")
        processCollectionsRecursively(doc.body(), jsonObj, output)
        println("PARSE")
        println(output.toString(4))
    } catch (e: JSONException) {
        println("ERROR JSON")
        e.printStackTrace()
        errorMessage = "Error: Invalid JSON format - ${e.message}"
        statusCode = HttpStatusCode.Companion.BadRequest
    } catch (e: Exception) {
        println("WEIRD")
        e.printStackTrace()
        errorMessage = "Error: ${e.message}"
        statusCode = HttpStatusCode.Companion.InternalServerError
    }
    return Triple(statusCode, errorMessage, doc)
}

// Recursive function to process collections
private fun processCollectionsRecursively(element: Element?, jsonObj: JSONObject, output: JSONObject) {
    if (element == null) return

    // Check if the current element has data-collection attribute
    if (element.hasAttr("data-collection")) {
        // Process this collection
        val processed = processCollectionElement(element, jsonObj, output)

        // If we processed the collection, we don't need to recurse into its children
        // as they've been replaced with the processed collection items
        if (processed) {
            return
        }
    }

    // Process all children recursively
    val children = element.children() // Get a snapshot of current children
    for (i in 0 until children.size) {
        val child = children[i]
        processCollectionsRecursively(child, jsonObj, output)
    }
}

// Process a single collection element
// Returns true if the collection was processed, false otherwise
private fun processCollectionElement(parent: Element, jsonObj: JSONObject, output: JSONObject): Boolean {
    val collectionName = parent.attr("data-collection")
    val subOutput = JSONObject()
    output.put(collectionName, subOutput)

    // Check if the collection exists in the JSON
    if (!jsonObj.has(collectionName) || jsonObj.get(collectionName) !is JSONArray) {
        return false
    }

    val itemsArray = jsonObj.getJSONArray(collectionName)

    // Find the template element
    val templateElement = parent.select("[data-collection-item=\"default\"]").first()
    if (templateElement == null) {
        return false
    }

    // Remove all children of the parent
    parent.empty()

    // Add a copy of the template for each item in the array
    for (i in 0 until itemsArray.length()) {
        val item = itemsArray.getJSONObject(i)
        val newElement = templateElement.clone()

        // Process text content elements
        processTextContentElements(newElement, item, subOutput)

        // Process elements with attribute specifications
        val attributeElements = newElement.select("[data-attribute-name]")
        for (attrElement in attributeElements) {
            // Get the attribute name to modify
            val attributeName = attrElement.attr("data-attribute-name")

            // Skip if the element doesn't have the required data-attribute-value attribute
            if (!attrElement.hasAttr("data-attribute-value")) {
                continue
            }

            // Get the attribute value or template
            var attributeValue: String = attrElement.attr("data-attribute-value")

            // Check if the attribute value contains template variables
            if (attributeValue.contains("\${")) {
                // Process template variables - there could be multiple
                val regex = Regex("\\$\\{([^}]*)}")
                attributeValue = regex.replace(attributeValue) { matchResult ->
                    val variableName = matchResult.groupValues[1]
                    if (item.has(variableName)) {
                        subOutput.put(variableName, "string")
                        item.get(variableName).toString()
                    } else {
                        // If variable not found, keep the original placeholder
                        matchResult.value
                    }
                }
            } else {
                subOutput.put(attributeValue, "string")

                // Direct mapping from JSON
                if (item.has(attributeValue)) {
                    attributeValue = item.get(attributeValue).toString()
                } else {
                    // Skip if no matching key in item
                    continue
                }
            }

            // Set the attribute on the element
            try {
                attrElement.attr(attributeName, attributeValue)
            } catch (e: Exception) {
                // Skip if we can't set the attribute
            }
        }

        // Remove the template marker from the new element
        newElement.removeAttr("data-collection-item")

        // Add the new element to the parent
        parent.appendChild(newElement)

        // IMPORTANT: Recursively process any nested collections in this new element
        processCollectionsRecursively(newElement, item, subOutput)
    }

    return true
}

// Helper function to process text content elements
private fun processTextContentElements(element: Element, item: JSONObject, output: JSONObject) {
    // Get all elements with data-text-content attribute
    val textContentElements = element.select("[data-text-content]")
    for (textElement in textContentElements) {
        val textKey = textElement.attr("data-text-content")
        output.put(textKey, "string")
        if (item.has(textKey)) {
            try {
                textElement.text(item.get(textKey).toString())
            } catch (e: Exception) {
                // Skip if we can't set the text content
            }
        }
    }

}

fun main() {
    val jsonData = """
    {
        "showHeader": true,
        "users": [
            {"name": "John", "email": "john@example.com"},
            {"name": "Jane", "email": "jane@example.com"}
        ]
    }
    """

    val htmlFile = File("template.html") // Create this file
    val result = parseHtml(jsonData, htmlFile)

    println("Status: ${result.first}")
    println("Error: ${result.second}")
    println("HTML: ${result.third}")
}
