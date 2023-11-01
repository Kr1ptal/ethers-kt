plugins {
    `project-conventions`
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.slf4j.api)
    runtimeOnly(libs.bundles.log4j2)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)
}

// exclude the log4j2.xml file from the jar
tasks.withType<Jar> {
    exclude("log4j2.xml")
}
