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

    implementation(platform(libs.ktlint.bom))
    implementation(libs.ktlint.gradle)
    implementation(libs.ktlint.sarif)
}
