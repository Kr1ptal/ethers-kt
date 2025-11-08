plugins {
    `project-conventions`
    `maven-publish-conventions`
}

dependencies {
    implementation(libs.bouncycastle.provider)

    testImplementation(project(":ethers-core"))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.jackson)
}
