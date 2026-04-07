import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

repositories {
    mavenCentral()
    google()
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
        alias(libs.plugins.android.kotlin.multiplatform.library)
    }

    configure<KotlinMultiplatformExtension> {
        jvm()

        // Configure Android library target using the AGP programmatic API
        // (the androidLibrary {} DSL accessor is not available in precompiled script plugins)
        val androidTarget = the<KotlinMultiplatformAndroidLibraryTarget>()
        androidTarget.namespace = "io.kriptal.ethers.${project.name.replace("-", ".")}"
        androidTarget.compileSdk = 35
        androidTarget.minSdk = 24

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

        // Intermediate source set shared between JVM and Android targets.
        // All existing code lives in src/jvmSharedMain. Platform-specific source sets
        // (jvmMain, androidMain) are only used for platform-specific overrides.
        sourceSets {
            val jvmSharedMain by creating {
                dependsOn(commonMain.get())
            }
            val jvmSharedTest by creating {
                dependsOn(commonTest.get())
            }

            jvmMain {
                dependsOn(jvmSharedMain)
            }
            androidMain {
                dependsOn(jvmSharedMain)
            }

            jvmTest {
                dependsOn(jvmSharedTest)
                dependencies {
                    implementation(libs.bundles.kotest)
                }
            }
            androidUnitTest {
                dependsOn(jvmSharedTest)
            }
        }
    }
}
