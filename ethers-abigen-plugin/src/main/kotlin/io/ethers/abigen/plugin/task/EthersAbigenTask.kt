package io.ethers.abigen.plugin.task

import io.ethers.abigen.ErrorLoaderBuilder
import io.ethers.abigen.plugin.source.AbiSourceProvider
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.util.Collections
import java.util.UUID
import javax.inject.Inject

@CacheableTask
abstract class EthersAbigenTask @Inject constructor(private val executor: WorkerExecutor) : ConventionTask() {
    init {
        group = "ethers"
        description = "Generate Kotlin code from Solidity ABI files"
    }

    /**
     * ABI source providers. ABIs returned by these providers will be used to generate contract wrappers.
     * */
    @get:Nested
    abstract val sourceProviders: ListProperty<AbiSourceProvider>

    /**
     * Remapping of function names. This is useful when the ABI contains functions names that produce optimized
     * selectors (e.g. all zeros), and are not particularly readable.
     * */
    @get:Input
    abstract val functionRenames: MapProperty<String, String>

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
    @Suppress("LeakingThis")
    internal val destinationDir: DirectoryProperty = project.objects.directoryProperty().convention(
        project.layout.buildDirectory.dir(outputDir),
    )

    /**
     * Version indicating the output format. This is useful when the abigen builder changes its output format, and
     * causes the new version of the plugin to invalidate previous output automatically.
     * */
    @get:Input
    internal val outputVersion: Property<String> = project.objects.property(String::class.java).convention("1")

    @TaskAction
    fun run() {
        // Since the task action is being executed, it means that inputs of the task have been changed. In this case,
        // first delete previous outputs if any exist. This prevents a case where user changes inputs without changing
        // "destinationDir" and does not run the "clean" task before the next build, and the old outputs are still
        // present. Gradle task will include both new and old task outputs in the new build cache. Now even if we
        // manually run "clean" task or delete the old output files, they will always get restored from the build cache.
        //
        // Solution: https://discuss.gradle.org/t/is-there-a-way-to-delete-prior-runs-output-for-a-task/28834
        outputs.previousOutputFiles.forEach { it.delete() }

        val destDir = destinationDir.get().asFile

        var loaderPrefix = project.path.split(":")
            .map { it.replaceFirstChar { c -> c.uppercase() } }
            .joinToString(separator = "") { it }
            .trim()

        if (loaderPrefix.isBlank()) {
            loaderPrefix = "Default"
        }

        val errorLoaderBuilder = ErrorLoaderBuilder(loaderPrefix, destDir)
        val results = Collections.synchronizedList(ArrayList<File>())

        val resultsDir = File(project.layout.buildDirectory.get().asFile, "tmp/abigen-results/").apply { mkdirs() }
        val queue = executor.noIsolation()

        sourceProviders.get().parallelStream().forEach { provider ->
            provider.getSources().forEach { source ->
                val resultFile = File(resultsDir, UUID.randomUUID().toString())
                results.add(resultFile)

                queue.submit(AbigenWork::class.java) {
                    it.abi.set(source)
                    it.destination.set(destDir)
                    it.functionRenames.set(functionRenames)
                    it.errorLoaderName.set(errorLoaderBuilder.canonicalName)
                    it.canonicalNameFile.set(resultFile)
                }
            }
        }

        queue.await()

        results.forEach {
            val canonicalName = it.readText()
            errorLoaderBuilder.addContract(canonicalName)
        }

        errorLoaderBuilder.build()
    }
}
