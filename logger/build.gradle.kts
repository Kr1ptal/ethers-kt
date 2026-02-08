plugins {
    `project-conventions`
    `maven-publish-conventions`
}

repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(libs.kotlin.logging.facade)
                runtimeOnly(libs.bundles.log4j2)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.bundles.junit)
                implementation(libs.bundles.kotest)
            }
        }
    }
}

// exclude the log4j2.xml file from the jar
tasks.withType<Jar> {
    exclude("log4j2.xml")
}
