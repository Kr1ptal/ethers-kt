plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-crypto"))
                api(libs.bignumkt)
            }
        }
    }
}
