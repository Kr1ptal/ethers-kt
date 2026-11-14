package io.ethers.abigen.plugin

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Adds the kotlinx.serialization compiler plugin to every Kotlin compilation when
 * [EthersSolanaIdlExtension.kotlinxSerialization] is enabled, so that `Json.encodeToString(value)` finds the
 * serializers of generated program bindings without the project applying `org.jetbrains.kotlin.plugin.serialization`.
 *
 * It is always applied, and the flag is read in [isApplicable], which the Kotlin Gradle plugin calls once the build
 * script has been evaluated. The compiler plugin artifact has no version, so the Kotlin Gradle plugin resolves it at its
 * own version, which always matches the compiler. A project that applies the serialization plugin itself keeps it, and
 * this one stays out of the way, whichever of the two was applied first.
 */
abstract class KotlinxSerializationSubplugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) = Unit

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.findByType(EthersSolanaIdlExtension::class.java) ?: return false
        return extension.kotlinxSerialization.get() && !hasSerializationPlugin(project)
    }

    /** Whether another plugin, normally the official serialization plugin, already adds the compiler plugin. */
    private fun hasSerializationPlugin(project: Project): Boolean = project.plugins.any {
        it.javaClass.name == SERIALIZATION_GRADLE_PLUGIN ||
            (it !== this && it is KotlinCompilerPluginSupportPlugin && it.getCompilerPluginId() == COMPILER_PLUGIN_ID)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> = kotlinCompilation.target.project.provider { emptyList() }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    // no version: the Kotlin Gradle plugin uses its own, as for its bundled subplugins; the embeddable artifact also
    // serves Kotlin/Native compilations
    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact("org.jetbrains.kotlin", "kotlin-serialization-compiler-plugin-embeddable")

    internal companion object {
        const val COMPILER_PLUGIN_ID = "org.jetbrains.kotlinx.serialization"
        const val SERIALIZATION_GRADLE_PLUGIN = "org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin"
    }
}
