plugins {
    `project-conventions`
    `maven-publish-conventions`
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":ethers-json"))
    api(project(":ethers-core"))

    implementation(libs.bundles.jackson)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
}
