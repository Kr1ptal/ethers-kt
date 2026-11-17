package io.ethers.abigen.plugin

import io.ethers.abigen.plugin.source.SolanaIdlDirectorySource
import io.ethers.solana.idl.generator.SeedByteOrder
import io.ethers.solana.idl.generator.SeedByteOrders
import io.ethers.solana.idl.reader.SolanaIdlReader
import io.ethers.solana.idl.reader.SolanaIdlReaderRegistry
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class EthersSolanaIdlExtension(private val project: Project) {
    private val factory = project.objects

    /**
     * Directory where the generated Kotlin files will be placed. Path is relative to the project build directory.
     *
     * Default value is `generated/source/ethers-solana/main/kotlin`.
     * */
    val outputDir: Property<String> = factory.property(String::class.java).convention(
        "generated/source/ethers-solana/main/kotlin",
    )

    /**
     * IDL sources. Bindings are generated for every IDL they contain.
     *
     * Default list contains [directorySource] for `src/main/idl`, which [directorySource] adds to. Set the list to
     * replace it.
     * */
    val sources: ListProperty<SolanaIdlDirectorySource> =
        factory.listProperty(SolanaIdlDirectorySource::class.java).value(
            listOf(createDirectorySource("src/main/idl")),
        )

    /**
     * Whether a declaration of an IDL that cannot be generated, such as a type with a layout the IDL cannot describe,
     * fails the build. By default it is left out of the bindings, together with everything that depends on it, and
     * logged as a warning; the generated program object's documentation lists it too. Strict mode fails the build
     * listing every such declaration of every IDL, which suits CI, where gaps in the bindings should not go unnoticed.
     *
     * Default value is `false`.
     * */
    val strict: Property<Boolean> = factory.property(Boolean::class.javaObjectType).convention(false)

    /**
     * Deployment addresses that replace the address an IDL declares, keyed by the program name the IDL declares. The
     * generated `PROGRAM_ID` is the override, e.g. for a program deployed to devnet or a local validator under another
     * address. Naming a program that no IDL declares fails the build.
     *
     * Default value is empty map.
     * */
    val programAddresses: MapProperty<String, String> =
        factory.mapProperty(String::class.java, String::class.java).convention(emptyMap())

    /**
     * Replace the address the IDL of [program] declares with [address], see [programAddresses].
     * */
    fun programAddress(program: String, address: String) {
        programAddresses.put(program, address)
    }

    /**
     * Byte order of integer PDA seeds, keyed by the program name the IDL declares. IDLs do not record the byte order
     * a program encodes its integer seeds in, so the generated `find` functions assume little-endian unless set here.
     * Naming a program that no IDL declares, or a seed that is not a multi-byte integer seed, fails the build.
     *
     * Default value is empty map.
     * */
    val seedByteOrders: MapProperty<String, SeedByteOrders> =
        factory.mapProperty(String::class.java, SeedByteOrders::class.java).convention(emptyMap())

    /**
     * Encode every integer PDA seed of [program] in [order], see [seedByteOrders].
     * */
    fun seedByteOrder(program: String, order: SeedByteOrder) {
        seedByteOrders.put(program, byteOrders(program).copy(default = order))
    }

    /**
     * Encode the integer seed at [seed] of the PDA account named [account] of [program] in [order], in every
     * instruction that has the account, see [seedByteOrders]. [seed] is the seed's path as the IDL declares it, e.g.
     * `index` or `position.tick_lower_index`.
     * */
    fun seedByteOrder(program: String, account: String, seed: String, order: SeedByteOrder) {
        val current = byteOrders(program)
        seedByteOrders.put(program, current.copy(seeds = current.seeds + (SeedByteOrders.Seed(account, seed) to order)))
    }

    private fun byteOrders(program: String): SeedByteOrders = seedByteOrders.getting(program).orNull ?: SeedByteOrders()

    /**
     * Add additional [SolanaIdlReader] to the registry, which will be used to read IDL files.
     * */
    fun idlReader(reader: SolanaIdlReader) {
        SolanaIdlReaderRegistry.appendReader(reader)
    }

    /**
     * Add directory source, in addition to the default `src/main/idl` one. Paths are relative to the project root
     * directory.
     * */
    @JvmOverloads
    fun directorySource(path: String, action: Action<in SolanaIdlDirectorySource>? = null) {
        val source = createDirectorySource(path)
        action?.execute(source)
        sources.add(source)
    }

    private fun createDirectorySource(path: String): SolanaIdlDirectorySource {
        val source = factory.newInstance(SolanaIdlDirectorySource::class.java)
        source.sourceDirectory.set(project.layout.projectDirectory.dir(path))
        return source
    }
}
