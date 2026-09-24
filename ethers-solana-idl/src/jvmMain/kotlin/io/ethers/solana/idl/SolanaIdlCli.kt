package io.ethers.solana.idl

import io.ethers.solana.idl.generator.SolanaProgramBuilder
import io.ethers.solana.idl.reader.SolanaIdlReaderRegistry
import java.io.File

/**
 * Headless entry point for [SolanaProgramBuilder]: generates bindings for every IDL file in a directory.
 *
 * ```
 * <idl-directory> <output-directory> <package-name>
 * ```
 *
 * This exists for builds that cannot apply a Gradle plugin, notably this module's own tests, which generate
 * bindings into `commonTest` as a plain JVM process.
 * */
fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: <idl-directory> <output-directory> <package-name>" }

    val idlDir = File(args[0])
    val outputDir = File(args[1])
    val packageName = args[2]
    require(idlDir.isDirectory) { "Not a directory: $idlDir" }

    outputDir.deleteRecursively()
    outputDir.mkdirs()

    val idlFiles = idlDir.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedBy { it.name }
    for (idlFile in idlFiles) {
        val program = SolanaIdlReaderRegistry.tryReadIdl(idlFile.toURI().toURL()).unwrap()
        SolanaProgramBuilder(program, packageName).writeTo(outputDir)
    }
}
