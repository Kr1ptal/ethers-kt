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
    implementation(libs.ens.normalise)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
}
