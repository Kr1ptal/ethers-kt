package io.ethers.abigen.plugin.source

import org.gradle.api.Project
import org.gradle.api.file.Directory
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
open class DirectorySourceProvider(project: Project, path: String) : AbiSourceProvider {
    /**
     * Provide a custom package name for the generated Kotlin files. If not set, the package name will be
     * derived from the directory structure.
     * */
    @get:Optional
    @get:Input
    var packageOverride: String? = null

    /**
     * Directory containing the ABI files. Path is relative to the project root directory.
     * */
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    val directory: Directory = project.layout.projectDirectory.dir(path)

    override fun getSources(): List<AbiSource> {
        val ret = ArrayList<AbiSource>()

        val dir = directory.asFile
        dir.walkTopDown().filter(File::isFile).forEach { file ->
            val contractName = file.nameWithoutExtension

            val destinationPackage = (packageOverride ?: file.normalizedPath())
                .substringAfter(dir.normalizedPath())
                .substringBeforeLast("/")
                .removePrefix("/")
                .replace("/", ".")

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
}
