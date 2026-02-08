import org.gradle.api.attributes.java.TargetJvmVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    idea
    eclipse
    kotlin("jvm")
    id("ktlint-conventions")
    `signing-conventions`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.0.0"
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
}

// Override incorrect JVM version metadata in secp256k1-kmp
// The library targets Java 8 bytecode but declares JVM 21 in Gradle Module Metadata
dependencies {
    components {
        listOf(
            "fr.acinq.secp256k1:secp256k1-kmp-jni-jvm",
            "fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-darwin",
            "fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-linux",
            "fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-mingw",
        ).forEach { module ->
            withModule(module) {
                allVariants {
                    attributes {
                        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                    }
                }
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(Constants.testJavaVersion.majorVersion)
        vendor = JvmVendorSpec.ADOPTIUM
        implementation = JvmImplementation.VENDOR_SPECIFIC
    }

    targetCompatibility = Constants.compileJavaVersion
    sourceCompatibility = Constants.compileJavaVersion
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        val isTestTask = name.contains("test") || name.contains("Test")

        jvmTarget = JvmTarget.fromTarget(
            (if (isTestTask) Constants.testJavaVersion else Constants.compileJavaVersion).majorVersion,
        )

        freeCompilerArgs.addAll(
            "-progressive",
            "-Xjvm-default=all",
        )

        if (isTestTask) {
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn,kotlin.ExperimentalStdlibApi,io.kotest.common.ExperimentalKotest")
        } else {
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xno-param-assertions",
                "-Xno-call-assertions",
                "-Xno-receiver-assertions",
            )
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    val isTestTask = name.contains("test", ignoreCase = true)
    val version = if (isTestTask) Constants.testJavaVersion else Constants.compileJavaVersion
    sourceCompatibility = version.majorVersion
    targetCompatibility = version.majorVersion
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    api(project(":ethers-abigen"))

    implementation(libs.kotlin.gradle)
    implementation(libs.kotlin.reflect)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.toml)
    implementation(libs.bundles.jackson)

    testImplementation(libs.bundles.kotest)
}

// the plugin is published as a fat jar, to automatically include all dependencies and avoid having
// to manually declare additional repositories from where to fetch them in settings.gradle.kts
tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.test {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
            vendor.set(JvmVendorSpec.ADOPTIUM)
            implementation.set(JvmImplementation.VENDOR_SPECIFIC)
        },
    )
}

gradlePlugin {
    website = "https://github.com/kr1ptal/ethers-kt"
    vcsUrl = "https://github.com/kr1ptal/ethers-kt.git"

    plugins {
        create("abigen-plugin") {
            id = "io.kriptal.ethers.abigen-plugin"
            implementationClass = "io.ethers.abigen.plugin.EthersAbigenPlugin"

            displayName = "Plugin for generating JVM contract bindings for Ethereum smart contracts"
            description = "A plugin that generates JVM contract bindings for Ethereum smart contracts"
            tags = listOf("ethereum", "evm", "java", "kotlin", "smart-contracts")
        }
    }
}
