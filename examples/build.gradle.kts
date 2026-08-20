plugins {
    `project-conventions`
    id("io.kriptal.ethers.abigen-plugin") version libs.versions.ethers.get()
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project.dependencies.platform(libs.ethers.bom))
                implementation(libs.bundles.ethers)
                implementation(libs.bignumkt)
            }
        }

        val jvmSharedMain by getting {
            dependencies {
                implementation(libs.kotlinx.cli)
                runtimeOnly(libs.bundles.log4j2)
            }
        }
    }
}
