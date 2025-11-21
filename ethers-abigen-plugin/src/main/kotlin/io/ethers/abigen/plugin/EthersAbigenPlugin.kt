package io.ethers.abigen.plugin

import io.ethers.abigen.plugin.task.EthersAbigenTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.slf4j.LoggerFactory

abstract class EthersAbigenPlugin : Plugin<Project> {
    private val logger = LoggerFactory.getLogger(EthersAbigenPlugin::class.java)

    override fun apply(target: Project) {
        val ext = target.extensions.create("ethersAbigen", EthersAbigenExtension::class.java)

        val abigenTask = target.tasks.register("ethersAbigen", EthersAbigenTask::class.java) {
            it.sourceProviders.set(ext.sourceProviders)
            it.functionRenames.set(ext.functionRenames)
            it.outputDir.set(ext.outputDir)

            // Configure external properties only (internal properties are initialized in task)
            it.projectPath.convention(target.path)
        }

        target.afterEvaluate {
            val kotlinCompileTasks = target.tasks.names.filter { it.contains("compile") && it.contains("Kotlin") }

            if (kotlinCompileTasks.isEmpty()) {
                throw GradleException("No Kotlin compile tasks found in project. Apply one of the Kotlin plugins to the project if you want to use the abigen plugin.")
            }

            kotlinCompileTasks.forEach { taskName ->
                target.tasks.named(taskName).configure { it.dependsOn(abigenTask) }
            }

            runCatching { target.extensions.getByType(KotlinMultiplatformExtension::class.java) }
                .onSuccess {
                    logger.info("Running in environment: ${it.javaClass.simpleName}")

                    it.sourceSets
                        .matching { sourceSet -> sourceSet.name.endsWith("Main") }
                        .all { sourceSet ->
                            logger.info("Adding generated abi wrappers as source to '${sourceSet.name}' SourceSet")

                            sourceSet.kotlin.srcDir(abigenTask)
                        }
                    return@afterEvaluate
                }

            runCatching { target.extensions.getByType(KotlinAndroidProjectExtension::class.java) }
                .onSuccess {
                    logger.info("Running in environment: ${it.javaClass.simpleName}")

                    val sourceSet = it.sourceSets.getByName("main")
                    logger.info("Adding generated abi wrappers as source to '${sourceSet.name}' SourceSet")

                    sourceSet.kotlin.srcDir(abigenTask)
                    return@afterEvaluate
                }

            runCatching { target.extensions.getByType(KotlinProjectExtension::class.java) }
                .onSuccess {
                    logger.info("Running in environment: ${it.javaClass.simpleName}")

                    val sourceSet = it.sourceSets.getByName("main")
                    logger.info("Adding generated abi wrappers as source to '${sourceSet.name}' SourceSet")

                    sourceSet.kotlin.srcDir(abigenTask)
                    return@afterEvaluate
                }

            throw GradleException("No Kotlin source sets found in project. Apply one of the Kotlin plugins to use this abigen plugin. If you have a kotlin plugin applied and see this issue, report it at 'https://github.com/Kr1ptal/ethers-kt/issues' and include your gradle build file")
        }
    }
}
