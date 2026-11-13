plugins {
    `project-conventions`
    `maven-publish-conventions`
    alias(libs.plugins.kotlin.serialization)
}

description = "Reads Solana program IDLs and generates Kotlin bindings for them."

val generatedPackage = "io.ethers.solana.idl.test"

// IDLs whose bindings the commonTest suite exercises.
val idlDir = layout.projectDirectory.dir("src/commonTest/idl")

// Raydium's concentrated liquidity AMM, one of the Anchor fixtures: zero-copy accounts and big-endian seeds.
val ammV3Idl = layout.projectDirectory.file("src/jvmSharedTest/resources/idl/amm_v3.json")

// Meteora's DLMM: unpacked zero-copy accounts.
val dlmmIdl = layout.projectDirectory.file("src/jvmSharedTest/resources/idl/dlmm.json")

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
    inputs.file(ammV3Idl)
        .withPropertyName("ammV3Idl")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(dlmmIdl)
        .withPropertyName("dlmmIdl")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(generatedSourceDir).withPropertyName("generatedSources")
    outputs.cacheIf { true }

    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    classpath(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass = "io.ethers.solana.idl.SolanaIdlCliKt"

    // Resolve script-owned values during configuration so execution does not capture the build script.
    args(
        listOf(idlDir.asFile.absolutePath, generatedSourceDir.get().asFile.absolutePath, generatedPackage) +
            listOf("--idl", ammV3Idl.asFile.absolutePath, "--idl", dlmmIdl.asFile.absolutePath) +
            // one seed the showcase program encodes big-endian, the others keep the little-endian default
            listOf("--seed-byte-order", "showcase:position:config.fee_bps=BIG_ENDIAN") +
            // Raydium encodes every integer seed big-endian
            listOf("--seed-byte-order", "amm_v3=BIG_ENDIAN"),
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
