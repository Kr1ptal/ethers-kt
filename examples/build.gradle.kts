plugins {
    `project-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
}

dependencies {
    // ArgParser
    implementation(libs.kotlinx.cli)

    // Define any required artifacts without version
    implementation(project(":ethers-abi"))
    implementation(project(":ethers-core"))
    implementation(project(":ethers-providers"))
    implementation(project(":ethers-signers"))
}
