plugins {
    `project-conventions`
    `jmh-conventions`
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ethers-rlp"))
                api(project(":ethers-crypto"))

                api(libs.bignumkt)
                api(libs.kotlinx.serialization)
            }
        }

        val jvmJmh by getting {
            dependencies {
                implementation(libs.jmh.core)
                implementation(libs.jmh.generator)
            }
        }
    }
}
