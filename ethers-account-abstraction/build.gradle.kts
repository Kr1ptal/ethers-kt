plugins {
    `project-conventions`
    `maven-publish-conventions`
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-abi"))
                api(project(":ethers-providers"))
                api(project(":ethers-signers"))
                api(libs.bignumkt)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.whyoleg.cryptography.core)
                implementation(libs.whyoleg.cryptography.asn1)
            }
        }

        val jvmSharedMain by getting {
            dependencies {
                implementation(libs.whyoleg.cryptography.jdk)
            }
        }

        val nativeMain by getting {
            dependencies {
                implementation(libs.whyoleg.cryptography.openssl3)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}
