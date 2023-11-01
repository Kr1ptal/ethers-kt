@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `project-conventions`
}

dependencies {
    implementation(libs.bouncycastle.provider)

    testImplementation(libs.bundles.kotest)
}
