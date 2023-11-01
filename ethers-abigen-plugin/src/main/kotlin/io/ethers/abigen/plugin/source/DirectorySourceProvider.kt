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
        dir.walkTopDown().filter(File::isFile).forEach {
            val contractName = it.nameWithoutExtension

            var destinationPackage = packageOverride
            if (destinationPackage == null) {
                destinationPackage = it.absolutePath
                    .substringAfter(dir.absolutePath)
                    .substringBeforeLast("/")
                    .removePrefix("/")
                    .replace("/", ".")
            }

            ret.add(AbiSource(contractName, destinationPackage, it.toURI().toURL()))
        }

        return ret
    }
}
