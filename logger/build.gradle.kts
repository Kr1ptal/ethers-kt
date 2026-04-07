plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                api(libs.kotlin.logging.facade)
                runtimeOnly(libs.bundles.log4j2)
            }
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}

tasks.withType<Jar> {
    exclude("log4j2.xml")
}
