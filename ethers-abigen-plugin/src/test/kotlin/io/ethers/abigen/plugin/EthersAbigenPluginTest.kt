package io.ethers.abigen.plugin

import io.ethers.abigen.plugin.task.EthersAbigenTask
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gradle.api.internal.plugins.PluginApplicationException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import java.io.File

class EthersAbigenPluginTest : FunSpec({
    test("plugin is successfully applied") {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("io.kriptal.ethers.abigen-plugin")

        (project.plugins.getPlugin(EthersAbigenPlugin::class.java) is EthersAbigenPlugin) shouldBe true
    }

    test("plugin applies defaults to tasks") {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("io.kriptal.ethers.abigen-plugin")

        val ext = project.extensions.getByType(EthersAbigenExtension::class.java)

        project.tasks.withType(EthersAbigenTask::class.java).forEach {
            it.sourceProviders.get() shouldBe ext.sourceProviders.get()
            it.outputDir.get() shouldBe ext.outputDir.get()
        }
    }

    test("fail to apply plugin if no kotlin plugin is applied to project") {
        val project = ProjectBuilder.builder().build()

        shouldThrow<PluginApplicationException> {
            project.plugins.apply("io.kriptal.ethers.abigen-plugin")
        }
    }

    context("task execution") {
        val project = ProjectBuilder.builder().build()

        val taskName = "ethersAbigen"
        val customAbiPath = "src/main/abi-custom-folder"
        val customOutputDir = "generated/source/ethers-custom/main/kotlin"
        val abiDir = project.layout.projectDirectory.dir(customAbiPath).asFile
        val localBuildCacheDir = project.layout.projectDirectory.dir("local-build-cache").asFile

        // Copy the ABI files from test resources to a custom directory in gradle test project
        File(EthersAbigenPlugin::class.java.getResource("/abi")!!.toURI()).copyRecursively(abiDir, true)

        @Language("gradle")
        val settingsFile = """
            rootProject.name = 'ethers-abigen-plugin-test'
            
            // custom cache dir so it's a fresh location each test run
            buildCache {
                local {
                    directory '${localBuildCacheDir.toURI()}'
                }
            }
        """.trimIndent()

        @Language("gradle")
        val buildFile = """
            plugins {
                id 'base'
                id 'org.jetbrains.kotlin.jvm'
                id 'io.kriptal.ethers.abigen-plugin'
            }
            
            ethersAbigen {
                directorySource('$customAbiPath')
                outputDir = '$customOutputDir'
            }
        """.trimIndent()

        project.layout.projectDirectory.file("settings.gradle").asFile.writeText(settingsFile)
        project.layout.projectDirectory.file("build.gradle").asFile.writeText(buildFile)

        val runner = GradleRunner.create()
            .withProjectDir(project.layout.projectDirectory.asFile)
            .withPluginClasspath()
            .withDebug(true)
            .forwardOutput()

        test("task generates contract wrappers") {
            val result = runner.withArguments(taskName, "--build-cache", "--info").build()
            result.tasks.filter { it.path.endsWith(taskName) }.forEach { it.outcome shouldBe TaskOutcome.SUCCESS }

            val generatedFiles = project.layout.buildDirectory.dir(customOutputDir).get().asFile
                .walkTopDown()
                .filter(File::isFile)
                .toList()

            generatedFiles.size shouldBe 2
        }

        test("task results are loaded from cache on second run with same inputs") {
            val result = runner.withArguments("clean", taskName, "--build-cache", "--info").build()
            result.tasks.filter { it.path.endsWith(taskName) }.forEach { it.outcome shouldBe TaskOutcome.FROM_CACHE }
        }

        test("task results are cached even if project is moved to a different location (relocatability)") {
            val newProject = ProjectBuilder.builder().build()

            runner.projectDir.copyRecursively(newProject.layout.projectDirectory.asFile, true)
            val newRunner = GradleRunner.create()
                .withProjectDir(project.layout.projectDirectory.asFile)
                .withPluginClasspath()
                .withDebug(true)
                .forwardOutput()

            newProject.layout.projectDirectory.dir("local-build-cache").asFile.deleteRecursively()

            val result = newRunner.withArguments("clean", taskName, "--build-cache", "--info").build()
            result.tasks.filter { it.path.endsWith(taskName) }.forEach { it.outcome shouldBe TaskOutcome.FROM_CACHE }
        }

        test("changing task inputs invalidates cache") {
            val newOutputDir = "generated/source/ethers-updated/main/kotlin"

            @Language("gradle")
            val newBuildFile = """
                plugins {
                    id 'base'
                    id 'org.jetbrains.kotlin.jvm'
                    id 'io.kriptal.ethers.abigen-plugin'
                }
                
                ethersAbigen {
                    directorySource('$customAbiPath')
                    outputDir = '$newOutputDir'
                }
            """.trimIndent()

            project.layout.projectDirectory.file("build.gradle").asFile.writeText(newBuildFile)

            val result = runner.withArguments("clean", taskName, "--build-cache", "--info").build()
            result.tasks.filter { it.path.endsWith(taskName) }.forEach { it.outcome shouldBe TaskOutcome.SUCCESS }

            val generatedFiles = project.layout.buildDirectory.dir(newOutputDir).get().asFile
                .walkTopDown()
                .filter(File::isFile)
                .toList()

            generatedFiles.size shouldBe 2
        }
    }
})
