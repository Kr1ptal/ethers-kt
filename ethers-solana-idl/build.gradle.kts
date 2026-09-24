plugins {
    `project-conventions`
    `maven-publish-conventions`
    alias(libs.plugins.kotlin.serialization)
}

description = "Reads Solana program IDLs and generates Kotlin bindings for them."

val generatedPackage = "io.ethers.solana.idl.test"

// IDLs whose bindings the commonTest suite exercises.
val idlDir = layout.projectDirectory.dir("src/commonTest/idl")

// Must live under `build/generated` - `ktlint-conventions` excludes that path, and generated code is not
// formatted to this project's ktlint config.
val generatedSourceDir = layout.buildDirectory.dir("generated/source/solanaIdl/commonTest/kotlin")

// Generates program bindings into `commonTest`, so they are compiled and exercised for every target the project
// supports. Runs the generator as a plain JVM process against this module's own JVM output, as ethers-abigen does.
val generateProgramBindings by tasks.registering(JavaExec::class) {
    description = "Generates program bindings for the test IDLs into the commonTest source set."

    inputs.dir(idlDir)
        .withPropertyName("idls")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedSourceDir).withPropertyName("generatedSources")
    outputs.cacheIf { true }

    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass = "io.ethers.solana.idl.SolanaIdlCliKt"

    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(idlDir.asFile.absolutePath, generatedSourceDir.get().asFile.absolutePath, generatedPackage)
        },
    )
}

kotlin {
    sourceSets {
        val jvmSharedMain by getting {
            dependencies {
                api(project(":ethers-common"))

                implementation(libs.kotlinx.serialization)
                implementation(libs.kotlinpoet)
            }
        }

        val commonTest by getting {
            // registering the task itself wires up the dependency for every consumer: compile, ktlint, sourcesJar
            kotlin.srcDir(generateProgramBindings)

            dependencies {
                // the generated bindings run against the Solana runtime, which the generator itself does not need
                implementation(project(":ethers-solana"))
                implementation(libs.bignumkt)
            }
        }

        val jvmSharedTest by getting {
            dependencies {
                implementation(libs.bundles.kotest)
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    from("NOTICE") { into("META-INF") }
}
