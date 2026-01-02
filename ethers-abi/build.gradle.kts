@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `project-conventions`
    id(libs.plugins.kotlin.kapt.get().pluginId) // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
    `jmh-conventions`
    `maven-publish-conventions`
}

dependencies {
    api(project(":ethers-core"))
    api(project(":ethers-providers"))
    api(project(":ethers-signers"))
    implementation(libs.ditchoom.buffer)

    testImplementation(libs.bundles.kotest)

    jmhImplementation(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator)
    kaptJmh(libs.jmh.generator)
}
