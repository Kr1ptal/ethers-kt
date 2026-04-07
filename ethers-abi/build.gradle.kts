import com.fasterxml.jackson.databind.ObjectMapper

plugins {
    `project-conventions`
    `jmh-conventions`
    `maven-publish-conventions`
    `static-data-generator`
}

staticDataGenerator {
    generators {
        create("multicall3Deployments") {
            inputFile.set(file("src/jvmSharedMain/resources/multicall3-deployments.json"))
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
        val jvmSharedMain by getting {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-providers"))
                api(project(":ethers-signers"))
                implementation(libs.ditchoom.buffer)
                implementation(libs.kotlinx.atomicfu)
            }
        }

        val jvmSharedTest by getting {
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
