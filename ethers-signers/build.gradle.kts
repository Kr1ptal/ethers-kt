plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-crypto"))
            }
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}
