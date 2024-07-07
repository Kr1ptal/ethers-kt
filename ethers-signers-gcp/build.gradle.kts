plugins {
    `project-conventions`
    `maven-publish-conventions`
}

dependencies {
    implementation(project(":ethers-core"))
    implementation(project(":ethers-crypto"))
    implementation(project(":ethers-signers"))

    implementation(libs.bouncycastle.provider)
    implementation(libs.gcp.kms)

    testImplementation(libs.bundles.kotest)
}
