package io.ethers.abigen.plugin

import io.ethers.abigen.plugin.task.EthersSolanaIdlTask
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import java.io.File

private const val TASK = "ethersSolanaIdl"
private const val OUTPUT_DIR = "generated/source/ethers-solana/main/kotlin"
private const val DECLARED_ADDRESS = "ShowcaseProgram1111111111111111111111111111"
private const val OVERRIDE_ADDRESS = "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo"

/** A Gradle build applying the plugin, with the showcase IDL in `src/main/idl/io/ethers/programs`. */
private class SolanaIdlBuild(extension: String = "", withIdl: Boolean = true) {
    val dir: File = ProjectBuilder.builder().build().layout.projectDirectory.asFile
    val idlDir = File(dir, "src/main/idl")
    val outputDir = File(dir, "build/$OUTPUT_DIR")

    init {
        if (withIdl) copyIdl("io/ethers/programs")

        @Language("gradle")
        val settings = """
            rootProject.name = 'ethers-solana-idl-plugin-test'

            buildCache {
                local {
                    directory '${File(dir, "local-build-cache").toURI()}'
                }
            }
        """.trimIndent()
        File(dir, "settings.gradle").writeText(settings)
        writeExtension(extension)
    }

    fun writeExtension(extension: String) {
        @Language("gradle")
        val build = """
            plugins {
                id 'base'
                id 'org.jetbrains.kotlin.jvm'
                id 'io.kriptal.ethers.abigen-plugin'
            }

            ethersSolanaIdl {
                $extension
            }
        """.trimIndent()
        File(dir, "build.gradle").writeText(build)
    }

    /** Copy the showcase IDL into [packagePath] of [idlDir], as [fileName]. */
    fun copyIdl(packagePath: String, fileName: String = "showcase.json") {
        val idl = File(EthersSolanaIdlPluginTest::class.java.getResource("/idl/io/ethers/programs/showcase.json")!!.toURI())
        idl.copyTo(File(idlDir, "$packagePath/$fileName"), overwrite = true)
    }

    /** Write an IDL named [program] to [packagePath], with a zero-copy type and account whose layout it cannot describe. */
    fun writeUnsupportedIdl(packagePath: String, program: String = "unsupported") {
        @Language("json")
        val idl = """
            {
              "address": "$DECLARED_ADDRESS",
              "metadata": {"name": "$program", "version": "0.1.0", "spec": "0.1.0"},
              "instructions": [{"name": "ping", "discriminator": [1], "accounts": [], "args": [{"name": "plain", "type": {"defined": {"name": "Plain"}}}]}],
              "accounts": [{"name": "Packed", "discriminator": [2]}],
              "types": [
                {"name": "Plain", "type": {"kind": "struct", "fields": [{"name": "x", "type": "u8"}]}},
                {"name": "Packed", "serialization": "bytemuckunsafe", "repr": {"kind": "rust", "packed": true},
                 "type": {"kind": "struct", "fields": [{"name": "x", "type": "u64"}]}}
              ]
            }
        """.trimIndent()
        File(idlDir, "$packagePath/unsupported.json").apply { parentFile.mkdirs() }.writeText(idl)
    }

    fun generatedFiles(): List<String> = outputDir.walkTopDown().filter(File::isFile).map { it.relativeTo(outputDir).invariantSeparatorsPath }.sorted().toList()

    fun runner(vararg args: String): GradleRunner = GradleRunner.create()
        .withProjectDir(dir)
        .withPluginClasspath()
        .withArguments(*args, "--build-cache", "--stacktrace")
        .forwardOutput()

    fun outcome(vararg args: String): TaskOutcome? = runner(*args).build().task(":$TASK")?.outcome
}

class EthersSolanaIdlPluginTest : FunSpec({
    test("plugin registers the Solana IDL extension and applies its defaults to the task") {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("io.kriptal.ethers.abigen-plugin")

        val ext = project.extensions.getByType(EthersSolanaIdlExtension::class.java)
        ext.outputDir.get() shouldBe OUTPUT_DIR
        ext.sources.get().single().sourceDirectory.get().asFile shouldBe project.file("src/main/idl")

        val task = project.tasks.withType(EthersSolanaIdlTask::class.java).single()
        task.sources.get() shouldBe ext.sources.get()
        task.outputDir.get() shouldBe ext.outputDir.get()
        task.programAddresses.get() shouldBe emptyMap()
        ext.strict.get() shouldBe false
        task.strict.get() shouldBe false
    }

    context("declarations that cannot be generated") {
        val report = "Some Solana IDL declarations were left out of the generated bindings:"
        val packed = "type 'Packed': zero-copy types need a C or transparent layout, but it has Rust(packed=true, align=null)"

        test("are left out with a warning, which a build cache hit or up-to-date bindings repeat") {
            val build = SolanaIdlBuild()
            build.writeUnsupportedIdl("io/ethers/unsupported")
            val result = build.runner(TASK).build()
            result.task(":$TASK")?.outcome shouldBe TaskOutcome.SUCCESS
            result.output shouldContain report
            result.output shouldContain "src/main/idl/io/ethers/unsupported/unsupported.json: program 'unsupported': 2 declaration(s) cannot be generated:"
            result.output shouldContain "- $packed"
            result.output shouldContain "- account 'Packed': depends on type 'Packed', which is not generated [intentionally unsupported]"
            // the IDL without problems reports nothing
            result.output.contains("program 'showcase'") shouldBe false
            build.generatedFiles() shouldBe listOf("io/ethers/programs/Showcase.kt", "io/ethers/unsupported/Unsupported.kt")
            val generated = File(build.outputDir, "io/ethers/unsupported/Unsupported.kt").readText()
            generated shouldContain "public data class Plain("
            generated shouldContain " * - $packed"

            val upToDate = build.runner(TASK).build()
            upToDate.task(":$TASK")?.outcome shouldBe TaskOutcome.UP_TO_DATE
            upToDate.output shouldContain "- $packed"

            val cached = build.runner("clean", TASK).build()
            cached.task(":$TASK")?.outcome shouldBe TaskOutcome.FROM_CACHE
            cached.output shouldContain report
            cached.output shouldContain "- $packed"
        }

        test("fail the build in strict mode, listing them for every IDL") {
            val build = SolanaIdlBuild("strict = true")
            build.writeUnsupportedIdl("io/ethers/unsupported")
            build.writeUnsupportedIdl("io/ethers/other", "other")
            val result = build.runner(TASK).buildAndFail()
            result.output shouldContain "Strict Solana IDL generation failed, as some declarations cannot be generated:"
            result.output shouldContain "src/main/idl/io/ethers/unsupported/unsupported.json: program 'unsupported'"
            result.output shouldContain "src/main/idl/io/ethers/other/unsupported.json: program 'other'"
            result.output shouldContain "- $packed"
            result.output.contains(report) shouldBe false

            // the setting is an input: leaving strict mode generates the bindings
            build.writeExtension("strict = false")
            build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        }
    }

    test("bindings are generated from src/main/idl, in the package of the IDL's directory") {
        val build = SolanaIdlBuild()
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS

        build.generatedFiles() shouldBe listOf("io/ethers/programs/Showcase.kt")
        val generated = File(build.outputDir, "io/ethers/programs/Showcase.kt").readText()
        generated shouldContain "package io.ethers.programs"
        generated shouldContain "SolanaAddress(\"$DECLARED_ADDRESS\")"
    }

    test("the generated bindings are compiled with the project's sources") {
        val build = SolanaIdlBuild()
        val result = build.runner("compileKotlin", "--dry-run").build()
        result.output shouldContain ":$TASK SKIPPED"
    }

    test("a project without IDLs skips the task") {
        SolanaIdlBuild(withIdl = false).outcome(TASK) shouldBe TaskOutcome.NO_SOURCE
    }

    test("results are loaded from the build cache, including after the project is moved") {
        val build = SolanaIdlBuild()
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        build.outcome("clean", TASK) shouldBe TaskOutcome.FROM_CACHE

        val moved = ProjectBuilder.builder().build().layout.projectDirectory.asFile
        build.dir.copyRecursively(moved, overwrite = true)
        File(moved, "build").deleteRecursively()
        val result = GradleRunner.create().withProjectDir(moved).withPluginClasspath().withArguments(TASK, "--build-cache").build()
        result.task(":$TASK")?.outcome shouldBe TaskOutcome.FROM_CACHE
    }

    test("the task is compatible with the configuration cache") {
        val build = SolanaIdlBuild()
        build.runner("clean", TASK, "--configuration-cache").build().task(":$TASK")?.outcome shouldBe TaskOutcome.SUCCESS
        val reused = build.runner("clean", TASK, "--configuration-cache").build()
        reused.output shouldContain "Reusing configuration cache"
        reused.task(":$TASK")?.outcome shouldBe TaskOutcome.FROM_CACHE
    }

    test("an address override replaces the address the IDL declares, and invalidates previous output") {
        val build = SolanaIdlBuild()
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS

        build.writeExtension("programAddress('showcase', '$OVERRIDE_ADDRESS')")
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        val generated = File(build.outputDir, "io/ethers/programs/Showcase.kt").readText()
        generated shouldContain "SolanaAddress(\"$OVERRIDE_ADDRESS\")"
        generated.contains(DECLARED_ADDRESS) shouldBe false
    }

    test("an address override for a program no IDL declares fails the build") {
        val build = SolanaIdlBuild("programAddress('shwocase', '$OVERRIDE_ADDRESS')")
        val result = build.runner(TASK).buildAndFail()
        result.output shouldContain "programAddresses names programs that no IDL declares: [shwocase]"
        result.output shouldContain "Declared programs: [showcase]"
    }

    test("an invalid address override fails the build") {
        val build = SolanaIdlBuild("programAddresses.put('showcase', 'not-an-address')")
        build.runner(TASK).buildAndFail().output shouldContain "Invalid address override for program 'showcase'"
    }

    test("integer PDA seeds are little-endian unless a byte order is configured for the program or the seed") {
        val littleEndian = "BorshWriter(2).apply { writeU16(paramsTag) }.toByteArray(),"
        val bigEndian = "BorshWriter(2).apply { writeU16(paramsTag) }.toByteArray().also { it.reverse() }"
        val build = SolanaIdlBuild()
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        File(build.outputDir, "io/ethers/programs/Showcase.kt").readText() shouldContain littleEndian

        // Groovy converts the order's name to the enum
        build.writeExtension("seedByteOrder('showcase', 'BIG_ENDIAN')")
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        File(build.outputDir, "io/ethers/programs/Showcase.kt").readText() shouldContain bigEndian

        build.writeExtension("seedByteOrder('showcase', 'vault', 'params.tag', 'BIG_ENDIAN')")
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        val perSeed = File(build.outputDir, "io/ethers/programs/Showcase.kt").readText()
        perSeed shouldContain bigEndian
        // the other integer seeds keep the default
        perSeed shouldContain "BorshWriter(8).apply { writeU64(nonce) }.toByteArray(),"
    }

    test("a byte order for a program no IDL declares, or for a seed that is not an integer seed, fails the build") {
        SolanaIdlBuild("seedByteOrder('shwocase', 'BIG_ENDIAN')").runner(TASK).buildAndFail().output shouldContain
            "seedByteOrders names programs that no IDL declares: [shwocase]"
        SolanaIdlBuild("seedByteOrder('showcase', 'vault', 'owner', 'BIG_ENDIAN')").runner(TASK).buildAndFail().output shouldContain
            "seed byte order for account 'vault' seed 'owner': no PDA account has an integer seed at that path"
    }

    test("a legacy IDL generates once an address is given for it") {
        val build = SolanaIdlBuild(withIdl = false)
        @Language("json")
        val legacy = """
            {
              "version": "0.1.0",
              "name": "legacy_program",
              "instructions": [{"name": "doThing", "accounts": [{"name": "payerAccount", "isMut": true, "isSigner": true}], "args": [{"name": "amount", "type": "u64"}]}],
              "accounts": [{"name": "Vault", "type": {"kind": "struct", "fields": [{"name": "owner", "type": "publicKey"}]}}]
            }
        """.trimIndent()
        File(build.idlDir, "io/ethers/legacy/legacy.json").apply { parentFile.mkdirs() }.writeText(legacy)

        build.runner(TASK).buildAndFail().output shouldContain
            "program 'legacy_program': the IDL declares no address, so one must be given, such as with the Gradle plugin's programAddress"

        build.writeExtension("programAddress('legacy_program', '$OVERRIDE_ADDRESS')")
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        val generated = File(build.outputDir, "io/ethers/legacy/LegacyProgram.kt").readText()
        generated shouldContain "SolanaAddress(\"$OVERRIDE_ADDRESS\")"
        generated shouldContain "public fun doThing("
        generated shouldContain "public val payerAccount: SolanaAddress"
    }

    test("two IDLs generating the same declaration fail the build") {
        val build = SolanaIdlBuild()
        build.copyIdl("io/ethers/programs", "showcase-copy.json")
        build.runner(TASK).buildAndFail().output shouldContain "both generate io.ethers.programs.Showcase"
    }

    test("moving an IDL removes the bindings generated from its previous location") {
        val build = SolanaIdlBuild()
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS

        build.idlDir.deleteRecursively()
        build.copyIdl("io/ethers/moved")
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        build.generatedFiles() shouldBe listOf("io/ethers/moved/Showcase.kt")
    }

    test("directory sources add to the default one, and can override the package") {
        val build = SolanaIdlBuild("directorySource('src/main/idl-extra') { packageOverride = 'com.example.extra' }")
        File(build.idlDir, "io/ethers/programs/showcase.json").copyTo(File(build.dir, "src/main/idl-extra/nested/showcase.json"))
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        build.generatedFiles() shouldBe listOf("com/example/extra/Showcase.kt", "io/ethers/programs/Showcase.kt")

        // the default directory may be missing when another source has IDLs
        build.idlDir.deleteRecursively()
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        build.generatedFiles() shouldBe listOf("com/example/extra/Showcase.kt")
    }

    test("setting the sources replaces the default one") {
        val build = SolanaIdlBuild("sources.set([]); directorySource('src/main/idl-extra')")
        build.copyIdl("../idl-extra/com/example")
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        build.generatedFiles() shouldBe listOf("com/example/Showcase.kt")
    }
})
