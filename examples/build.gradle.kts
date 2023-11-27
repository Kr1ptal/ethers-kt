plugins {
    `project-conventions`
    id("io.kriptal.ethers.abigen-plugin") version "0.1.0"
}

dependencies {
    // ArgParser
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")

    // Define any required artifacts without version
    implementation(project(":ethers-abi"))
    implementation(project(":ethers-core"))
    implementation(project(":ethers-providers"))
    implementation(project(":ethers-signers"))
}
