@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `project-conventions`
}

dependencies {
    implementation(project(":ethers-core"))
    implementation(project(":ethers-crypto"))
    implementation(project(":ethers-rlp"))

    implementation(libs.bouncycastle.provider)

    testImplementation(libs.bundles.kotest)
}
