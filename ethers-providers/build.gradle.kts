plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                api(libs.bundles.okhttp3)
                api(libs.channelskt.core)

                api(project(":ethers-core"))
                api(project(":ethers-signers"))

                implementation(project(":logger"))
                implementation(libs.jctools)
                implementation(libs.bundles.jackson)
                implementation(libs.kotlinx.atomicfu)
            }
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
                implementation(libs.okhttp3.mockwebserver)
            }
        }
    }
}
