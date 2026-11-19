package io.ethers.abigen.plugin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.io.File

private const val SERIALIZATION_PLUGIN = "org.jetbrains.kotlin.plugin.serialization"
private const val COMPILER_PLUGIN_ARTIFACT = "kotlin-serialization-compiler-plugin-embeddable"

/** The JVM artifacts of ethers-solana and its dependencies, see this module's build script. */
private val consumerClasspath: List<String> = System.getProperty("ethers.solanaConsumerClasspath").split(File.pathSeparator)

/**
 * A JVM application applying the plugin, with the showcase IDL, whose `main` round-trips generated types through
 * `Json.encodeToString` and `decodeFromString` without naming their serializers.
 */
private class ConsumerBuild(extension: String, extraPlugin: String = "") {
    val dir: File = ProjectBuilder.builder().build().layout.projectDirectory.asFile

    init {
        val idl = File(EthersSolanaIdlSerializationTest::class.java.getResource("/idl/io/ethers/programs/showcase.json")!!.toURI())
        idl.copyTo(File(dir, "src/main/idl/io/ethers/programs/showcase.json"))

        @Language("gradle")
        val settings = """
            rootProject.name = 'ethers-solana-idl-json-consumer'

            buildCache {
                local {
                    directory '${File(dir, "local-build-cache").toURI()}'
                }
            }
        """.trimIndent()
        File(dir, "settings.gradle").writeText(settings)

        val files = consumerClasspath.joinToString(", ") { "'${it.replace("\\", "/")}'" }

        @Language("gradle")
        val build = """
            plugins {
                id 'application'
                id 'org.jetbrains.kotlin.jvm'
                $extraPlugin
                id 'io.kriptal.ethers.abigen-plugin'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation files($files)
            }

            application {
                mainClass = 'com.example.MainKt'
            }

            ethersSolanaIdl {
                $extension
            }

            tasks.register('printCompilerPlugins') {
                def plugins = configurations.kotlinCompilerPluginClasspathMain
                doLast {
                    plugins.resolvedConfiguration.resolvedArtifacts.each { println("compiler plugin: " + it.moduleVersion.id) }
                }
            }
        """.trimIndent()
        File(dir, "build.gradle").writeText(build)

        @Language("kotlin")
        val main = """
            package com.example

            import io.ethers.programs.Showcase
            import io.github.artificialpb.bignum.BigInteger
            import kotlinx.serialization.json.Json

            fun main() {
                val snapshots = listOf(
                    Showcase.Snapshot(BigInteger("340282366920938463463374607431768211455"), -1L),
                    Showcase.Snapshot(BigInteger("0"), Long.MIN_VALUE),
                )
                val json = Json.encodeToString(snapshots)
                check(Json.decodeFromString<List<Showcase.Snapshot>>(json) == snapshots)

                val modes = listOf<Showcase.Mode>(Showcase.Mode.Unit, Showcase.Mode.Fixed(BigInteger("1"), -2L))
                val modesJson = Json.encodeToString(modes)
                check(Json.decodeFromString<List<Showcase.Mode>>(modesJson) == modes)
                println("json: " + json + " " + modesJson)
            }
        """.trimIndent()
        File(dir, "src/main/kotlin/com/example/Main.kt").apply { parentFile.mkdirs() }.writeText(main)
    }

    /** Turn the project into a Kotlin Multiplatform one with a JVM target, whose bindings land in `commonMain`. */
    fun multiplatform(extension: String) {
        val files = consumerClasspath.joinToString(", ") { "'${it.replace("\\", "/")}'" }

        @Language("gradle")
        val build = """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.kriptal.ethers.abigen-plugin'
            }

            repositories {
                mavenCentral()
            }

            kotlin {
                jvm()
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files($files)
                        }
                    }
                }
            }

            ethersSolanaIdl {
                $extension
            }

            tasks.register('run', JavaExec) {
                def main = kotlin.jvm().compilations.main
                classpath = main.output.allOutputs + main.runtimeDependencyFiles
                mainClass = 'com.example.MainKt'
            }
        """.trimIndent()
        File(dir, "build.gradle").writeText(build)
        val main = File(dir, "src/main/kotlin/com/example/Main.kt")
        main.copyTo(File(dir, "src/jvmMain/kotlin/com/example/Main.kt"))
        // the IDL stays in src/main/idl, the plugin's default source
        File(dir, "src/main/kotlin").deleteRecursively()
    }

    fun runner(vararg args: String): GradleRunner = GradleRunner.create()
        .withProjectDir(dir)
        .withPluginClasspath()
        .withArguments(*args, "--build-cache", "--stacktrace")
        .forwardOutput()

    fun compilerPlugins(result: BuildResult): List<String> = result.output.lines().filter { it.startsWith("compiler plugin: ") }
}

class EthersSolanaIdlSerializationTest : FunSpec({
    val expectedJson = """json: [{"value":"340282366920938463463374607431768211455","at":"-1"},{"value":"0","at":"-9223372036854775808"}] ["Unit",{"Fixed":{"rate":"1","until":"-2"}}]"""

    test("the one-line flag wires the serialization compiler plugin, so Json finds the generated serializers") {
        val build = ConsumerBuild("kotlinxSerialization = true")
        val result = build.runner("run", "printCompilerPlugins").build()
        result.output shouldContain expectedJson
        // at the Kotlin Gradle plugin's own version, which is the compiler's
        build.compilerPlugins(result).filter { COMPILER_PLUGIN_ARTIFACT in it } shouldBe
            listOf("compiler plugin: org.jetbrains.kotlin:$COMPILER_PLUGIN_ARTIFACT:${System.getProperty("ethers.kotlinVersion")}")
    }

    test("the one-line flag also wires a Kotlin Multiplatform project's compilations") {
        val build = ConsumerBuild("")
        build.multiplatform("kotlinxSerialization = true")
        build.runner("run").build().output shouldContain expectedJson
    }

    test("a project that applies the serialization plugin itself keeps it, without a second registration") {
        val build = ConsumerBuild("kotlinxSerialization = true", "id '$SERIALIZATION_PLUGIN'")
        val result = build.runner("run", "printCompilerPlugins").build()
        result.output shouldContain expectedJson
        build.compilerPlugins(result).count { COMPILER_PLUGIN_ARTIFACT in it } shouldBe 1
    }

    test("without the flag, the compiler plugin is not wired and the bindings have no JSON form") {
        val build = ConsumerBuild("")
        File(build.dir, "src/main/kotlin/com/example/Main.kt").writeText("package com.example\n\nfun main() = println(io.ethers.programs.Showcase.PROGRAM_ID)\n")
        val result = build.runner("run", "printCompilerPlugins").build()
        result.output shouldContain "ShowcaseProgram1111111111111111111111111111"
        build.compilerPlugins(result).none { COMPILER_PLUGIN_ARTIFACT in it } shouldBe true
        File(build.dir, "build/generated/source/ethers-solana/main/kotlin/io/ethers/programs/Showcase.kt").readText().contains("kotlinx.serialization") shouldBe false
    }

    test("the configuration cache and build cache still apply with the flag") {
        val build = ConsumerBuild("kotlinxSerialization = true")
        build.runner("clean", "compileKotlin", "--configuration-cache").build().task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
        val reused = build.runner("clean", "compileKotlin", "--configuration-cache").build()
        reused.output shouldContain "Reusing configuration cache"
        reused.task(":ethersSolanaIdl")?.outcome shouldBe TaskOutcome.FROM_CACHE
        reused.task(":compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE
    }

    test("the subplugin reads the flag and steps aside for the serialization plugin, in either application order") {
        fun applicable(vararg plugins: String, enabled: Boolean = true): Boolean {
            val project = ProjectBuilder.builder().build()
            plugins.forEach(project.pluginManager::apply)
            project.extensions.getByType(EthersSolanaIdlExtension::class.java).kotlinxSerialization.set(enabled)
            (project as ProjectInternal).evaluate()
            val compilation = project.extensions.getByType(KotlinJvmProjectExtension::class.java).target.compilations.getByName("main")
            return project.plugins.getPlugin(KotlinxSerializationSubplugin::class.java).isApplicable(compilation)
        }
        val kotlin = "org.jetbrains.kotlin.jvm"
        val ethers = "io.kriptal.ethers.abigen-plugin"
        applicable(kotlin, ethers) shouldBe true
        applicable(ethers, kotlin) shouldBe true
        applicable(kotlin, ethers, enabled = false) shouldBe false
        applicable(kotlin, SERIALIZATION_PLUGIN, ethers) shouldBe false
        applicable(kotlin, ethers, SERIALIZATION_PLUGIN) shouldBe false
    }
})
