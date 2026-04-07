import com.fasterxml.jackson.databind.ObjectMapper

plugins {
    `project-conventions`
    `maven-publish-conventions`
    `static-data-generator`
}

staticDataGenerator {
    generators {
        create("bip39EnglishWordlist") {
            inputFile.set(file("src/jvmMain/resources/bip39/wordlist_english.txt"))
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
        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlincrypto.hash.sha3)
                implementation(libs.whyoleg.cryptography.core)
                implementation(libs.whyoleg.cryptography.random)
                implementation(libs.whyoleg.cryptography.jdk)
                implementation(libs.secp256k1.kmp)
                implementation(libs.ditchoom.buffer)

                // JVM-specific native library for secp256k1.
                // When an Android target is added, its dependencies will declare secp256k1-kmp-jni-android instead.
                runtimeOnly(libs.secp256k1.kmp.jni)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
                implementation(libs.bundles.jackson)
            }
        }
    }
}
