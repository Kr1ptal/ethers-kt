import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

repositories {
    mavenCentral()
}

// disable runtime null call and argument checks for improved performance - they're left in tests to catch early bugs
val kotlinCompilerConfig: KotlinCommonCompilerOptions.(Boolean) -> Unit = { isTestTask ->
    val defaultArgs = listOf(
        "-progressive",
        // TODO re-add when this is fixed: https://youtrack.jetbrains.com/issue/KT-78923
        //"-Xbackend-threads=0", // use all available processors
        "-Xjvm-default=all",
        "-Xexpect-actual-classes",
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

    if (this is KotlinJvmCompilerOptions) {
        val version = if (isTestTask) Constants.testJavaVersion else Constants.compileJavaVersion
        jvmTarget = JvmTarget.fromTarget(version.majorVersion)
    }

    freeCompilerArgs.addAll(defaultArgs + specificArgs)
}

fun isTestTask(name: String) = name.contains("test") || name.contains("Test")

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    val libs = the<LibrariesForLibs>()

    plugins {
        alias(libs.plugins.ksp)
        alias(libs.plugins.kotest)
    }

    configure<KotlinMultiplatformExtension> {
        jvm()

        jvmToolchain {
            languageVersion = JavaLanguageVersion.of(Constants.testJavaVersion.majorVersion)
            vendor = JvmVendorSpec.ADOPTIUM
            implementation = JvmImplementation.VENDOR_SPECIFIC
        }

        // disable default KMP test task - we use `kotest` instead
        tasks.matching { it.name == "jvmTest" }.configureEach {
            enabled = false
        }

        targets.configureEach {
            compilations.all {
                compileTaskProvider.configure {
                    compilerOptions.kotlinCompilerConfig(isTestTask(name))
                }
            }
        }

        sourceSets {
            val jvmTest by getting {
                dependencies {
                    implementation(libs.bundles.kotest)
                }
            }
        }
    }
}
