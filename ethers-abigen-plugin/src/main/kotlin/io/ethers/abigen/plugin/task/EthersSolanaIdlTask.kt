package io.ethers.abigen.plugin.task

import io.ethers.abigen.plugin.source.SolanaIdlDirectorySource
import io.ethers.solana.idl.generator.SolanaProgramBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
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

        val idls = sources.get().flatMap { it.getSources() }
        idls.forEachIndexed { index, source ->
            queue.submit(SolanaIdlWork::class.java) {
                it.idl.set(source)
                it.destination.set(destDir)
                it.programAddresses.set(programAddresses)
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

        val unknown = programAddresses.get().keys - programNames
        if (unknown.isNotEmpty()) {
            throw GradleException(
                "programAddresses names programs that no IDL declares: ${unknown.sorted()}. " +
                    "Declared programs: ${programNames.sorted()}",
            )
        }
    }
}
