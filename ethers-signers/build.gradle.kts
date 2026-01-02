plugins {
    `project-conventions`
    `maven-publish-conventions`
}

dependencies {
    api(project(":ethers-core"))
    api(project(":ethers-crypto"))

    testImplementation(libs.bundles.kotest)
}
