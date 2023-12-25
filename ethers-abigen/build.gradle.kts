plugins {
    `project-conventions`
    `maven-publish-conventions`
}

dependencies {
    implementation(project(":ethers-core"))
    implementation(project(":ethers-providers"))
    implementation(project(":ethers-signers"))
    implementation(project(":ethers-abi"))

    implementation(libs.kotlinpoet) {
        // don't need this dependency: https://square.github.io/kotlinpoet/#kotlin-reflect
        exclude(module = "kotlin-reflect")
    }

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotlin.compileTesting)
}

tasks.withType<Test>().all {
    // Keep the property name in sync with the one in AbigenCompiler.kt
    systemProperty("abigen.directory", layout.buildDirectory.dir("abigen").get().asFile.absolutePath)

    // https://github.com/tschuchortdev/kotlin-compile-testing?tab=readme-ov-file#java-16-compatibility
    if (JavaVersion.current() >= JavaVersion.VERSION_16) {
        jvmArgs(
            "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        )
    }
}
