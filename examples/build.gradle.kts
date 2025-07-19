plugins {
    `project-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
}

dependencies {
    // ArgParser
    implementation(libs.kotlinx.cli)
    runtimeOnly(libs.bundles.log4j2)

    implementation(platform(libs.ethers.bom))
    implementation(libs.bundles.ethers)
}
