plugins {
    `project-conventions`
    `jmh-conventions`
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    `maven-publish-conventions`
}

description = "Chain-agnostic JSON-RPC transport over HTTP and WebSocket. Targets JVM, Android, macOS and iOS."

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

        val jvmJmh by getting {
            dependencies {
                // WebSocket messages of both chains, decoded into the types their providers return
                implementation(project(":ethers-core"))
                implementation(project(":ethers-solana"))
                implementation(libs.jmh.core)
                implementation(libs.jmh.generator)
            }
        }
    }
}
