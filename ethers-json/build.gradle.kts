plugins {
    `project-conventions`
    `maven-publish-conventions`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
}
