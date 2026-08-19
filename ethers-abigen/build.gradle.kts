plugins {
    `project-conventions`
    `maven-publish-conventions`
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

val generatedPackage = "io.ethers.abigen.gen"

// The same fixtures the JVM-side tests compile in-memory via `AbigenCompiler`.
val abiDir = layout.projectDirectory.dir("src/jvmSharedTest/resources/abi")

// Must live under `build/generated` - `ktlint-conventions` excludes that path, and generated code is not
// formatted to this project's ktlint config.
val generatedSourceDir = layout.buildDirectory.dir("generated/source/abigen/commonTest/kotlin")

// Generates contract wrappers into `commonTest`, so they are compiled for every target the project supports.
// `AbigenCompiler` only ever compiles them for the JVM, which cannot catch a `java.math` type or a `javaClass`
// call leaking into the output - a native compile can.
//
// This runs the generator as a plain JVM process against this module's own JVM output rather than applying
// `io.kriptal.ethers.abigen-plugin`, because a Gradle plugin cannot be applied to a project of the build that
// defines it.
val generateContractWrappers by tasks.registering(JavaExec::class) {
    description = "Generates contract wrappers for the test ABIs into the commonTest source set."

    inputs.dir(abiDir)
        .withPropertyName("abis")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedSourceDir).withPropertyName("generatedSources")
    outputs.cacheIf { true }

    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass = "io.ethers.abigen.AbigenCliKt"

    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                abiDir.asFile.absolutePath,
                generatedSourceDir.get().asFile.absolutePath,
                generatedPackage,
            )
        },
    )
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                api(project(":ethers-core"))
                api(project(":ethers-providers"))
                api(project(":ethers-abi"))
                api(libs.bignumkt)

                implementation(libs.kotlin.reflect)
                implementation(libs.kotlinx.atomicfu)
                implementation(libs.kotlinpoet)
            }
        }

        val commonTest by getting {
            // registering the task itself wires up the dependency for every consumer: compile, ktlint, sourcesJar
            kotlin.srcDir(generateContractWrappers)

            dependencies {
                // the module's own code is JVM-bound and declares these in jvmSharedMain, which the Apple test
                // compilations cannot see - the generated wrappers need them on every target
                implementation(project(":ethers-core"))
                implementation(project(":ethers-providers"))
                implementation(project(":ethers-abi"))
                implementation(libs.bignumkt)
            }
        }

        val jvmSharedTest by getting {
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
