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
    maven("https://central.sonatype.com/repository/maven-snapshots/")
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
    api(project(":ethers-solana-idl"))

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

// The JVM artifacts of ethers-solana and its dependencies, which TestKit builds compile generated bindings against as
// plain files, since this build's modules are not published to a repository they can resolve.
val solanaConsumerClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java), "jvm")
    }
}

// The serialization Gradle plugin, for TestKit builds that apply it alongside this one.
val serializationGradlePlugin: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    solanaConsumerClasspath(project(":ethers-solana"))
    serializationGradlePlugin("org.jetbrains.kotlin:kotlin-serialization:${libs.versions.kotlin.get()}")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-serialization:${libs.versions.kotlin.get()}")
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(serializationGradlePlugin)
}

tasks.test {
    inputs.files(solanaConsumerClasspath).withPropertyName("solanaConsumerClasspath").withNormalizer(ClasspathNormalizer::class.java)
    val consumerClasspath = solanaConsumerClasspath.elements
    systemProperty("ethers.kotlinVersion", libs.versions.kotlin.get())
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dethers.solanaConsumerClasspath=${consumerClasspath.get().joinToString(File.pathSeparator) { it.asFile.absolutePath }}") })
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

            displayName = "Plugin for generating Kotlin bindings for Ethereum smart contracts and Solana programs"
            description = "A plugin that generates Kotlin bindings for Ethereum smart contracts from JSON-ABI files, and for Solana programs from IDL files"
            tags = listOf("ethereum", "evm", "solana", "anchor", "java", "kotlin", "smart-contracts")
        }
    }
}
