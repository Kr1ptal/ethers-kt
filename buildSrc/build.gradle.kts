plugins {
    `kotlin-dsl`
}

repositories {
    google()
    gradlePluginPortal()
}

configurations.all {
    // Force BouncyCastle to 1.83 to avoid conflict between AGP (brings 1.79) and JReleaser (needs 1.83).
    // buildSrc classpath is shared with the build script classpath, so an older version here
    // would break JReleaser's PGP signing at deploy time.
    resolutionStrategy {
        force("org.bouncycastle:bcpg-jdk18on:1.84")
        force("org.bouncycastle:bcprov-jdk18on:1.84")
        force("org.bouncycastle:bcutil-jdk18on:1.84")
        force("org.bouncycastle:bcpkix-jdk18on:1.84")
    }
}

dependencies {
    // hacky way to make "libs" available in convention plugins
    // see: https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.kotlin.gradle)
    implementation(libs.dokka.gradle)
    implementation(libs.agp.library)

    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${libs.versions.ksp.get()}")
    implementation("io.kotest:io.kotest.gradle.plugin:${libs.versions.kotest.get()}")

    implementation(platform(libs.ktlint.bom))
    implementation(libs.ktlint.gradle)
    implementation(libs.ktlint.sarif)

    // For static data code generation
    implementation(libs.kotlinpoet)
    implementation(libs.jackson.databind)
}
