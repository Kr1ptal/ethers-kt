plugins {
    `project-conventions`
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.bignumkt)
                api(libs.kotlinx.serialization)
                implementation(libs.kotlincrypto.hash.sha3)
                implementation(libs.whyoleg.cryptography.core)
                implementation(libs.whyoleg.cryptography.random)
                implementation(libs.ditchoom.buffer)
            }
        }

        val jvmSharedMain by getting {
            dependencies {
                // JVM + Android provider for whyoleg cryptography (SHA256, HMAC, RIPEMD160)
                implementation(libs.whyoleg.cryptography.jdk)
            }
        }

        val nativeMain by getting {
            dependencies {
                // native provider for whyoleg cryptography. Apple's CommonCrypto/CryptoKit providers have no
                // RIPEMD160, which Hashing.ripemd160() needs, so OpenSSL is the one that covers everything.
                implementation(libs.whyoleg.cryptography.openssl3)
            }
        }
    }
}
