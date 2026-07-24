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

        create("bip39EnglishTestVectors") {
            inputFile.set(file("src/jvmSharedTest/resources/bip39/wordlist_english_test_vector.json"))
            packageName.set("io.ethers.crypto.bip39")
            propertyName.set("VECTORS")
            sourceSetName.set("commonTest")
            data { file ->
                ObjectMapper().readTree(file)
            }
        }
    }
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.bignumkt)
                implementation(libs.kotlincrypto.hash.sha3)
                implementation(libs.whyoleg.cryptography.core)
                implementation(libs.whyoleg.cryptography.random)
                implementation(libs.secp256k1.kmp)
                implementation(libs.ditchoom.buffer)
            }
        }

        val jvmSharedMain by getting {
            dependencies {
                // JVM + Android provider for whyoleg cryptography (SHA256, HMAC, RIPEMD160)
                implementation(libs.whyoleg.cryptography.jdk)
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
    }
}
