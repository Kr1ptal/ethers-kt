package io.ethers.solana.idl

import io.ethers.solana.idl.generator.SeedByteOrder
import io.ethers.solana.idl.generator.SeedByteOrders
import io.ethers.solana.idl.generator.SolanaIdlUnsupportedException
import io.ethers.solana.idl.generator.SolanaProgramBuilder
import io.ethers.solana.idl.reader.SolanaIdlReaderRegistry
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

private const val USAGE = "Usage: <idl-directory> <output-directory> <package-name> [--strict] [--idl <idl-file>]... " +
    "[--program-address <program>=<address>]... [--seed-byte-order <program>[:<account>:<seed>]=<LITTLE_ENDIAN|BIG_ENDIAN>]..."

/**
 * Headless entry point for [SolanaProgramBuilder]: generates bindings for every IDL file in a directory, and for each
 * file `--idl` names.
 *
 * ```
 * <idl-directory> <output-directory> <package-name> [--strict] [--idl <idl-file>]...
 *     [--program-address <program>=<address>]... [--seed-byte-order <program>[:<account>:<seed>]=<order>]...
 * ```
 *
 * `--program-address` replaces the address a program's IDL declares, or gives one to an IDL that declares none.
 * `--seed-byte-order` sets the byte order of a program's integer PDA seeds, or of one seed of one of its PDA
 * accounts, see [SeedByteOrders].
 *
 * Declarations that cannot be generated are left out, with a warning on standard error for each. With `--strict`,
 * every IDL is still processed, then the run fails listing the declarations of all of them that cannot be generated.
 *
 * This exists for builds that cannot apply a Gradle plugin, notably this module's own tests, which generate
 * bindings into `commonTest` as a plain JVM process.
 * */
fun main(args: Array<String>) {
    val status = runCli(args, System.err)
    if (status != 0) exitProcess(status)
}

/** Run the CLI with [args], reporting to [err]. The exit status: 0 on success, 1 when strict and something is unsupported. */
internal fun runCli(args: Array<String>, err: PrintStream): Int {
    val strict = "--strict" in args
    val options = args.filter { it != "--strict" }
    require(options.size >= 3 && (options.size - 3) % 2 == 0) { USAGE }

    val idlDir = File(options[0])
    val outputDir = File(options[1])
    val packageName = options[2]
    require(idlDir.isDirectory) { "Not a directory: $idlDir" }

    val extraIdls = ArrayList<File>()
    val addresses = HashMap<String, String>()
    val byteOrders = HashMap<String, SeedByteOrders>()
    for (i in 3 until options.size step 2) {
        if (options[i] == "--idl") {
            extraIdls.add(File(options[i + 1]).also { require(it.isFile) { "Not a file: $it" } })
            continue
        }
        if (options[i] == "--program-address") {
            val (program, address) = options[i + 1].split('=').also { require(it.size == 2) { USAGE } }
            addresses[program] = address
            continue
        }
        require(options[i] == "--seed-byte-order") { USAGE }
        val (target, order) = options[i + 1].split('=').also { require(it.size == 2) { USAGE } }
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
    val unsupported = ArrayList<String>()
    val idlFiles = idlDir.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedBy { it.name } + extraIdls
    for (idlFile in idlFiles) {
        val read = SolanaIdlReaderRegistry.tryReadIdl(idlFile.toURI().toURL()).unwrap()
        val program = addresses[read.name]?.let { read.copy(address = it) } ?: read
        programs.add(program.name)
        val builder = SolanaProgramBuilder(program, packageName, seedByteOrders = byteOrders[program.name] ?: SeedByteOrders())
        if (builder.diagnostics.isNotEmpty()) {
            val description = "${SolanaIdlUnsupportedException.describe(program.name, builder.diagnostics)} ($idlFile)"
            if (strict) unsupported.add(description) else err.println("warning: $description")
        }
        builder.writeTo(outputDir)
    }
    for ((option, names) in listOf("--seed-byte-order" to byteOrders.keys, "--program-address" to addresses.keys)) {
        val unknown = names - programs
        require(unknown.isEmpty()) { "$option names programs that no IDL declares: ${unknown.sorted()}" }
    }
    if (unsupported.isEmpty()) return 0
    err.println("error: strict generation failed, as some declarations cannot be generated:")
    unsupported.forEach(err::println)
    return 1
}
