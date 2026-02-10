plugins {
    `project-conventions`
    `maven-publish-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":ethers-core"))
    api(project(":ethers-abi"))
    api(project(":ethers-providers"))

    implementation(project(":logger"))

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
}
