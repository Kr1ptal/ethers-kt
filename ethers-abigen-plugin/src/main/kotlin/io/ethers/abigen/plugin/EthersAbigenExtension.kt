package io.ethers.abigen.plugin

import io.ethers.abigen.plugin.source.AbiSourceProvider
import io.ethers.abigen.plugin.source.DirectorySourceProvider
import io.ethers.abigen.plugin.source.FoundrySourceProvider
import io.ethers.abigen.reader.JsonAbiReader
import io.ethers.abigen.reader.JsonAbiReaderRegistry
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class EthersAbigenExtension(private val project: Project) {
    private val factory = project.objects

    /**
     * Directory where the generated Kotlin files will be placed. Path is relative to the project build directory.
     *
     * Default value is `generated/source/ethers/main/kotlin`.
     * */
    val outputDir: Property<String> = factory.property(String::class.java).convention(
        "generated/source/ethers/main/kotlin",
    )

    /**
     * ABI source providers. ABIs returned by these providers will be used to generate contract wrappers.
     *
     * Default list contains [directorySource] provider for `src/main/abi`.
     * */
    val sourceProviders: ListProperty<AbiSourceProvider> =
        factory.listProperty(AbiSourceProvider::class.java).convention(
            listOf(createDirectorySourceProvider("src/main/abi")),
        )

    /**
     * Remapping of function names. This is useful when the ABI contains functions names that produce optimized
     * selectors (e.g. all zeros), and are not particularly readable.
     *
     * Default value is empty map.
     * */
    val functionRenames: MapProperty<String, String> =
        project.objects.mapProperty(String::class.java, String::class.java)
            .convention(emptyMap())

    /**
     * Add additional [JsonAbiReader] to the registry, which will be used to read ABI files.
     * */
    fun abiReader(reader: JsonAbiReader) {
        JsonAbiReaderRegistry.appendReader(reader)
    }

    /**
     * Add directory source provider. Paths are relative to the project root directory.
     * */
    @JvmOverloads
    fun directorySource(path: String, action: Action<in DirectorySourceProvider>? = null) {
        val source = createDirectorySourceProvider(path)
        action?.execute(source)
        sourceProviders.add(source)
    }

    private fun createDirectorySourceProvider(path: String): DirectorySourceProvider {
        val source = factory.newInstance(DirectorySourceProvider::class.java)
        source.sourceDirectory.set(project.layout.projectDirectory.dir(path))
        return source
    }

    /**
     * Add foundry source provider which automatically builds the project using Foundry and reads the ABI files from
     * the output directory.
     *
     * Foundry root directory defaults to `src/main/solidity`, but can be changed by setting
     * [FoundrySourceProvider.foundryRoot].
     * */
    @JvmOverloads
    fun foundrySource(destinationPackage: String, action: Action<in FoundrySourceProvider>? = null) {
        val source = createFoundrySourceProvider(destinationPackage)
        action?.execute(source)
        sourceProviders.add(source)
    }

    private fun createFoundrySourceProvider(destinationPackage: String): FoundrySourceProvider {
        val source = factory.newInstance(FoundrySourceProvider::class.java)
        source.destinationPackage.set(destinationPackage)
        return source
    }
}
