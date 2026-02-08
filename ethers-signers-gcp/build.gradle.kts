plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(project(":ethers-signers"))

                implementation(libs.gcp.kms)
                implementation(libs.whyoleg.cryptography.asn1)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.bundles.kotest)
                implementation(libs.mockk)
            }
        }
    }
}
