plugins {
    `project-conventions`
    `maven-publish-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

// TODO remove after publishing abigen-plugin with KMP fix (adds generated sources only to commonMain)
// The published plugin (1.6.0) adds generated sources to all *Main source sets, causing duplication errors in KMP.
// Keep them only in commonMain, remove from all others.
afterEvaluate {
    kotlin.sourceSets
        .matching { it.name.endsWith("Main") && it.name != "commonMain" }
        .configureEach {
            kotlin.setSrcDirs(kotlin.srcDirs.filter { !it.path.contains("generated/source/ethers") })
        }
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
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}
