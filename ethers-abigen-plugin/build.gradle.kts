plugins {
    idea
    kotlin("jvm")
    `signing-conventions`
    `java-gradle-plugin`
    `ktlint-conventions`
    id("com.gradle.plugin-publish") version "2.1.1"
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
        implementation = JvmImplementation.VENDOR_SPECIFIC
    }
}

// Override incorrect JVM version metadata in secp256k1-kmp (transitive via ethers-crypto)
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

    api(project(":ethers-abigen"))

    implementation(libs.kotlin.gradle)
    implementation(libs.kotlin.reflect)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.toml)
    implementation(libs.bundles.jackson)

    testImplementation(libs.bundles.kotest)
    testRuntimeOnly(libs.kotest.runner.junit5)
}

// the plugin is published as a fat jar, to automatically include all dependencies and avoid having
// to manually declare additional repositories from where to fetch them in settings.gradle.kts
tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
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
