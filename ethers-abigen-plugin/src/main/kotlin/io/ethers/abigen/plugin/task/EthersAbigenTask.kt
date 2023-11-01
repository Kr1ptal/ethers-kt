package io.ethers.abigen.plugin.task

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

    @TaskAction
    fun run() {
        val destDir = destinationDir.get().asFile

        sourceProviders.get().parallelStream().forEach { provider ->
            provider.getSources().forEach { source ->
                executor.noIsolation().submit(AbigenWork::class.java) {
                    it.abi.set(source)
                    it.destination.set(destDir)
                    it.functionRenames.set(functionRenames)
                }
            }
        }
    }
}
