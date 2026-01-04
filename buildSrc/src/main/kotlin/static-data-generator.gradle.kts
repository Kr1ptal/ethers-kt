import com.fasterxml.jackson.databind.JsonNode
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Extension for configuring static data generation.
 */
abstract class StaticDataGeneratorExtension {
    abstract val generators: org.gradle.api.NamedDomainObjectContainer<StaticDataConfig>
}

/**
 * Configuration for a single static data generator.
 */
abstract class StaticDataConfig(val configName: String) : org.gradle.api.Named {
    abstract val inputFile: RegularFileProperty
    abstract val packageName: Property<String>
    abstract val objectName: Property<String>
    abstract val propertyName: Property<String>

    internal var dataProvider: ((File) -> JsonNode)? = null

    /**
     * Define a lambda that reads the input file and returns a JsonNode.
     * The JsonNode must be an array at the top level.
     */
    fun data(provider: (File) -> JsonNode) {
        dataProvider = provider
    }

    override fun getName(): String = configName
}

/**
 * Task that generates Kotlin source files from static data files.
 */
@CacheableTask
abstract class GenerateStaticDataTask : org.gradle.api.DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val objectName: Property<String>

    @get:Input
    abstract val propertyName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    internal var dataProvider: ((File) -> JsonNode)? = null

    @TaskAction
    fun generate() {
        val input = inputFile.get().asFile
        val output = outputDir.get().asFile
        val pkg = packageName.get()
        val objName = objectName.get()
        val propName = propertyName.get()
        val provider = dataProvider ?: throw IllegalStateException("data {} provider not configured for generator")

        output.mkdirs()

        val jsonData = provider(input)
        val fileSpec = generateFromJsonNode(jsonData, pkg, objName, propName)
        fileSpec.writeTo(output)
    }

    private fun generateFromJsonNode(data: JsonNode, pkg: String, objName: String, propName: String): FileSpec {
        require(data.isArray) { "Top-level JSON must be an array" }

        val elements = data.toList()
        if (elements.isEmpty()) {
            return generateEmptyArray(pkg, objName, propName)
        }

        val firstElement = elements.first()
        return when {
            firstElement.isObject -> generateObjectArray(elements, pkg, objName, propName)
            firstElement.isTextual -> generatePrimitiveArray(elements, pkg, objName, propName, String::class.asTypeName())
            firstElement.isIntegralNumber -> generatePrimitiveArray(elements, pkg, objName, propName, Long::class.asTypeName())
            firstElement.isFloatingPointNumber -> generatePrimitiveArray(elements, pkg, objName, propName, Double::class.asTypeName())
            firstElement.isBoolean -> generatePrimitiveArray(elements, pkg, objName, propName, Boolean::class.asTypeName())
            else -> throw IllegalArgumentException("Unsupported element type: ${firstElement.nodeType}")
        }
    }

    private fun generateEmptyArray(pkg: String, objName: String, propName: String): FileSpec {
        val arrayType = Array::class.asClassName().parameterizedBy(Any::class.asTypeName())

        val objectSpec = TypeSpec.objectBuilder(objName)
            .addModifiers(KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder(propName, arrayType)
                    .initializer("emptyArray()")
                    .build()
            )
            .build()

        return FileSpec.builder(pkg, objName)
            .addType(objectSpec)
            .indent("    ")
            .build()
    }

    private fun generatePrimitiveArray(
        elements: List<JsonNode>,
        pkg: String,
        objName: String,
        propName: String,
        elementType: TypeName,
    ): FileSpec {
        val arrayCode = elements.joinToString(
            separator = ",\n        ",
            prefix = "arrayOf(\n        ",
            postfix = ",\n    )"
        ) { node ->
            when {
                node.isTextual -> "\"${escapeString(node.asText())}\""
                node.isIntegralNumber -> "${node.asLong()}L"
                node.isFloatingPointNumber -> "${node.asDouble()}"
                node.isBoolean -> "${node.asBoolean()}"
                else -> throw IllegalArgumentException("Unexpected element type in primitive array")
            }
        }

        val arrayType = Array::class.asClassName().parameterizedBy(elementType)

        val objectSpec = TypeSpec.objectBuilder(objName)
            .addModifiers(KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder(propName, arrayType)
                    .initializer(arrayCode)
                    .build()
            )
            .build()

        return FileSpec.builder(pkg, objName)
            .addType(objectSpec)
            .indent("    ")
            .build()
    }

    private fun generateObjectArray(
        elements: List<JsonNode>,
        pkg: String,
        objName: String,
        propName: String,
    ): FileSpec {
        val schema = inferObjectSchema(elements)
        val dataClassName = "Element"
        val dataClassFullName = ClassName(pkg, objName, dataClassName)

        // Build the data class
        val dataClass = buildDataClass(dataClassName, schema)

        // Build the array initializer
        val arrayCode = elements.joinToString(
            separator = ",\n        ",
            prefix = "arrayOf(\n        ",
            postfix = ",\n    )"
        ) { node ->
            val args = schema.entries.joinToString(", ") { (jsonFieldName, fieldInfo) ->
                val value = node.get(jsonFieldName)
                formatValue(value, fieldInfo)
            }
            "$dataClassName($args)"
        }

        val arrayType = Array::class.asClassName().parameterizedBy(dataClassFullName)

        val objectSpec = TypeSpec.objectBuilder(objName)
            .addModifiers(KModifier.INTERNAL)
            .addType(dataClass)
            .addProperty(
                PropertySpec.builder(propName, arrayType)
                    .initializer(arrayCode)
                    .build()
            )
            .build()

        return FileSpec.builder(pkg, objName)
            .addType(objectSpec)
            .indent("    ")
            .build()
    }

    private data class FieldInfo(
        val type: FieldType,
        val nullable: Boolean,
    )

    private sealed class FieldType {
        data object StringType : FieldType()
        data object LongType : FieldType()
        data object DoubleType : FieldType()
        data object BooleanType : FieldType()
    }

    private fun inferObjectSchema(elements: List<JsonNode>): LinkedHashMap<String, FieldInfo> {
        val schema = LinkedHashMap<String, FieldInfo>()

        // First pass: collect all fields and their types
        for (element in elements) {
            val fieldNames = element.fieldNames().asSequence().toList()
            for (fieldName in fieldNames) {
                val value = element.get(fieldName)
                val fieldType = inferFieldType(value)

                val existing = schema[fieldName]
                if (existing == null) {
                    schema[fieldName] = FieldInfo(fieldType, nullable = value.isNull)
                } else if (value.isNull) {
                    schema[fieldName] = existing.copy(nullable = true)
                }
            }

            // Fields missing from this element become nullable
            for (key in schema.keys) {
                if (!element.has(key)) {
                    schema[key] = schema[key]!!.copy(nullable = true)
                }
            }
        }

        return schema
    }

    private fun inferFieldType(value: JsonNode): FieldType {
        return when {
            value.isNull -> FieldType.StringType // Default to string for null-only fields
            value.isTextual -> FieldType.StringType
            value.isIntegralNumber -> FieldType.LongType
            value.isFloatingPointNumber -> FieldType.DoubleType
            value.isBoolean -> FieldType.BooleanType
            else -> throw IllegalArgumentException("Unsupported field type: ${value.nodeType}")
        }
    }

    private fun buildDataClass(className: String, schema: LinkedHashMap<String, FieldInfo>): TypeSpec {
        val constructorBuilder = FunSpec.constructorBuilder()
        val classBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)

        for ((jsonFieldName, fieldInfo) in schema) {
            val kotlinFieldName = jsonFieldName.snakeToCamelCase()
            var typeName: TypeName = when (fieldInfo.type) {
                FieldType.StringType -> String::class.asTypeName()
                FieldType.LongType -> Long::class.asTypeName()
                FieldType.DoubleType -> Double::class.asTypeName()
                FieldType.BooleanType -> Boolean::class.asTypeName()
            }

            if (fieldInfo.nullable) {
                typeName = typeName.copy(nullable = true)
            }

            constructorBuilder.addParameter(kotlinFieldName, typeName)
            classBuilder.addProperty(
                PropertySpec.builder(kotlinFieldName, typeName)
                    .initializer(kotlinFieldName)
                    .build()
            )
        }

        return classBuilder
            .primaryConstructor(constructorBuilder.build())
            .build()
    }

    private fun formatValue(value: JsonNode?, fieldInfo: FieldInfo): String {
        if (value == null || value.isNull) {
            return "null"
        }

        return when (fieldInfo.type) {
            FieldType.StringType -> "\"${escapeString(value.asText())}\""
            FieldType.LongType -> "${value.asLong()}L"
            FieldType.DoubleType -> "${value.asDouble()}"
            FieldType.BooleanType -> "${value.asBoolean()}"
        }
    }

    private fun escapeString(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("$", "\\$")
    }

    private fun String.snakeToCamelCase(): String {
        if (!contains("_")) return this
        return split("_").mapIndexed { index, part ->
            if (index == 0) part.lowercase()
            else part.lowercase().replaceFirstChar { it.uppercase() }
        }.joinToString("")
    }
}

// Register the extension
val extension = extensions.create<StaticDataGeneratorExtension>("staticDataGenerator")

// Configure the output directory
val generatedSourceDir = layout.buildDirectory.dir("generated/source/staticData/main/kotlin")

// Hook into Kotlin source sets when Kotlin plugin is applied
pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    val kotlin = extensions.getByType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>()
    kotlin.sourceSets.getByName("main").kotlin.srcDir(generatedSourceDir)
}

// Create tasks for each generator after evaluation
afterEvaluate {
    extension.generators.forEach { config ->
        val taskName = "generate${config.name.replaceFirstChar { it.uppercase() }}StaticData"

        val task = tasks.register<GenerateStaticDataTask>(taskName) {
            inputFile.set(config.inputFile)
            packageName.set(config.packageName)
            objectName.set(config.objectName)
            propertyName.set(config.propertyName)
            outputDir.set(generatedSourceDir)
            dataProvider = config.dataProvider
        }

        tasks.named("compileKotlin").configure {
            dependsOn(task)
        }

        // Also wire up kapt if present
        tasks.matching { it.name == "kaptGenerateStubsKotlin" }.configureEach {
            dependsOn(task)
        }

        // Wire up ktlint tasks
        tasks.matching { it.name.startsWith("runKtlint") && it.name.contains("MainSourceSet") }.configureEach {
            dependsOn(task)
        }
    }
}
