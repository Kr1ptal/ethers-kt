package io.ethers.solana.idl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

/** A directory with a supported IDL and one with an unsupported generic type, and an output directory. */
private class CliRun {
    val idlDir: File = Files.createTempDirectory("idl").toFile()
    val outputDir: File = Files.createTempDirectory("out").toFile()

    init {
        for (name in listOf("relations", "generics")) {
            File(SolanaIdlCliTest::class.java.getResource("/idl/$name.json")!!.toURI()).copyTo(File(idlDir, "$name.json"))
        }
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
        err shouldContain "warning: program 'generics': "
        err shouldContain "generic type definitions are not supported yet [not supported yet]"
        err shouldContain "generics.json)"
        err shouldNotContain "program 'relations'"
        run.generated() shouldBe listOf("Generics.kt", "RelationsDerivation.kt")
    }

    test("strict mode processes every IDL, then fails listing what cannot be generated") {
        val run = CliRun()
        File(SolanaIdlCliTest::class.java.getResource("/idl/generics.json")!!.toURI()).copyTo(File(run.idlDir, "nested/generics_copy.json"))
        val (status, err) = run.run("--strict")
        status shouldBe 1
        err shouldNotContain "warning:"
        err shouldContain "error: strict generation failed, as some declarations cannot be generated:"
        // both IDLs with unsupported declarations are listed
        err shouldContain "generics.json)"
        err shouldContain "generics_copy.json)"
        run.generated().contains("RelationsDerivation.kt") shouldBe true
    }
})
