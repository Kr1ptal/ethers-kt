package io.ethers.abigen.plugin.source

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.defaultDecoders
import com.sksamuel.hoplite.toml.TomlParser
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * A Foundry [AbiSourceProvider] that builds the foundry project and reads the ABI files from the output directory.
 * Only contracts in from the source directory will be included. The contracts will be placed into [destinationPackage]
 * package, replicating the foundry source directory structure.
 */
abstract class FoundrySourceProvider : AbiSourceProvider {

    @get:Inject
    abstract val objectFactory: ObjectFactory

    @get:Inject
    abstract val projectLayout: ProjectLayout

    @get:Inject
    abstract val providerFactory: ProviderFactory

    @get:Inject
    abstract val logger: Logger

    /**
     * The parent package name of the generated Kotlin files.
     */
    @get:Input
    abstract val destinationPackage: Property<String>

    // load the config file
    private val config = providerFactory.provider { getFoundryConfig(foundryConfigFile.get()) }
    private val srcDirProvider = providerFactory.provider { config.get().src }
    private val outDirProvider = providerFactory.provider { config.get().out }

    /**
     * The root directory of the foundry project, which contains the `foundry.toml` file. Defaults to
     * `src/main/solidity`.
     * */
    @get:Optional
    @get:Input
    val foundryRoot: Property<String> = objectFactory.property(String::class.java).convention("src/main/solidity")

    /**
     * Foundry profile to use when building the project. Defaults to `default`. Will be passed to the `FOUNDRY_PROFILE`
     * environment variable.
     * */
    @get:Optional
    @get:Input
    val foundryProfile: Property<String> = objectFactory.property(String::class.java).convention("default")

    /**
     * List of glob patterns to filter out contracts that are not part of the source directory. Defaults to empty
     * which includes all contracts.
     *
     * Filters are evaluated relative to the [srcDir] directory. E.g. if the contract is `contracts/erc/ERC20.sol`,
     * the glob pattern evaluates path without the `contracts` prefix, so only `erc/ERC20.sol` is being matched.
     * */
    @get:Optional
    @get:Input
    @Suppress("UNCHECKED_CAST")
    val contractGlobFilters: Property<List<String>> =
        objectFactory.property(List::class.java as Class<List<String>>).convention(emptyList())

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    val foundryConfigFile: RegularFileProperty = objectFactory.fileProperty().convention(
        projectLayout.projectDirectory.dir(foundryRoot).map { it.file("foundry.toml") },
    )

    /**
     * Directory containing the source files.
     * */
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    val srcDir: DirectoryProperty = objectFactory.directoryProperty().convention(
        projectLayout.projectDirectory.dir(foundryRoot).flatMap { it.dir(srcDirProvider) },
    )

    /**
     * Root directory where foundry project is located.
     * */
    @get:InputDirectory
    val foundryRootDir: DirectoryProperty = objectFactory.directoryProperty().convention(
        projectLayout.projectDirectory.dir(foundryRoot),
    )

    override fun getSources(): List<AbiSource> {
        val ret = ArrayList<AbiSource>()

        forgeBuild()

        val jackson = ObjectMapper()
        val srcDir = srcDir.asFile.get()
        val outDir = foundryRootDir.get().dir(outDirProvider.get()).asFile
        val config = config.get()
        val globMatchers = contractGlobFilters.get().map { FileSystems.getDefault().getPathMatcher("glob:$it") }
        outDir.walkTopDown()
            .filter(File::isFile)
            .filter(::isMainContractJson)
            .forEach {
                logger.info("Found ABI file: ${it.absolutePath}")

                val json = jackson.readTree(it)
                if (json.get("abi").isEmpty) {
                    logger.info("Skipping, no external/public ABI functions: ${it.absolutePath}")
                    return@forEach
                }

                // make sure metadata is present so we can replicate the package structure from the compilation target
                val compilationTarget = json.get("metadata")?.get("settings")?.get("compilationTarget")
                    ?: throw IllegalStateException("Compilation target not found in ${it.absolutePath}")

                val relativePaths = ArrayList<String>()
                (compilationTarget as ObjectNode).properties().iterator()
                    .forEach { entry -> relativePaths.add(entry.key) }

                // find the relative path of the contract in the src dir
                val relativePath = relativePaths
                    .firstOrNull { p -> p.startsWith("${config.src}/") && p.endsWith("${it.nameWithoutExtension}.sol") }

                // TODO if no relative path it means that the contract inside the file has a different name than the
                //      file name. We should probably generate all the contracts in the file in that case.
                if (relativePath == null) {
                    return@forEach
                }

                val sourceFile = File(srcDir, relativePath.substringAfter("/"))
                if (!matchesGlobPatterns(sourceFile, srcDir, globMatchers)) {
                    logger.info("Skipping, does not match any glob pattern: ${sourceFile.absolutePath}")
                    return@forEach
                }

                var destinationPackage = this@FoundrySourceProvider.destinationPackage.get()

                // if contract is not in root of src dir, replicate the package structure from the compilation target
                if (relativePath.count { c -> c == '/' } > 1) {
                    destinationPackage += "." + relativePath.substringAfter("/")
                        .substringBeforeLast("/")
                        .replace("/", ".")
                }

                val contractName = it.nameWithoutExtension
                ret.add(AbiSource(contractName, destinationPackage, it.toURI().toURL()))
            }

        return ret
    }

    private fun matchesGlobPatterns(file: File, srcDir: File, matchers: List<PathMatcher>): Boolean {
        return matchers.isEmpty() || matchers.any { it.matches(file.relativeTo(srcDir).toPath()) }
    }

    /**
     * Filter out extra data files like `ContactName.metadata.json` and take only the main contract json file, which
     * has the same name as the directory it's in. The directory contains also other inherited contract files e.g.
     * `Ownable.json`. If the directory is `../ERC20.sol/`, take only the file named `ERC20.json`.
     * */
    private fun isMainContractJson(file: File): Boolean {
        return file.name.count { c -> c == '.' } == 1 &&
            file.nameWithoutExtension == file.parentFile.nameWithoutExtension &&
            file.extension == "json"
    }

    private fun forgeBuild() {
        val errorOutput = ByteArrayOutputStream()
        val commands = listOf("forge", "build", "--force", "--extra-output", "abi", "metadata", "evm.bytecode")

        val result = providerFactory.exec {
            it.commandLine(commands)
            it.environment("FOUNDRY_PROFILE", foundryProfile.get())
            it.workingDir = foundryRootDir.get().asFile
            it.errorOutput = errorOutput
        }.result.get()

        val cmd = commands.joinToString(" ")
        if (result.exitValue != 0) {
            val errorReader = ByteArrayInputStream(errorOutput.toByteArray()).bufferedReader()
            val error = errorReader.lines().collect(Collectors.toList()).last()

            logger.error("Foundry build failed for command `$cmd` and profile `${foundryProfile.get()}`: $error")
            result.rethrowFailure()
        }

        logger.info("Foundry build succeeded for command `$cmd` and profile `${foundryProfile.get()}`")
    }

    private fun getFoundryConfig(configFile: RegularFile): ProfileConfig {
        logger.info("Loading foundry config from ${configFile.asFile.absolutePath}")

        data class FoundryConfig(private val profile: Map<String, ProfileConfig>) {
            fun getProfile(name: String): ProfileConfig {
                return profile[name] ?: profile["default"] ?: ProfileConfig()
            }
        }

        val loader = ConfigLoaderBuilder.empty()
            .addDecoders(defaultDecoders)
            .addDefaultPreprocessors()
            .addDefaultParamMappers()
            .addDefaultPropertySources()
            .addDefaultParsers()
            .addFileExtensionMapping("toml", TomlParser())
            .addFileSource(configFile.asFile, optional = false, allowEmpty = true)
            .build()

        val result = loader.loadConfig<FoundryConfig>()
        result.onFailure {
            throw IllegalStateException("Failed to load foundry config: ${it.description()}")
        }

        return result.getUnsafe().getProfile(foundryProfile.get())
    }

    internal data class ProfileConfig(
        val src: String = "src",
        val out: String = "out",
    )
}
