import org.json.JSONObject
import java.io.File
import java.io.FileWriter

data class ClassDefinition(
    val name: String,
    val properties: List<Property>
)

data class Property(
    val name: String,
    val type: String,
    val isList: Boolean = false
)

fun convertJsonToKotlinDataClasses(jsonString: String) {
    val jsonObject = JSONObject(jsonString)
    val classes = mutableListOf<ClassDefinition>()

    // Process the JSON and extract class definitions
    processJsonObject(jsonObject, classes)

    // Write to file
    writeDataClassesToFile(classes)
}

private fun processJsonObject(jsonObject: JSONObject, classes: MutableList<ClassDefinition>) {
    jsonObject.keys().forEach { key ->
        val value = jsonObject.getJSONObject(key)
        processComponent(key, value, classes)
    }
}

private fun processComponent(className: String, componentJson: JSONObject, classes: MutableList<ClassDefinition>) {
    val properties = mutableListOf<Property>()

    // Handle components (nested objects)
    if (componentJson.has("components")) {
        val components = componentJson.getJSONObject("components")
        components.keys().forEach { componentKey ->
            val component = components.getJSONObject(componentKey)
            val itemName = if (component.has("itemName")) {
                component.getString("itemName")
            } else {
                componentKey.removeSuffix("s") // Simple pluralization handling
            }

            val isArray = component.optBoolean("array", false)
            val capitalizedItemName = itemName.capitalize()

            // Recursively process nested component
            processComponent(capitalizedItemName, component, classes)

            // Add property to current class
            val propertyType = if (isArray) "List<$capitalizedItemName>" else capitalizedItemName
            properties.add(Property(componentKey, propertyType))
        }
    }

    // Handle direct properties
    if (componentJson.has("properties")) {
        val propertiesJson = componentJson.getJSONObject("properties")
        propertiesJson.keys().forEach { propertyKey ->
            val propertyType = propertiesJson.getString(propertyKey)
            val kotlinType = mapJsonTypeToKotlin(propertyType)
            properties.add(Property(propertyKey, kotlinType))
        }
    }

    // Only add class if it has properties
    if (properties.isNotEmpty()) {
        classes.add(ClassDefinition(className.capitalize(), properties))
    }
}

private fun mapJsonTypeToKotlin(jsonType: String): String {
    return when (jsonType.lowercase()) {
        "string" -> "String"
        "int", "integer" -> "Int"
        "long" -> "Long"
        "double", "float" -> "Double"
        "boolean", "bool" -> "Boolean"
        else -> "String" // Default to String for unknown types
    }
}

private fun writeDataClassesToFile(classes: List<ClassDefinition>) {
    val file = File("src/main/kotlin/Root.kt")

    // Create directories if they don't exist
    file.parentFile.mkdirs()

    FileWriter(file).use { writer ->
        writer.write("import kotlinx.serialization.Serializable\n\n")

        // Write each data class
        classes.reversed().forEach { classDefinition ->
            writer.write("@Serializable\ndata class ${classDefinition.name}(\n")

            classDefinition.properties.forEachIndexed { index, property ->
                val comma = if (index < classDefinition.properties.size - 1) "," else ""
                writer.write("  val ${property.name}: ${property.type}$comma\n")
            }

            writer.write(")\n\n")
        }
    }

    println("Data classes written to src/main/kotlin/Root.kt")
}

// Extension function to capitalize first letter
private fun String.capitalize(): String {
    return if (isEmpty()) this else this[0].uppercaseChar() + substring(1)
}

fun main() {
    try {
        // Read JSON from data.json file
        val jsonFile = File("data.json")
        if (!jsonFile.exists()) {
            println("Error: data.json file not found!")
            return
        }

        val jsonInput = jsonFile.readText()

        convertJsonToKotlinDataClasses(jsonInput)

    } catch (e: Exception) {
        println("Error reading or processing JSON file: ${e.message}")
        e.printStackTrace()
    }
}
