plugins {
    `project-conventions`
    `maven-publish-conventions`
}

dependencies {
    implementation(libs.kotlincrypto.hash.sha3)
    implementation(libs.whyoleg.cryptography.core)
    implementation(libs.whyoleg.cryptography.random)
    implementation(libs.whyoleg.cryptography.jdk)
    implementation(libs.secp256k1.kmp)
    runtimeOnly(libs.secp256k1.kmp.jni)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.jackson)
}
