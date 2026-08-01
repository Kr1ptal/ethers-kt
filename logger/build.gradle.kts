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

        val jvmSharedMain by getting {
            dependencies {
                runtimeOnly(libs.bundles.log4j2)
            }
        }
    }
}

tasks.withType<Jar> {
    exclude("log4j2.xml")
}
