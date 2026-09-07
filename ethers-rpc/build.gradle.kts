plugins {
    `project-conventions`
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)

                api(libs.ktor.client.core)
                api(libs.ktor.client.websockets)
                api(libs.channelskt.core)

                api(project(":ethers-common"))
                api(libs.bignumkt)

                implementation(project(":logger"))
                implementation(libs.kotlinx.atomicfu)
            }
        }

        val jvmSharedMain by getting {
            dependencies {
                // engine is selected per-platform via `defaultHttpClientEngineFactory`
                api(libs.ktor.client.cio)
            }
        }

        val nativeMain by getting {
            dependencies {
                // engine is selected per-platform via `defaultHttpClientEngineFactory`
                api(libs.ktor.client.darwin)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
            }
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}
