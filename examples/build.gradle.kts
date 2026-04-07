plugins {
    `project-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
}

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlinx.cli)
                runtimeOnly(libs.bundles.log4j2)

                implementation(project.dependencies.platform(libs.ethers.bom))
                implementation(libs.bundles.ethers)
            }
        }
    }
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
