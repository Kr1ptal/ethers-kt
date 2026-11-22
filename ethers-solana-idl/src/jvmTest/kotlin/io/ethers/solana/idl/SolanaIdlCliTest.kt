package io.ethers.solana.idl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

/** An Anchor IDL with a type of custom serialization, which cannot be generated. */
private const val UNSUPPORTED_IDL = """{
  "address": "11111111111111111111111111111111",
  "metadata": { "name": "unsupported", "version": "0.1.0", "spec": "0.1.0" },
  "instructions": [],
  "types": [
    { "name": "Compressed", "serialization": { "custom": "Lz4" }, "type": { "kind": "struct", "fields": [{ "name": "x", "type": "u8" }] } }
  ]
}"""

/** A directory with a supported IDL and one with a type of custom serialization, and an output directory. */
private class CliRun {
    val idlDir: File = Files.createTempDirectory("idl").toFile()
    val outputDir: File = Files.createTempDirectory("out").toFile()

    init {
        File(SolanaIdlCliTest::class.java.getResource("/idl/relations.json")!!.toURI()).copyTo(File(idlDir, "relations.json"))
        File(idlDir, "unsupported.json").writeText(UNSUPPORTED_IDL)
    }

    fun run(vararg options: String): Pair<Int, String> {
        val err = ByteArrayOutputStream()
        val status = runCli(arrayOf(idlDir.path, outputDir.path, "test.pkg", *options), PrintStream(err, true))
        return status to err.toString()
    }

    fun generated(): List<String> = outputDir.walkTopDown().filter(File::isFile).map { it.name }.sorted().toList()
}

class SolanaIdlCliTest : FunSpec({
    test("unsupported declarations are left out with a warning, and every IDL is generated") {
        val run = CliRun()
        val (status, err) = run.run()
        status shouldBe 0
        err shouldContain "warning: program 'unsupported': "
        err shouldContain "serialization is intentionally unsupported"
        err shouldContain "unsupported.json)"
        err shouldNotContain "program 'relations'"
        run.generated() shouldBe listOf("RelationsDerivation.kt", "Unsupported.kt")
    }

    test("strict mode processes every IDL, then fails listing what cannot be generated") {
        val run = CliRun()
        File(run.idlDir, "nested").mkdirs()
        File(run.idlDir, "nested/unsupported_copy.json").writeText(UNSUPPORTED_IDL)
        val (status, err) = run.run("--strict")
        status shouldBe 1
        err shouldNotContain "warning:"
        err shouldContain "error: strict generation failed, as some declarations cannot be generated:"
        // both IDLs with unsupported declarations are listed
        err shouldContain "unsupported.json)"
        err shouldContain "unsupported_copy.json)"
        run.generated().contains("RelationsDerivation.kt") shouldBe true
    }
})
