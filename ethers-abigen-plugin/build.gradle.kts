plugins {
    `project-conventions`
    `signing-conventions`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.2.1"
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":ethers-abigen"))
    implementation(libs.kotlin.gradle)
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
