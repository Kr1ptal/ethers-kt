package io.ethers.abigen

import io.ethers.abigen.reader.JsonAbiReaderRegistry
import java.io.File

/**
 * Headless entry point for [AbiContractBuilder]: generates a contract wrapper for every JSON-ABI file in a
 * directory, plus the custom error loader tying them together.
 *
 * ```
 * <abi-directory> <output-directory> <package-name> [loader-prefix]
 * ```
 *
 * The `io.kriptal.ethers.abigen-plugin` Gradle plugin is the supported way to generate wrappers during a build.
 * This exists for builds that cannot apply it - notably `ethers-abigen-kmp-test`, which generates into a
 * `commonMain` source set from the same Gradle build that defines the plugin, and so has to invoke the generator
 * as a plain JVM process.
 * */
fun main(args: Array<String>) {
    require(args.size == 3 || args.size == 4) {
        "Usage: <abi-directory> <output-directory> <package-name> [loader-prefix]"
    }

    val abiDir = File(args[0])
    val outputDir = File(args[1])
    val packageName = args[2]
    val loaderPrefix = if (args.size == 4) args[3] else "Default"

    require(abiDir.isDirectory) { "Not a directory: $abiDir" }

    outputDir.deleteRecursively()
    outputDir.mkdirs()

    val errorLoaderBuilder = ErrorLoaderBuilder(loaderPrefix, outputDir)

    // sorted so the generated loader lists contracts in a stable order, keeping the task output reproducible
    val abiFiles = abiDir.walkTopDown()
        .filter { it.isFile && it.extension == "json" }
        .sortedBy { it.name }

    for (abiFile in abiFiles) {
        val abi = JsonAbiReaderRegistry.readAbiOrNull(abiFile.toURI().toURL())
            ?: throw IllegalArgumentException("Invalid ABI: ${abiFile.path}")

        val canonicalName = AbiContractBuilder(
            abiFile.name.removeSuffix(".json"),
            packageName,
            outputDir,
            abi,
            emptyMap(),
            generateMiddlewareExtensions = true,
        ).build(errorLoaderBuilder.canonicalName)

        errorLoaderBuilder.addContract(canonicalName)
    }

    errorLoaderBuilder.build()
}
