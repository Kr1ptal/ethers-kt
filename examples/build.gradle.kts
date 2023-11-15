plugins {
    id("java")
    kotlin("jvm") version "1.9.20"
    id("io.kriptal.ethers.abigen-plugin") version "0.1.0"
}

group = "io.ethers"
version = "1.0-SNAPSHOT"

// default values
ethersAbigen {
    directorySource("src/main/abi")
    outputDir = "generated/source/ethers/main/kotlin"
}

// Define a maven repository where the library is published
repositories {
    mavenCentral()

    // for snapshot versions, use the following repository
    //maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    // Define a BOM and its version
    implementation(platform("io.kriptal.ethers:ethers-bom:0.1.0"))

    // Define any required artifacts without version
    implementation("io.kriptal.ethers:ethers-abi")
    implementation("io.kriptal.ethers:ethers-core")
    implementation("io.kriptal.ethers:ethers-providers")
    implementation("io.kriptal.ethers:ethers-signers")
}