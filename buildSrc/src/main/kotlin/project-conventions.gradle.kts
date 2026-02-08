import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    idea
    eclipse
    kotlin("multiplatform")
    id("kotlin-project-conventions")
    id("ktlint-conventions")
}

// Override incorrect JVM version metadata in secp256k1-kmp
// The library targets Java 8 bytecode but declares JVM 21 in Gradle Module Metadata
dependencies {
    components {
        listOf(
            "fr.acinq.secp256k1:secp256k1-kmp-jni-jvm",
            "fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-darwin",
            "fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-linux",
            "fr.acinq.secp256k1:secp256k1-kmp-jni-jvm-mingw",
        ).forEach { module ->
            withModule(module) {
                allVariants {
                    attributes {
                        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                    }
                }
            }
        }
    }
}
