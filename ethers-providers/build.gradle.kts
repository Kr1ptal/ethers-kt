plugins {
    `project-conventions`
    `maven-publish-conventions`
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.bundles.okhttp3)
    api(libs.channelskt.core)

    api(project(":ethers-core"))
    api(project(":ethers-signers"))

    implementation(project(":logger"))
    implementation(libs.jctools)
    implementation(libs.bundles.jackson)
    implementation(libs.kotlinx.atomicfu)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.okhttp3.mockwebserver)
}
