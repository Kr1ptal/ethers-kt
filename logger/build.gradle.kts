plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlin.logging.facade)
            }
        }

        // jvmSharedMain is androidMain's parent too, and log4j2 is a JVM backend that cannot dex
        val jvmMain by getting {
            dependencies {
                runtimeOnly(libs.bundles.log4j2)
            }
        }
    }
}

tasks.withType<Jar> {
    exclude("log4j2.xml")
}
