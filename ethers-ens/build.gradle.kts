plugins {
    `project-conventions`
    `maven-publish-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
}

repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-abi"))
                api(project(":ethers-providers"))

                implementation(project(":logger"))
                implementation(libs.ens.normalise)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.bundles.junit)
                implementation(libs.bundles.kotest)
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
