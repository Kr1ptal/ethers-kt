plugins {
    `project-conventions`
    `maven-publish-conventions`
}

dependencies {
    api(project(":ethers-core"))
    api(project(":ethers-crypto"))

    implementation(libs.bouncycastle.provider)

    testImplementation(libs.bundles.kotest)
}
