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
    implementation(project(":ethers-abi"))
    implementation(project(":ethers-providers"))
    implementation(project(":logger"))
    implementation("io.github.adraffy:ens-normalize:0.2.0")

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
}
