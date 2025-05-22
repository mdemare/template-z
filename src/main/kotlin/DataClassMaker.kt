package nl.mdemare

import com.squareup.kotlinpoet.*
import org.json.JSONObject
import java.io.File

/**
 * Generates Kotlin data classes from a nested JSON structure.
 *
 * @param json The JSON object to convert to data classes
 * @param baseClassName The name for the root class
 * @param packageName The package name for the generated classes
 * @param outputDir Directory where Kotlin files will be generated
 */
fun generateDataClassesFromJson(
    json: JSONObject,
    baseClassName: String,
    packageName: String,
    outputDir: String
) {
    val processedClasses = mutableMapOf<String, TypeSpec.Builder>()

    // Process the root object
    processJsonObject(json, baseClassName, processedClasses)

    // Generate Kotlin files for each class
    for ((className, classBuilder) in processedClasses) {
        val file = FileSpec.builder(packageName, className)
            .addType(classBuilder.build())
            .build()

        val directory = File(outputDir)
        directory.mkdirs()
        file.writeTo(directory)
    }
}

/**
 * Recursively processes a JSON object to build data class definitions.
 *
 * @param jsonObject The JSON object to process
 * @param className The name for the generated class
 * @param processedClasses Map to store generated class builders
 * @return The TypeName for the generated class
 */
private fun processJsonObject(
    jsonObject: JSONObject,
    className: String,
    processedClasses: MutableMap<String, TypeSpec.Builder>
): TypeName {
    // Create class builder if not already created
    if (!processedClasses.containsKey(className)) {
        val classBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(FunSpec.constructorBuilder().build())

        processedClasses[className] = classBuilder

        // Process all properties
        val properties = mutableListOf<PropertySpec>()
        val constructorBuilder = FunSpec.constructorBuilder()

        jsonObject.keys().forEach { key ->
            val propertyName = sanitizePropertyName(key)
            val value = jsonObject.get(key)

            val (propertyType, needsImport) = when {
                value is JSONObject -> {
                    // Recursively process nested object
                    val nestedClassName = "${className}${key.capitalize()}"
                    val typeName = processJsonObject(value, nestedClassName, processedClasses)
                    Pair(typeName, true)
                }
                else -> {
                    // Default to String for all primitive values
                    Pair(String::class.asTypeName(), false)
                }
            }

            // Add parameter to constructor
            constructorBuilder.addParameter(
                ParameterSpec.builder(propertyName, propertyType)
                    .build()
            )

            // Create property with constructor reference
            val property = PropertySpec.builder(propertyName, propertyType)
                .initializer(propertyName)
                .build()

            properties.add(property)
        }

        // Update the class with constructor and properties
        classBuilder.primaryConstructor(constructorBuilder.build())
        properties.forEach { classBuilder.addProperty(it) }
    }

    return ClassName("", className)
}

/**
 * Sanitizes a JSON key to be a valid Kotlin property name.
 */
private fun sanitizePropertyName(key: String): String {
    // Handle invalid characters and reserved keywords
    val sanitized = key.replace(Regex("[^a-zA-Z0-9_]"), "_")

    // Handle starting with number
    val startsWithLetter = sanitized.matches(Regex("^[a-zA-Z_].*"))
    val finalName = if (!startsWithLetter) "_$sanitized" else sanitized

    // Handle Kotlin keywords
    return when (finalName) {
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
        "if", "in", "interface", "is", "null", "object", "package", "return",
        "super", "this", "throw", "true", "try", "typealias", "typeof",
        "val", "var", "when", "while" -> "${finalName}_"
        else -> finalName
    }
}

/**
 * Convert string to title case for class names.
 */
private fun String.capitalize(): String {
    return if (this.isEmpty()) this
    else this[0].uppercaseChar() + this.substring(1)
}

// Example usage:
fun main() {
    val jsonString = """
    {
        "user": {
            "name": "John Doe",
            "address": {
                "street": "123 Main St",
                "city": "New York",
                "zipCode": "10001"
            },
            "contacts": {
                "email": "john@example.com",
                "phone": "555-1234"
            }
        },
        "settings": {
            "theme": "dark",
            "notifications": {
                "email": "weekly",
                "push": "daily"
            }
        }
    }
    """

    val jsonObject = JSONObject(jsonString)
    generateDataClassesFromJson(
        jsonObject,
        "RootData",
        "com.example.model",
        "src/main/kotlin"
    )
}
