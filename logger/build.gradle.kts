plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(libs.kotlin.logging.facade)
                runtimeOnly(libs.bundles.log4j2)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}

tasks.withType<Jar> {
    exclude("log4j2.xml")
}
