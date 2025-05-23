package nl.mdemare

import org.json.JSONObject
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

fun generateDataClasses(jsonString: String) {
    val json = JSONObject(jsonString)
    val rootComponent = json.getJSONObject("root")

    val fileSpec = FileSpec.builder("", "GeneratedClasses")
    val generatedClasses = mutableSetOf<String>()

    generateDataClass("Root", rootComponent, fileSpec, generatedClasses)

    // Print the generated code
    println(fileSpec.build().toString())
}

fun generateDataClass(
    className: String,
    component: JSONObject,
    fileSpec: FileSpec.Builder,
    generatedClasses: MutableSet<String>
) {
    if (generatedClasses.contains(className)) {
        return
    }

    val dataClassBuilder = TypeSpec.classBuilder(className)
        .addModifiers(KModifier.DATA)

    val constructorBuilder = FunSpec.constructorBuilder()
    val properties = mutableListOf<PropertySpec>()

    // Process nested components first (to handle dependencies)
    if (component.has("components")) {
        val components = component.getJSONObject("components")
        val componentNames = components.keys()

        while (componentNames.hasNext()) {
            val componentName = componentNames.next()
            val nestedClassName = componentName.replaceFirstChar { it.uppercase() }
            val nestedComponent = components.getJSONObject(componentName)

            // Recursively generate nested classes
            generateDataClass(nestedClassName, nestedComponent, fileSpec, generatedClasses)
        }
    }

    // Process direct properties
    if (component.has("properties")) {
        val propertiesArray = component.getJSONArray("properties")

        for (i in 0 until propertiesArray.length()) {
            val property = propertiesArray.getJSONObject(i)
            val propName = property.getString("name")
            val propType = property.getString("type")

            val kotlinType = mapJsonTypeToKotlinType(propType, propName)

            val propertySpec = PropertySpec.builder(propName, kotlinType)
                .initializer(propName)
                .build()

            properties.add(propertySpec)

            constructorBuilder.addParameter(propName, kotlinType)
        }
    }

    // Add constructor and properties to the class
    dataClassBuilder.primaryConstructor(constructorBuilder.build())
    properties.forEach { dataClassBuilder.addProperty(it) }

    fileSpec.addType(dataClassBuilder.build())
    generatedClasses.add(className)
}

fun mapJsonTypeToKotlinType(jsonType: String, propertyName: String): TypeName {
    return when {
        jsonType == "string" -> STRING
        jsonType == "array" -> {
            val elementType = ANY
            LIST.parameterizedBy(elementType)
        }
        jsonType == "boolean" -> BOOLEAN
        jsonType == "number" -> INT
        else -> {
            // Custom type - capitalize first letter
            ClassName("", jsonType.replaceFirstChar { it.uppercase() })
        }
    }
}

// Alternative simpler function without kotlinpoet
fun generateDataClassesSimple(jsonString: String) {
    val json = JSONObject(jsonString)
    val rootComponent = json.getJSONObject("root")

    val generatedClasses = mutableListOf<String>()
    val processedClasses = mutableSetOf<String>()

    generateDataClassSimple("Root", rootComponent, generatedClasses, processedClasses)

    // Print all generated classes
    generatedClasses.forEach { println(it) }
}

fun generateDataClassSimple(
    className: String,
    component: JSONObject,
    generatedClasses: MutableList<String>,
    processedClasses: MutableSet<String>
) {
    if (processedClasses.contains(className)) {
        return
    }

    // Process nested components first
    if (component.has("components")) {
        val components = component.getJSONObject("components")
        val componentNames = components.keys()

        while (componentNames.hasNext()) {
            val componentName = componentNames.next()
            val nestedClassName = componentName.replaceFirstChar { it.uppercase() }
            val nestedComponent = components.getJSONObject(componentName)

            generateDataClassSimple(nestedClassName, nestedComponent, generatedClasses, processedClasses)
        }
    }

    val properties = mutableListOf<String>()

    // Process direct properties
    if (component.has("properties")) {
        val propertiesArray = component.getJSONArray("properties")

        for (i in 0 until propertiesArray.length()) {
            val property = propertiesArray.getJSONObject(i)
            val propName = property.getString("name")
            val propType = property.getString("type")

            val kotlinType = when (propType) {
                "string" -> "String"
                "array" -> "List<ANY>"
                "boolean" -> "Boolean"
                "number" -> "Int"
                else -> propType.replaceFirstChar { it.uppercase() }
            }

            properties.add("$propName: $kotlinType")
        }
    }

    val dataClass = if (properties.isEmpty()) {
        "@Serializable\ndata class $className()"
    } else {
        "@Serializable\ndata class $className(${properties.joinToString(", ")})"
    }

    generatedClasses.add(dataClass)
    processedClasses.add(className)
}

// Example usage
fun main() {
    val jsonInput = """
{"root": {"properties": [
    {
        "name": "actionsRemaining",
        "type": "string"
    },
    {
        "name": "turn",
        "type": "string"
    },
    {
        "name": "outbreaks",
        "type": "string"
    },
    {
        "name": "playerCards",
        "type": "string"
    },
    {
        "name": "currentPlayerRole",
        "type": "string"
    }
]}}
    """.trimIndent()

    println("=== Using KotlinPoet ===")
    generateDataClasses(jsonInput)

    println("\n=== Using Simple String Generation ===")
    generateDataClassesSimple(jsonInput)
}
