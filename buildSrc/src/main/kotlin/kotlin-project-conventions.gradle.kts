import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

repositories {
    mavenCentral()
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    val libs = the<LibrariesForLibs>()

    configure<KotlinMultiplatformExtension> {
        jvm()

        jvmToolchain {
            languageVersion = JavaLanguageVersion.of(Constants.testJavaVersion.majorVersion)
            vendor = JvmVendorSpec.ADOPTIUM
            implementation = JvmImplementation.VENDOR_SPECIFIC
        }

        targets.configureEach {
            compilations.all {
                compileTaskProvider.configure {
                    compilerOptions {
                        val isTestTask = name.contains("test", ignoreCase = true)

                        val defaultArgs = listOf(
                            "-progressive",
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

                            freeCompilerArgs.addAll(
                                listOf(
                                    "-Xjvm-default=all",
                                    // TODO re-add when this is fixed: https://youtrack.jetbrains.com/issue/KT-78923
                                    //"-Xbackend-threads=0", // use all available processors
                                ),
                            )
                        }

                        freeCompilerArgs.addAll(defaultArgs + specificArgs)
                    }
                }
            }
        }
    }

    // Set Java compile tasks to match Kotlin jvmTarget
    tasks.withType<JavaCompile>().configureEach {
        val isTestTask = name.contains("test", ignoreCase = true)
        val version = if (isTestTask) Constants.testJavaVersion else Constants.compileJavaVersion
        sourceCompatibility = version.majorVersion
        targetCompatibility = version.majorVersion
    }

    // Jacoco configuration
    apply(plugin = "jacoco")

    configure<JacocoPluginExtension> {
        toolVersion = libs.versions.jacoco.tool.get()
    }

    tasks.withType<JacocoReport>().configureEach {
        executionData.setFrom(fileTree(layout.buildDirectory).include("/jacoco/*.exec"))

        reports {
            html.required.set(true)
            csv.required.set(true)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
