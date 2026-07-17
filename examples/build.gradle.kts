plugins {
    `project-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                implementation(libs.kotlinx.cli)
                implementation(libs.bignumkt)
                runtimeOnly(libs.bundles.log4j2)

                implementation(project(":ethers-abi"))
                implementation(project(":ethers-core"))
                implementation(project(":ethers-crypto"))
                implementation(project(":ethers-ens"))
                implementation(project(":ethers-providers"))
                implementation(project(":ethers-rlp"))
                implementation(project(":ethers-signers"))
            }
        }
    }
}

// TODO remove after publishing abigen-plugin with KMP fix (adds generated sources only to commonMain)
// The published plugin (1.6.0) adds generated sources to all *Main source sets, causing duplication errors in KMP.
// Keep them only in jvmSharedMain, remove from all others.
afterEvaluate {
    kotlin.sourceSets
        .matching { it.name.endsWith("Main") && it.name != "jvmSharedMain" }
        .configureEach {
            kotlin.setSrcDirs(kotlin.srcDirs.filter { !it.path.contains("generated/source/ethers") })
        }
}
