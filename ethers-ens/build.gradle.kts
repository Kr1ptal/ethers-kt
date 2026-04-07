plugins {
    `project-conventions`
    `maven-publish-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
}

// TODO remove after publishing abigen-plugin with KMP fix (adds generated sources only to commonMain)
// The published plugin (1.6.0) adds generated sources to all *Main source sets, causing duplication errors in KMP.
afterEvaluate {
    kotlin.sourceSets.findByName("commonMain")?.kotlin?.setSrcDirs(
        kotlin.sourceSets.getByName("commonMain").kotlin.srcDirs.filter {
            !it.path.contains("generated/source/ethers")
        },
    )
}

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-abi"))
                api(project(":ethers-providers"))

                implementation(project(":logger"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}
