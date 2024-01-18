plugins {
    `project-conventions`
    `maven-publish-conventions`
    id("io.kriptal.ethers.abigen-plugin") version "0.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ethers-core"))
    implementation(libs.ethers.abi)
    implementation(libs.ethers.providers)
    implementation(libs.ethers.logger)
    implementation(libs.ens.normalise)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
}
