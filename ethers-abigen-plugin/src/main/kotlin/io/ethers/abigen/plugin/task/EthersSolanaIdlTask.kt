package io.ethers.abigen.plugin.task

import io.ethers.abigen.plugin.source.SolanaIdlDirectorySource
import io.ethers.solana.idl.generator.GeneratorOverrides
import io.ethers.solana.idl.generator.SeedByteOrders
import io.ethers.solana.idl.generator.SolanaProgramBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.TreeMap
import javax.inject.Inject

@CacheableTask
abstract class EthersSolanaIdlTask @Inject constructor(private val executor: WorkerExecutor) : DefaultTask() {

    @get:Inject
    abstract val objectFactory: ObjectFactory

    @get:Inject
    abstract val projectLayout: ProjectLayout

    init {
        group = "ethers"
        description = "Generate Kotlin program bindings from Solana IDL files"
    }

    /**
     * IDL sources. Bindings are generated for every IDL they contain.
     * */
    @get:Nested
    abstract val sources: ListProperty<SolanaIdlDirectorySource>

    /**
     * Deployment addresses that replace the address an IDL declares, keyed by the program name the IDL declares.
     * */
    @get:Input
    abstract val programAddresses: MapProperty<String, String>

    /**
     * Byte order of integer PDA seeds, keyed by the program name the IDL declares.
     * */
    @get:Input
    abstract val seedByteOrders: MapProperty<String, SeedByteOrders>

    /**
     * Generator overrides, keyed by the program name the IDL declares, see [GeneratorOverrides]. Tracked as an input
     * through [sortedGeneratorOverrides], so the order they were configured in does not change the cache key.
     * */
    @get:Internal
    abstract val generatorOverrides: MapProperty<String, GeneratorOverrides>

    /**
     * [generatorOverrides] sorted by program, whose overrides are sorted by target themselves.
     * */
    @get:Input
    internal val sortedGeneratorOverrides: Provider<Map<String, GeneratorOverrides>>
        get() = generatorOverrides.map { TreeMap(it) }

    /**
     * JSON files of generator overrides, in the format [GeneratorOverrides.parse] reads, merged with
     * [generatorOverrides].
     * */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val overridesFiles: ConfigurableFileCollection

    /**
     * Directory where the generated Kotlin files will be placed. Path is relative to the project build directory.
     * */
    @get:Input
    abstract val outputDir: Property<String>

    /**
     * Directory where the generated Kotlin files will be placed. This property should be modified via [outputDir]
     * so all paths are relative to the project build directory.
     * */
    @get:OutputDirectory
    internal val destinationDir: DirectoryProperty = objectFactory.directoryProperty().convention(
        projectLayout.buildDirectory.dir(outputDir),
    )

    /**
     * Version indicating the output format, [SolanaProgramBuilder.OUTPUT_VERSION]. A new version of the generator
     * invalidates previous output automatically.
     * */
    @get:Input
    internal val outputVersion: Property<String> = objectFactory.property(String::class.java).convention(SolanaProgramBuilder.OUTPUT_VERSION)

    /**
     * Directory for temporary generation results. This property is used instead of direct project.layout access
     * during task execution to ensure configuration cache compatibility.
     * */
    @get:Internal
    internal val resultsDirectory: DirectoryProperty = objectFactory.directoryProperty().convention(
        projectLayout.buildDirectory.dir("tmp/solana-idl-results/"),
    )

    /** [generatorOverrides] merged with those of [overridesFiles], which must not configure one target differently. */
    private fun mergedOverrides(): Map<String, GeneratorOverrides> {
        var merged: Map<String, GeneratorOverrides> = TreeMap(generatorOverrides.get())
        // sorted, so a conflict is reported alike in any configuration order
        for (file in overridesFiles.files.sortedBy { it.path }) {
            merged = try {
                GeneratorOverrides.mergeAll(merged, GeneratorOverrides.parse(file.readText()))
            } catch (e: IllegalArgumentException) {
                throw GradleException("Invalid generator overrides in $file: ${e.message}", e)
            }
        }
        return merged
    }

    @TaskAction
    fun run() {
        // delete previous outputs, so bindings of removed or renamed IDLs are not restored from the build cache,
        // see EthersAbigenTask
        outputs.previousOutputFiles.forEach { it.delete() }

        val destDir = destinationDir.get().asFile
        val resultsDir = resultsDirectory.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val queue = executor.noIsolation()
        val overrides = mergedOverrides()

        val idls = sources.get().flatMap { it.getSources() }
        idls.forEachIndexed { index, source ->
            queue.submit(SolanaIdlWork::class.java) {
                it.idl.set(source)
                it.destination.set(destDir)
                it.programAddresses.set(programAddresses)
                it.seedByteOrders.set(seedByteOrders)
                it.generatorOverrides.set(overrides)
                it.resultFile.set(File(resultsDir, index.toString()))
            }
        }

        queue.await()

        val programNames = HashSet<String>()
        val generatedBy = HashMap<String, String>()
        idls.forEachIndexed { index, source ->
            val (programName, canonicalName) = File(resultsDir, index.toString()).readLines()
            programNames.add(programName)
            generatedBy.put(canonicalName, source.toString())?.let { previous ->
                throw GradleException("IDLs $previous and $source both generate $canonicalName; move one to another package")
            }
        }

        for ((property, programs) in listOf(
            "programAddresses" to programAddresses.get().keys,
            "seedByteOrders" to seedByteOrders.get().keys,
            "generatorOverrides" to overrides.keys,
        )) {
            val unknown = programs - programNames
            if (unknown.isNotEmpty()) {
                throw GradleException("$property names programs that no IDL declares: ${unknown.sorted()}. Declared programs: ${programNames.sorted()}")
            }
        }
    }
}
