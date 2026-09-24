plugins {
    `project-conventions`
    `maven-publish-conventions`
    alias(libs.plugins.kotlin.serialization)
}

description = "Reads Solana program IDLs into a format-independent schema for code generation."

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                api(project(":ethers-common"))

                implementation(libs.kotlinx.serialization)
            }
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    from("NOTICE") { into("META-INF") }
}
