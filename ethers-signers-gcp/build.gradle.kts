plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                api(project(":ethers-signers"))

                implementation(libs.gcp.kms)
                implementation(libs.whyoleg.cryptography.asn1)
            }
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
                implementation(libs.mockk)
            }
        }
    }
}
