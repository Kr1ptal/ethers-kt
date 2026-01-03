plugins {
    `project-conventions`
    `maven-publish-conventions`
    `static-data-generator`
}

staticDataGenerator {
    generators {
        create("bip39Wordlist") {
            inputFile.set(file("src/main/resources/bip39/wordlist_english.txt"))
            packageName.set("io.ethers.crypto.bip39")
            objectName.set("BIP39WordListEnglishData")
            generatorType.set("bip39-wordlist")
        }
    }
}

// Create separate runtime configurations for each platform
// These extend from 'implementation' (not runtimeClasspath) to avoid inheriting
// the runtimeOnly JVM JNI, allowing each variant to declare its own JNI dependency
val jvmRuntimeElements: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    extendsFrom(configurations.implementation.get())
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.EXTERNAL))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM))
    }
}

val androidRuntimeElements: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    extendsFrom(configurations.implementation.get())
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.EXTERNAL))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.ANDROID))
    }
}

dependencies {
    // Core dependencies (same for all platforms)
    implementation(libs.kotlincrypto.hash.sha3)
    implementation(libs.whyoleg.cryptography.core)
    implementation(libs.whyoleg.cryptography.random)
    implementation(libs.whyoleg.cryptography.jdk)
    implementation(libs.secp256k1.kmp)
    implementation(libs.ditchoom.buffer)

    // JVM JNI - available transitively for internal project dependencies and their tests
    // This is NOT included in the published variant configurations (they use implementation, not runtimeClasspath)
    runtimeOnly(libs.secp256k1.kmp.jni)

    // Platform-specific JNI dependencies for published variants
    jvmRuntimeElements(libs.secp256k1.kmp.jni)
    androidRuntimeElements(libs.secp256k1.kmp.jni.android)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.jackson)
}

// Add artifacts to the platform-specific configurations and register them with the component
afterEvaluate {
    val jarTask = tasks.named("jar")

    artifacts {
        add("jvmRuntimeElements", jarTask)
        add("androidRuntimeElements", jarTask)
    }

    // Add custom configurations to the Java component for publishing
    val javaComponent = components["java"] as AdhocComponentWithVariants
    javaComponent.addVariantsFromConfiguration(jvmRuntimeElements) {
        mapToMavenScope("runtime")
    }
    javaComponent.addVariantsFromConfiguration(androidRuntimeElements) {
        mapToMavenScope("runtime")
        mapToOptional() // Excludes from POM, keeps in Gradle Module Metadata
    }
}
