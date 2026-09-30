import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import java.security.MessageDigest

plugins {
    `project-conventions`
    `jmh-conventions`
    `maven-publish-conventions`
    alias(libs.plugins.kotlin.serialization)
}

description = "Async, high-performance Kotlin library for interacting with Solana. Targets JVM, Android, macOS and iOS."

/** Embed committed JSONL verbatim for commonTest, which has no portable classpath-resource API. */
@CacheableTask
abstract class GenerateSolanaCorpus : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val corpusDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val input = corpusDirectory.get().asFile
        val expectedFiles = input.resolve("SHA256SUMS").readLines().filter(String::isNotBlank).map { line ->
            val (expected, name) = line.split(Regex("\\s+"), limit = 2)
            require(name.matches(Regex("[a-z0-9-]+\\.jsonl"))) { "Invalid corpus filename" }
            val actual = MessageDigest.getInstance("SHA-256").digest(input.resolve(name).readBytes())
                .joinToString("") { "%02x".format(it) }
            require(actual == expected) { "Corpus checksum mismatch: $name" }
            name
        }
        require(input.listFiles()!!.filter { it.extension == "jsonl" }.map { it.name }.toSet() == expectedFiles.toSet()) { "Corpus checksum inventory is incomplete" }
        val output = outputDirectory.get().asFile.resolve("io/ethers/solana/corpus").also { it.mkdirs() }
        val chunks = input.listFiles()!!.filter { it.extension == "jsonl" }.sortedBy { it.name }
            .flatMap { it.readLines().filter(String::isNotBlank).chunked(20) }
        require(chunks.isNotEmpty()) { "The committed Solana transaction corpus is missing" }
        fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        chunks.forEachIndexed { index, records ->
            output.resolve("SolanaCorpusChunk$index.kt").writeText(
                buildString {
                    appendLine("// Generated from committed corpus JSONL. Do not edit.")
                    appendLine("package io.ethers.solana.corpus")
                    appendLine("internal object SolanaCorpusChunk$index {")
                    appendLine("    fun records(): List<String> = listOf(${records.indices.joinToString { "record$it()" }})")
                    records.forEachIndexed { recordIndex, raw ->
                        // Bound individual JVM constants and method sizes even for large RPC log arrays.
                        appendLine("    private fun record$recordIndex(): String = listOf(")
                        raw.chunked(4000).forEach { appendLine("        ${quoted(it)},") }
                        appendLine("    ).joinToString(\"\")")
                    }
                    appendLine("}")
                },
            )
        }
        output.resolve("SolanaCorpusData.kt").writeText(
            buildString {
                appendLine("// Generated from committed corpus JSONL. Do not edit.")
                appendLine("package io.ethers.solana.corpus")
                appendLine("internal fun transactionCorpus(): List<String> = buildList {")
                chunks.indices.forEach { appendLine("    addAll(SolanaCorpusChunk$it.records())") }
                appendLine("}")
            },
        )
    }
}

val generateSolanaCorpus = tasks.register<GenerateSolanaCorpus>("generateSolanaCorpus") {
    corpusDirectory.set(layout.projectDirectory.dir("src/commonTest/resources/transactions"))
    outputDirectory.set(layout.buildDirectory.dir("generated/source/solanaCorpus/commonTest/kotlin"))
}

kotlin {
    the<KotlinMultiplatformAndroidLibraryTarget>().withDeviceTestBuilder {
        sourceSetTreeName = "deviceTest"
    }.configure {
        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":ethers-rpc"))
                implementation(libs.ditchoom.buffer)
                implementation(libs.whyoleg.cryptography.core)
            }
        }
        val jvmSharedMain by getting {
            dependencies {
                implementation(libs.sol4k.tweetnacl)
            }
        }
        val nativeMain by getting {
            dependencies {
                implementation(libs.whyoleg.cryptography.openssl3)
            }
        }
        commonTest {
            kotlin.srcDir(generateSolanaCorpus)
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
            }
        }
        val jvmJmh by getting {
            dependencies {
                implementation(libs.jmh.core)
                implementation(libs.jmh.generator)
            }
        }
        named("androidDeviceTest") {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("androidx.test:runner:1.6.2")
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    from("NOTICE") { into("META-INF") }
    from(rootProject.file("LICENSE-APACHE")) { into("META-INF") }
}
