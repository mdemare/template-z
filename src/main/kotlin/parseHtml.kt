package nl.mdemare

import io.ktor.http.HttpStatusCode
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import kotlin.collections.iterator

fun parseHtml(
    jsonParam: String,
    htmlFile: File
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
            // TODO !jsonObj.isNull(key) should be jsonObj.isBoolean(key) or a similar function
            if (jsonObj.has(key) && !jsonObj.isNull(key)) {
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

        // TODO
        //  Find each element with attribute data-collection (parent element)
        // The value of the attribute is the collectionName. jsonObject[collectionName] contains an array of items
        // For each parent, find child element with data-collection-item="default", and use it as the template element
        // Remove all children of the parent, and add a copy of the template element for each item.
    } catch (e: JSONException) {
        errorMessage = "Error: Invalid JSON format - ${e.message}"
        statusCode = HttpStatusCode.Companion.BadRequest
    } catch (e: Exception) {
        e.printStackTrace()
        errorMessage = "Error: ${e.message}"
        statusCode = HttpStatusCode.Companion.InternalServerError
    }
    return Triple(statusCode, errorMessage, doc)
}
