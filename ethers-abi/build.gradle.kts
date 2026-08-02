import com.fasterxml.jackson.databind.ObjectMapper

plugins {
    `project-conventions`
    `jmh-conventions`
    `maven-publish-conventions`
    `static-data-generator`
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

staticDataGenerator {
    generators {
        create("multicall3Deployments") {
            inputFile.set(file("src/commonMain/resources/multicall3-deployments.json"))
            packageName.set("io.ethers.abi.call")
            propertyName.set("DEPLOYMENTS")
            data { file ->
                ObjectMapper().readTree(file)
            }
        }
    }
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-providers"))
                api(project(":ethers-signers"))
                api(libs.bignumkt)
                implementation(libs.ditchoom.buffer)
                implementation(libs.kotlinx.atomicfu)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
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
