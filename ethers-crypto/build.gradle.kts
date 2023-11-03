plugins {
    `project-conventions`
    `maven-publish-conventions`
}

dependencies {
    implementation(libs.bouncycastle.provider)

    testImplementation(libs.bundles.kotest)
}
