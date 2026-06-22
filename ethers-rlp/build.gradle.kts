plugins {
    `project-conventions`
    `jmh-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.bignumkt)
                implementation(libs.ditchoom.buffer)
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
