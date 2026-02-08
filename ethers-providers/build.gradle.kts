plugins {
    `project-conventions`
    `maven-publish-conventions`
}

repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                api(libs.bundles.okhttp3)
                api(libs.channelskt.core)

                api(project(":ethers-core"))
                api(project(":ethers-signers"))

                implementation(project(":logger"))
                implementation(libs.jctools)
                implementation(libs.bundles.jackson)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.bundles.junit)
                implementation(libs.bundles.kotest)
                implementation(libs.okhttp3.mockwebserver)
            }
        }
    }
}
