plugins {
    `project-conventions`
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

                api(project(":ethers-core"))
                api(project(":ethers-signers"))
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

        // only present when the opt-in iOS target is enabled (-PethersEnableIos)
        findByName("nativeMain")?.dependencies {
            // engine is selected per-platform via `defaultHttpClientEngineFactory`
            api(libs.ktor.client.darwin)
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
                implementation(libs.okhttp3.mockwebserver)
            }
        }
    }
}
