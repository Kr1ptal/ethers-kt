package io.ethers.abigen.plugin.source

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import java.io.File

/**
 * [AbiSourceProvider] that reads ABI files from a directory. The directory structure will be used to determine
 * the package name of the generated Kotlin files, unless [packageOverride] is set.
 * */
abstract class DirectorySourceProvider : AbiSourceProvider {
    /**
     * Provide a custom package name for the generated Kotlin files. If not set, the package name will be
     * derived from the directory structure.
     * */
    @get:Optional
    @get:Input
    abstract val packageOverride: Property<String>

    /**
     * Directory containing the ABI files. Path is relative to the project root directory.
     * */
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    override fun getSources(): List<AbiSource> {
        val ret = ArrayList<AbiSource>()

        val dir = sourceDirectory.get().asFile
        dir.walkTopDown().filter(File::isFile).forEach { file ->
            val contractName = file.nameWithoutExtension

            val destinationPackage = (packageOverride.orNull ?: file.normalizedPath())
                .substringAfter(dir.normalizedPath())
                .substringBeforeLast("/")
                .removePrefix("/")
                .replace("/", ".")
                .ifBlank { DEFAULT_PACKAGE }

            ret.add(AbiSource(contractName, destinationPackage, file.toURI().toURL()))
        }

        return ret
    }

    /**
     * Return normalized absolute path of the file, using `/` as a separator. This is mainly useful
     * for normalizing paths on Windows.
     *
     * Example:
     * ```
     * from: "C:\\ethers-kt\\ethers-abigen-plugin\\src\\main\\kotlin"
     * to:    "/ethers-kt/ethers-abigen-plugin/src/main/kotlin"
     * ```
     * */
    private fun File.normalizedPath(): String {
        return absolutePath.replace('\\', '/').split(":/").last()
    }

    companion object {
        private const val DEFAULT_PACKAGE = "io.ethers.abigen.generated"
    }
}
