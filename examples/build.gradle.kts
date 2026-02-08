plugins {
    `project-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
}

ethersAbigen {
    directorySource("src/jvmMain/abi")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                // ArgParser
                implementation(libs.kotlinx.cli)
                runtimeOnly(libs.bundles.log4j2)

                implementation(project.dependencies.platform(libs.ethers.bom))
                implementation(libs.bundles.ethers)
            }
        }
    }
}

// The abigen plugin registers generated sources with all *Main source sets, but in KMP
// the same files can't belong to both commonMain and jvmMain. Remove from commonMain.
afterEvaluate {
    kotlin.sourceSets.getByName("commonMain").kotlin.setSrcDirs(
        kotlin.sourceSets.getByName("commonMain").kotlin.srcDirs.filter {
            !it.path.contains("generated/source/ethers")
        },
    )
}
