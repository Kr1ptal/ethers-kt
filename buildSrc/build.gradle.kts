plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    // hacky way to make "libs" available in convention plugins
    // see: https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.kotlin.gradle)
    implementation(libs.dokka.gradle)

    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${libs.versions.ksp.get()}")
    implementation("io.kotest:io.kotest.gradle.plugin:${libs.versions.kotest.get()}")

    implementation(platform(libs.ktlint.bom))
    implementation(libs.ktlint.gradle)
    implementation(libs.ktlint.sarif)

    // For static data code generation
    implementation(libs.kotlinpoet)
    implementation(libs.jackson.databind)
}
