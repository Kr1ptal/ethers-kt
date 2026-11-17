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

    context("generator overrides") {
        val token = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        val dsl = listOf(
            "rename('showcase', 'type:Mode', 'SwapMode')",
            "accountDefault('showcase', 'swap.referrer', '$token')",
            "customCodec('showcase', 'showcase::Point', 'com.example.PointCodec', 8)",
            "typeSubstitution('showcase', 'Amount', 'com.example.TokenAmount', 'com.example.TokenAmount.Codec', 8, 8)",
        )
        fun showcase(build: SolanaIdlBuild) = File(build.outputDir, "io/ethers/programs/Showcase.kt").readText()

        test("the DSL configures custom codecs, substitutions, renames and account defaults") {
            val build = SolanaIdlBuild(dsl.joinToString("\n"))
            build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
            val generated = showcase(build)
            generated shouldContain "public sealed class SwapMode"
            generated shouldContain "SolanaAddress? = SolanaAddress(\"$token\")"
            generated shouldContain "public companion object : SolanaCodec<ShowcasePoint> by PointCodec"
            generated shouldContain "TokenAmount.Codec.read(this)"
            build.outcome(TASK) shouldBe TaskOutcome.UP_TO_DATE

            // a changed override regenerates
            build.writeExtension((dsl.drop(1) + "rename('showcase', 'type:Mode', 'TradeMode')").joinToString("\n"))
            build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
            showcase(build) shouldContain "public sealed class TradeMode"
        }

        test("the order overrides are configured in does not change the output or the cache key") {
            val build = SolanaIdlBuild(dsl.joinToString("\n"))
            build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
            val first = showcase(build)
            build.writeExtension(dsl.reversed().joinToString("\n"))
            build.outcome("clean", TASK) shouldBe TaskOutcome.FROM_CACHE
            showcase(build) shouldBe first
        }

        test("overrides files are task inputs, merged with the DSL") {
            val build = SolanaIdlBuild("overridesFile('overrides.json')\n" + dsl[1])
            val file = File(build.dir, "overrides.json")
            file.writeText("""{"showcase": {"renames": {"type:Mode": "SwapMode"}}}""")
            build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
            showcase(build) shouldContain "public sealed class SwapMode"
            showcase(build) shouldContain "SolanaAddress(\"$token\")"
            build.outcome(TASK) shouldBe TaskOutcome.UP_TO_DATE

            file.writeText("""{"showcase": {"renames": {"type:Mode": "TradeMode"}}}""")
            build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
            showcase(build) shouldContain "public sealed class TradeMode"

            // the file and the DSL must agree on a target both configure
            build.writeExtension("overridesFile('overrides.json')\nrename('showcase', 'type:Mode', 'SwapMode')")
            build.runner(TASK).buildAndFail().output shouldContain
                "program 'showcase': conflicting rename for 'type:Mode': SwapMode and TradeMode"
        }

        test("the task with overrides is compatible with the configuration cache") {
            val build = SolanaIdlBuild("overridesFile('overrides.json')\n" + dsl.joinToString("\n"))
            File(build.dir, "overrides.json").writeText("""{"showcase": {"renames": {"constant:MAX_FEE": "FEE_LIMIT"}}}""")
            build.runner("clean", TASK, "--configuration-cache").build().task(":$TASK")?.outcome shouldBe TaskOutcome.SUCCESS
            val reused = build.runner("clean", TASK, "--configuration-cache").build()
            reused.output shouldContain "Reusing configuration cache"
            reused.task(":$TASK")?.outcome shouldBe TaskOutcome.FROM_CACHE
            showcase(build) shouldContain "FEE_LIMIT"
        }

        test("overrides for unknown programs or targets, and conflicting ones, fail the build") {
            SolanaIdlBuild("rename('shwocase', 'type:Mode', 'SwapMode')").runner(TASK).buildAndFail().output shouldContain
                "generatorOverrides names programs that no IDL declares: [shwocase]"
            SolanaIdlBuild("rename('showcase', 'type:Missing', 'X')").runner(TASK).buildAndFail().output shouldContain
                "rename 'type:Missing': the program declares no type 'Missing'"
            SolanaIdlBuild("rename('showcase', 'type:Mode', 'A')\nrename('showcase', 'type:Mode', 'B')").runner(TASK).buildAndFail().output shouldContain
                "ethersSolanaIdl overrides of program 'showcase': conflicting rename for 'type:Mode': A and B"
            SolanaIdlBuild("accountDefault('showcase', 'swap.referrer', 'nope!')").runner(TASK).buildAndFail().output shouldContain
                "account default for 'swap.referrer': 'nope!' is not a base58 address"
            SolanaIdlBuild("customCodec('showcase', 'showcase::Point', 'com.example.PointCodec', 4)").runner(TASK).buildAndFail().output shouldContain
                "custom codec for type 'showcase::Point': declares size 4, but the IDL lays the type out in 8 bytes"
        }
    }

    test("setting the sources replaces the default one") {
        val build = SolanaIdlBuild("sources.set([]); directorySource('src/main/idl-extra')")
        build.copyIdl("../idl-extra/com/example")
        build.outcome(TASK) shouldBe TaskOutcome.SUCCESS
        build.generatedFiles() shouldBe listOf("com/example/Showcase.kt")
    }
})
