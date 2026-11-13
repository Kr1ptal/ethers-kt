package io.ethers.abigen.plugin.source

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Property
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import java.io.File
import java.io.Serializable
import java.net.URL

/**
 * Reads Solana program IDL files (`*.json`) from a directory. The directory structure will be used to determine the
 * package name of the generated Kotlin files, unless [packageOverride] is set.
 * */
abstract class SolanaIdlDirectorySource {
    /**
     * Provide a custom package name for the generated Kotlin files. If not set, the package name will be
     * derived from the directory structure.
     * */
    @get:Optional
    @get:Input
    abstract val packageOverride: Property<String>

    /**
     * Directory containing the IDL files. Path is relative to the project root directory. A missing directory
     * contains no IDLs.
     * */
    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    /**
     * The IDL files in [sourceDirectory].
     * */
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val idlFiles: FileTree
        get() = sourceDirectory.asFileTree.matching { it.include("**/*.json") }

    /**
     * Returns the IDL files in [sourceDirectory], in a stable order.
     * */
    @Internal
    fun getSources(): List<SolanaIdlSource> {
        val dir = sourceDirectory.get().asFile
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .sortedBy { it.relativeTo(dir).invariantSeparatorsPath }
            .map { file ->
                val destinationPackage = DirectorySourceProvider.destinationPackage(dir, file, packageOverride.orNull, DEFAULT_PACKAGE)
                SolanaIdlSource(destinationPackage, file.toURI().toURL())
            }
            .toList()
    }

    companion object {
        private const val DEFAULT_PACKAGE = "io.ethers.solana.generated"
    }
}

/**
 * A single IDL file, whose program bindings are generated into [destinationPackage].
 * */
data class SolanaIdlSource(
    val destinationPackage: String,
    val idlUrl: URL,
) : Serializable {
    /** The IDL file's path. */
    override fun toString(): String = File(idlUrl.toURI()).path

    companion object {
        @Transient
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 7362091849210L
    }
}
