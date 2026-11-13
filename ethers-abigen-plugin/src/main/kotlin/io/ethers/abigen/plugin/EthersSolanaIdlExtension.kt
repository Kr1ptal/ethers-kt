package io.ethers.abigen.plugin

import io.ethers.abigen.plugin.source.SolanaIdlDirectorySource
import io.ethers.solana.idl.generator.GeneratorOverrides
import io.ethers.solana.idl.generator.SeedByteOrder
import io.ethers.solana.idl.generator.SeedByteOrders
import io.ethers.solana.idl.reader.SolanaIdlReader
import io.ethers.solana.idl.reader.SolanaIdlReaderRegistry
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
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
     * Generator overrides, keyed by the program name the IDL declares: handwritten codecs, type substitutions, renames
     * and instruction account defaults, see [GeneratorOverrides]. [customCodec], [typeSubstitution], [rename] and
     * [accountDefault] add to it, and [overridesFile] reads more from JSON files. Naming a program that no IDL
     * declares, or a target its IDL does not declare, fails the build, as does configuring one target in two
     * different ways.
     *
     * Default value is empty map.
     * */
    val generatorOverrides: MapProperty<String, GeneratorOverrides> =
        factory.mapProperty(String::class.java, GeneratorOverrides::class.java).convention(emptyMap())

    /**
     * JSON files of generator overrides for any number of programs, in the format [GeneratorOverrides.parse] reads,
     * merged with [generatorOverrides]. They are inputs of the generating task, so changing one regenerates the
     * bindings.
     * */
    val overridesFiles: ConfigurableFileCollection = factory.fileCollection()

    /**
     * Read the IDL type [type] of [program] with the handwritten [codec], the fully qualified name of a Kotlin `object`
     * implementing `SolanaCodec` of the type's generated class, e.g. for a type declared with custom serialization.
     * Declare [size] only if the codec reads and writes that many bytes for every value, and [align] where an
     * unpacked `#[repr(C)]` zero-copy type contains it; see [GeneratorOverrides.CustomCodec].
     * */
    @JvmOverloads
    fun customCodec(program: String, type: String, codec: String, size: Int? = null, align: Int? = null) {
        override(program) { it.withCustomCodec(type, GeneratorOverrides.CustomCodec(codec, size, align)) }
    }

    /**
     * Use the existing Kotlin class [kotlinType], read and written by the Kotlin `object` [codec], wherever [program]
     * uses its IDL type [type], which then gets no class of its own. [size] and [align] are declared as for
     * [customCodec]; see [GeneratorOverrides.TypeSubstitution].
     * */
    @JvmOverloads
    fun typeSubstitution(program: String, type: String, kotlinType: String, codec: String, size: Int? = null, align: Int? = null) {
        override(program) { it.withTypeSubstitution(type, GeneratorOverrides.TypeSubstitution(kotlinType, codec, size, align)) }
    }

    /**
     * Give the declaration [target] of [program] the Kotlin name [name]. [target] is `<kind>:<path>` as the IDL names
     * it, e.g. `type:Mode`, `field:Config.fee_bps` or `instructionAccount:swap.vaults.input`; see
     * [GeneratorOverrides.RENAME_KINDS].
     * */
    fun rename(program: String, target: String, name: String) {
        override(program) { it.withRename(target, name) }
    }

    /**
     * Default the instruction account [account] of [program], `<instruction>.<account path>` as the IDL names it, to
     * the base58 [address], e.g. `accountDefault("escrow", "deposit.token_program", "Tokenkeg...")`.
     * */
    fun accountDefault(program: String, account: String, address: String) {
        override(program) { it.withAccountDefault(account, address) }
    }

    /**
     * Read generator overrides from the JSON file at [path], relative to the project directory, see [overridesFiles].
     * */
    fun overridesFile(path: Any) {
        overridesFiles.from(path)
    }

    private fun override(program: String, change: (GeneratorOverrides) -> GeneratorOverrides) {
        val current = generatorOverrides.getting(program).orNull ?: GeneratorOverrides()
        val changed = try {
            change(current)
        } catch (e: IllegalArgumentException) {
            throw GradleException("ethersSolanaIdl overrides of program '$program': ${e.message}", e)
        }
        generatorOverrides.put(program, changed)
    }

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
