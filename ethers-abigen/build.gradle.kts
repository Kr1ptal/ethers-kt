plugins {
    `project-conventions`
    `maven-publish-conventions`
}

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-providers"))
                api(project(":ethers-abi"))

                implementation(libs.kotlin.reflect)
                implementation(libs.kotlinx.atomicfu)
                implementation(libs.kotlinpoet)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
                implementation(libs.kotlin.compileTesting)
            }
        }
    }
}

// Configure both Test and JavaExec tasks (Kotest Gradle plugin uses JavaExec, not Test)
tasks.withType<JavaExec>().configureEach {
    if (name.contains("otest", ignoreCase = true)) {
        systemProperty("abigen.directory", layout.buildDirectory.dir("abigen").get().asFile.absolutePath)

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
}
