plugins {
    `java-gradle-plugin`
    `project-conventions`
    id("com.gradle.plugin-publish") version "1.2.1"
}

dependencies {
    implementation(project(":ethers-abigen"))
    implementation(libs.kotlin.gradle)

    testImplementation(libs.bundles.kotest)
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
