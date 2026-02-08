plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-crypto"))
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}
