import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.kotest.framework.gradle.KotestGradleExtension
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
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

    configure<KotestGradleExtension> {
        customGradleTask.set(true)
    }

    configure<KotlinMultiplatformExtension> {
        jvm()

        // Modules whose code is inherently JVM-bound and so have no commonMain at all: ethers-signers-gcp wraps
        // the Google Cloud KMS client.
        //
        // They must not declare Apple targets. A target with no common sources compiles to NO-SOURCE and produces
        // no klib, but the publication still expects that artifact, so `publish` dies with
        // `FileNotFoundException: ethers-signers-gcp-iosArm64Main-<version>.klib`.
        val jvmOnlyModules = setOf("ethers-signers-gcp")

        // ethers-abigen is JVM-bound in exactly the same way, but declares Apple targets anyway: its commonTest
        // compiles generated contract wrappers for every target, which is what proves abigen output is portable.
        // Its main compilations stay empty, so it hits the klib problem above - `publish` therefore has to run
        // with `-PethersPublishing`, which drops the targets and restores the JVM-only publication.
        //
        // The flag gates publishing rather than the targets so the check is on by default: forgetting it fails
        // the publish loudly, where an opt-in check would just quietly stop running.
        val testOnlyNativeModules = setOf("ethers-abigen")
        val isPublishing = project.hasProperty("ethersPublishing")

        val supportsNative = when {
            project.name in jvmOnlyModules -> false
            project.name in testOnlyNativeModules -> !isPublishing
            else -> true
        }

        // Apple targets. These also keep commonMain honest: with only JVM and Android, both JVM-family,
        // `compileCommonMainKotlinMetadata` is skipped and commonMain resolves `java.*` without complaint, so a
        // green build proves nothing about portability. Compiling for a native target is what enforces it.
        //
        // NOTE: they can only be built on a macOS host - Kotlin/Native cannot cross-compile Apple targets. Any
        // task that reaches them (`build`, `allTests`, an unscoped `ktlintCheck`) therefore fails on Linux, which
        // is why CI splits JVM work onto ubuntu and everything else onto macOS.
        if (supportsNative) {
            macosArm64()
            iosArm64()
            iosX64()
            iosSimulatorArm64()
        }

        // Configure Android library target using the AGP programmatic API
        // (the androidLibrary {} DSL accessor is not available in precompiled script plugins)
        val androidTarget = the<KotlinMultiplatformAndroidLibraryTarget>()
        androidTarget.namespace = "io.kriptal.ethers.${project.name.replace("-", ".")}"
        androidTarget.compileSdk = 36
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

            commonTest {
                dependencies {
                    implementation(libs.bundles.kotest)
                }
            }

            jvmTest {
                dependsOn(jvmSharedTest)
            }
            androidUnitTest {
                dependsOn(jvmSharedTest)
            }

            // Intermediate source set shared by every native target, holding the `actual` declarations that
            // cannot live in jvmSharedMain.
            if (supportsNative) {
                val nativeMain by creating {
                    dependsOn(commonMain.get())
                }
                val nativeTest by creating {
                    dependsOn(commonTest.get())
                }

                listOf("macosArm64", "iosArm64", "iosX64", "iosSimulatorArm64").forEach { target ->
                    named("${target}Main") { dependsOn(nativeMain) }
                    named("${target}Test") { dependsOn(nativeTest) }
                }
            }
        }
    }
}
