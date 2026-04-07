plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-crypto"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}
