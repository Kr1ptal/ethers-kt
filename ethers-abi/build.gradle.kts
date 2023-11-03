@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `project-conventions`
    id(libs.plugins.kotlin.kapt.get().pluginId) // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
    `jmh-conventions`
    `maven-publish-conventions`
}

dependencies {
    implementation(project(":ethers-core"))
    implementation(project(":ethers-providers"))
    implementation(project(":ethers-signers"))

    testImplementation(libs.bundles.kotest)

    jmhImplementation(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator)
    kaptJmh(libs.jmh.generator)
}
