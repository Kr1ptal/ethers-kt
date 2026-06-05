plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                api(libs.bundles.ktor.client)
                api(libs.channelskt.core)

                api(project(":ethers-core"))
                api(project(":ethers-signers"))
                api(libs.bignumkt)

                implementation(project(":logger"))
                implementation(libs.jctools)
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
