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

                implementation(project.dependencies.platform(libs.ethers.bom))
                implementation(libs.bundles.ethers)
            }
        }
    }
}

// The abigen plugin registers its generated sources on a single source set: `commonMain` since 2.0.0, every
// `*Main` before that. Neither placement works here - the generated bindings reference `java.math.BigInteger`,
// and the ethers dependencies are declared on `jvmSharedMain` - so strip the generated dir from every other
// source set and register the task output on `jvmSharedMain`, where all the examples live.
afterEvaluate {
    kotlin.sourceSets
        .matching { it.name.endsWith("Main") && it.name != "jvmSharedMain" }
        .configureEach {
            kotlin.setSrcDirs(kotlin.srcDirs.filter { !it.path.contains("generated/source/ethers") })
        }

    kotlin.sourceSets.named("jvmSharedMain") {
        kotlin.srcDir(tasks.named("ethersAbigen"))
    }
}
