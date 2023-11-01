package io.ethers.abigen.plugin

import io.ethers.abigen.plugin.task.EthersAbigenTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

abstract class EthersAbigenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val ext = target.extensions.create("ethersAbigen", EthersAbigenExtension::class.java)

        val abigenTask = target.tasks.register("ethersAbigen", EthersAbigenTask::class.java) {
            it.sourceProviders.set(ext.sourceProviders)
            it.functionRenames.set(ext.functionRenames)
            it.outputDir.set(ext.outputDir)
        }

        val kotlinCompileTasks = target.tasks.names.filter { it.contains("compile") && it.contains("Kotlin") }
        if (kotlinCompileTasks.isEmpty()) {
            throw GradleException("No Kotlin compile tasks found in project. Apply one of the Kotlin plugins to the project if you want to use the abigen plugin.")
        }

        kotlinCompileTasks.forEach { taskName ->
            target.tasks.named(taskName).configure { it.dependsOn(abigenTask) }
        }

        val extKotlin = target.extensions.getByType(KotlinProjectExtension::class.java)
        extKotlin.sourceSets.getByName("main").kotlin.srcDir(abigenTask)
    }
}
