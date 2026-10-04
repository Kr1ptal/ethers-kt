package io.ethers.solana.idl

import io.ethers.solana.idl.generator.SeedByteOrder
import io.ethers.solana.idl.generator.SeedByteOrders
import io.ethers.solana.idl.generator.SolanaProgramBuilder
import io.ethers.solana.idl.reader.SolanaIdlReaderRegistry
import java.io.File

private const val USAGE = "Usage: <idl-directory> <output-directory> <package-name> [--idl <idl-file>]... " +
    "[--seed-byte-order <program>[:<account>:<seed>]=<LITTLE_ENDIAN|BIG_ENDIAN>]..."

/**
 * Headless entry point for [SolanaProgramBuilder]: generates bindings for every IDL file in a directory, and for each
 * file `--idl` names.
 *
 * ```
 * <idl-directory> <output-directory> <package-name> [--idl <idl-file>]... [--seed-byte-order <program>[:<account>:<seed>]=<order>]...
 * ```
 *
 * `--seed-byte-order` sets the byte order of a program's integer PDA seeds, or of one seed of one of its PDA
 * accounts, see [SeedByteOrders].
 *
 * This exists for builds that cannot apply a Gradle plugin, notably this module's own tests, which generate
 * bindings into `commonTest` as a plain JVM process.
 * */
fun main(args: Array<String>) {
    require(args.size >= 3 && (args.size - 3) % 2 == 0) { USAGE }

    val idlDir = File(args[0])
    val outputDir = File(args[1])
    val packageName = args[2]
    require(idlDir.isDirectory) { "Not a directory: $idlDir" }

    val extraIdls = ArrayList<File>()
    val byteOrders = HashMap<String, SeedByteOrders>()
    for (i in 3 until args.size step 2) {
        if (args[i] == "--idl") {
            extraIdls.add(File(args[i + 1]).also { require(it.isFile) { "Not a file: $it" } })
            continue
        }
        require(args[i] == "--seed-byte-order") { USAGE }
        val (target, order) = args[i + 1].split('=').also { require(it.size == 2) { USAGE } }
        val parts = target.split(':')
        val current = byteOrders[parts[0]] ?: SeedByteOrders()
        byteOrders[parts[0]] = when (parts.size) {
            1 -> current.copy(default = SeedByteOrder.valueOf(order))
            3 -> current.copy(seeds = current.seeds + (SeedByteOrders.Seed(parts[1], parts[2]) to SeedByteOrder.valueOf(order)))
            else -> throw IllegalArgumentException(USAGE)
        }
    }

    outputDir.deleteRecursively()
    outputDir.mkdirs()

    val programs = HashSet<String>()
    val idlFiles = idlDir.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedBy { it.name } + extraIdls
    for (idlFile in idlFiles) {
        val program = SolanaIdlReaderRegistry.tryReadIdl(idlFile.toURI().toURL()).unwrap()
        programs.add(program.name)
        SolanaProgramBuilder(program, packageName, seedByteOrders = byteOrders[program.name] ?: SeedByteOrders()).writeTo(outputDir)
    }
    val unknown = byteOrders.keys - programs
    require(unknown.isEmpty()) { "--seed-byte-order names programs that no IDL declares: ${unknown.sorted()}" }
}
