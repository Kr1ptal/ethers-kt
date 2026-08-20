plugins {
    `project-conventions`
    `maven-publish-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-abi"))
                api(project(":ethers-providers"))
                api(libs.bignumkt)

                implementation(project(":logger"))
                implementation(libs.kotlinx.atomicfu)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}
