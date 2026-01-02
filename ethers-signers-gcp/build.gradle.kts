plugins {
    `project-conventions`
    `maven-publish-conventions`
}

dependencies {
    api(project(":ethers-signers"))

    implementation(libs.gcp.kms)
    implementation(libs.whyoleg.cryptography.asn1)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
}
