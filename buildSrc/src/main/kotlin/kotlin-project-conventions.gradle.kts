import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

plugins {
    id("java-toolchain-conventions")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

project.pluginManager.withPlugin("java") {
    val jvmTarget = the<JavaPluginExtension>().toolchain.languageVersion.get().asInt().toString()

    // disable runtime nullable call and argument checks for improved performance - they're left in tests to catch early bugs
    val kotlinCompilerConfig: KotlinJvmCompilerOptions.(Boolean) -> Unit = { isTestTask ->
        val defaultArgs = listOf(
            "-progressive",
            "-Xjvm-default=all",
            // TODO re-add when this is fixed: https://youtrack.jetbrains.com/issue/KT-78923
            //"-Xbackend-threads=0", // use all available processors
        )

        val specificArgs = if (isTestTask) {
            listOf(
                "-opt-in=kotlin.RequiresOptIn,kotlin.ExperimentalStdlibApi,io.kotest.common.ExperimentalKotest",
            )
        } else {
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xno-param-assertions",
                "-Xno-call-assertions",
                "-Xno-receiver-assertions",
            )
        }

        freeCompilerArgs.addAll(defaultArgs + specificArgs)
    }

    // need to do two separate checks for both cases, not ignoring case. Otherwise, we'd get a false positive for "kaptGenera`teSt`ubsKotlin"
    fun isTestTask(name: String) = name.contains("test") || name.contains("Test")

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            this.jvmTarget = JvmTarget.fromTarget(jvmTarget)
            kotlinCompilerConfig(isTestTask(name))
        }
    }

    project.pluginManager.withPlugin("kapt") {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs>().configureEach {
            compilerOptions {
                this.jvmTarget = JvmTarget.fromTarget(jvmTarget)
                kotlinCompilerConfig(isTestTask(name))
            }
        }
    }
}
