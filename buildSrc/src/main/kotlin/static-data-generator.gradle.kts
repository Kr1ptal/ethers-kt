import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
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
    abstract val generatorType: Property<String>

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
    abstract val generatorType: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val input = inputFile.get().asFile
        val output = outputDir.get().asFile
        val pkg = packageName.get()
        val objName = objectName.get()
        val type = generatorType.get()

        output.mkdirs()

        val fileSpec = when (type) {
            "bip39-wordlist" -> generateBip39Wordlist(input, pkg, objName)
            "multicall3-deployments" -> generateMulticall3Deployments(input, pkg, objName)
            else -> throw IllegalArgumentException("Unknown generator type: $type")
        }

        fileSpec.writeTo(output)
    }

    private fun generateBip39Wordlist(input: File, pkg: String, objName: String): FileSpec {
        val words = input.readLines().filter { it.isNotBlank() }

        require(words.size == 2048) { "BIP39 wordlist must contain exactly 2048 words, found ${words.size}" }

        val wordsArrayCode = words.joinToString(
            separator = ",\n        ",
            prefix = "arrayOf(\n        ",
            postfix = ",\n    )"
        ) { "\"$it\"" }

        val arrayOfStringsType = Array::class.asClassName().parameterizedBy(String::class.asTypeName())

        val objectSpec = TypeSpec.objectBuilder(objName)
            .addModifiers(KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder("WORDS", arrayOfStringsType)
                    .initializer(wordsArrayCode)
                    .build()
            )
            .build()

        return FileSpec.builder(pkg, objName)
            .addType(objectSpec)
            .indent("    ")
            .build()
    }

    private fun generateMulticall3Deployments(input: File, pkg: String, objName: String): FileSpec {
        val mapper = ObjectMapper()
        val deployments: List<JsonNode> = mapper.readTree(input).toList()

        val deploymentClassName = ClassName(pkg, objName, "Deployment")

        val deploymentsArrayCode = deployments.joinToString(
            separator = ",\n        ",
            prefix = "arrayOf(\n        ",
            postfix = ",\n    )"
        ) { node ->
            val name = node.get("name").asText()
            val chainId = node.get("chainId").asLong()
            val url = node.get("url").asText()
            val address = node.get("address")?.asText()

            if (address != null) {
                "Deployment(\"$name\", ${chainId}L, \"$url\", \"$address\")"
            } else {
                "Deployment(\"$name\", ${chainId}L, \"$url\", null)"
            }
        }

        val nullableStringType = String::class.asTypeName().copy(nullable = true)

        val deploymentClass = TypeSpec.classBuilder("Deployment")
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("name", String::class)
                    .addParameter("chainId", Long::class)
                    .addParameter("url", String::class)
                    .addParameter("address", nullableStringType)
                    .build()
            )
            .addProperty(PropertySpec.builder("name", String::class).initializer("name").build())
            .addProperty(PropertySpec.builder("chainId", Long::class).initializer("chainId").build())
            .addProperty(PropertySpec.builder("url", String::class).initializer("url").build())
            .addProperty(PropertySpec.builder("address", nullableStringType).initializer("address").build())
            .build()

        val arrayOfDeploymentsType = Array::class.asClassName().parameterizedBy(deploymentClassName)

        val objectSpec = TypeSpec.objectBuilder(objName)
            .addModifiers(KModifier.INTERNAL)
            .addType(deploymentClass)
            .addProperty(
                PropertySpec.builder("DEPLOYMENTS", arrayOfDeploymentsType)
                    .initializer(deploymentsArrayCode)
                    .build()
            )
            .build()

        return FileSpec.builder(pkg, objName)
            .addType(objectSpec)
            .indent("    ")
            .build()
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
            generatorType.set(config.generatorType)
            outputDir.set(generatedSourceDir)
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
