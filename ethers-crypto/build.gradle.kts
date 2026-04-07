import com.fasterxml.jackson.databind.ObjectMapper

plugins {
    `project-conventions`
    `maven-publish-conventions`
    `static-data-generator`
}

staticDataGenerator {
    generators {
        create("bip39EnglishWordlist") {
            inputFile.set(file("src/jvmSharedMain/resources/bip39/wordlist_english.txt"))
            packageName.set("io.ethers.crypto.bip39")
            propertyName.set("WORDS")
            data { file ->
                val words = file.readLines().filter { it.isNotBlank() }
                require(words.size == 2048) { "BIP39 wordlist must contain exactly 2048 words, found ${words.size}" }
                ObjectMapper().valueToTree(words)
            }
        }
    }
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                implementation(libs.kotlincrypto.hash.sha3)
                implementation(libs.whyoleg.cryptography.core)
                implementation(libs.whyoleg.cryptography.random)
                implementation(libs.whyoleg.cryptography.jdk)
                implementation(libs.secp256k1.kmp)
                implementation(libs.ditchoom.buffer)
            }
        }

        // Platform-specific native libraries for secp256k1
        jvmMain {
            dependencies {
                runtimeOnly(libs.secp256k1.kmp.jni)
            }
        }

        androidMain {
            dependencies {
                runtimeOnly(libs.secp256k1.kmp.jni.android)
            }
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
                implementation(libs.bundles.jackson)
            }
        }
    }
}
