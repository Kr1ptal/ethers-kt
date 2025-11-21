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
    api(project(":ethers-json"))

    implementation(project(":logger"))
    implementation(project(":ethers-json-jackson"))
    implementation(libs.jctools)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.okhttp3.mockwebserver)
}
