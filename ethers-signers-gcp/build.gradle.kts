plugins {
    `project-conventions`
    `maven-publish-conventions`
}

dependencies {
    api(project(":ethers-signers"))

    implementation(libs.bouncycastle.provider)
    implementation(libs.gcp.kms)

    testImplementation(libs.bundles.kotest)
}
