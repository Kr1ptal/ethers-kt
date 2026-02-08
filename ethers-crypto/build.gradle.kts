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
        jvmMain {
            dependencies {
                implementation(libs.kotlincrypto.hash.sha3)
                implementation(libs.whyoleg.cryptography.core)
                implementation(libs.whyoleg.cryptography.random)
                implementation(libs.whyoleg.cryptography.jdk)
                implementation(libs.secp256k1.kmp)
                implementation(libs.ditchoom.buffer)

                // JVM JNI - included as runtimeOnly for internal project dependencies and tests.
                // TODO: When adding androidTarget(), use platform-specific JNI variants instead
                runtimeOnly(libs.secp256k1.kmp.jni)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.bundles.kotest)
                implementation(libs.bundles.jackson)
            }
        }
    }
}
