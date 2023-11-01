plugins {
    `project-conventions`
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.bundles.okhttp3)

    implementation(project(":ethers-core"))
    implementation(project(":logger"))
    implementation(libs.jctools)
    implementation(libs.bundles.jackson)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.okhttp3.mockwebserver)
}
