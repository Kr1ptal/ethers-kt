plugins {
    `project-conventions`
    `jmh-conventions`
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                api(project(":ethers-rlp"))
                api(project(":ethers-crypto"))

                api(libs.bignumkt)
                api(libs.bundles.jackson)
            }
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }

        val jvmJmh by getting {
            dependencies {
                implementation(libs.jmh.core)
                implementation(libs.jmh.generator)

                implementation(libs.kotlinx.serialization)
            }
        }
    }
}
