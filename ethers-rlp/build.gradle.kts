plugins {
    `project-conventions`
    `jmh-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(libs.ditchoom.buffer)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }

        val jvmJmh by getting {
            dependencies {
                implementation(libs.jmh.core)
                implementation(libs.jmh.generator)
            }
        }
    }
}
