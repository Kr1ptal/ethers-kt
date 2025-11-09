@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `project-conventions`
    id(libs.plugins.kotlin.kapt.get().pluginId) // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
    `jmh-conventions`
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    `maven-publish-conventions`
}

dependencies {
    api(project(":ethers-json"))
    api(project(":ethers-rlp"))
    api(project(":ethers-crypto"))

    // Jackson is used for annotations on types, but not exposed in API
    implementation(libs.bundles.jackson)
    implementation(libs.bouncycastle.provider)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(project(":ethers-json-jackson"))

    jmhImplementation(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator)
    kaptJmh(libs.jmh.generator)

    jmhImplementation(libs.kotlinx.serialization)
}
