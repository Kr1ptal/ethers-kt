import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    `project-conventions`
    `maven-publish-conventions`
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    the<KotlinMultiplatformAndroidLibraryTarget>().withDeviceTestBuilder {
        sourceSetTreeName = "deviceTest"
    }.configure {
        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":ethers-providers"))
                implementation(libs.ditchoom.buffer)
                implementation(libs.whyoleg.cryptography.core)
            }
        }
        val jvmSharedMain by getting {
            dependencies {
                implementation(libs.sol4k.tweetnacl)
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation(libs.whyoleg.cryptography.openssl3)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
            }
        }
        named("androidDeviceTest") {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("androidx.test:runner:1.6.2")
            }
        }
    }
}

publishing.publications.withType<MavenPublication>().configureEach {
    pom.description.set("Solana keys, transactions, JSON-RPC and subscriptions for Kotlin Multiplatform, JVM and Android.")
}

tasks.withType<Jar>().configureEach {
    from("NOTICE") { into("META-INF") }
    from(rootProject.file("LICENSE-APACHE")) { into("META-INF") }
}
