package io.ethers.solana.idl.reader

import io.ethers.core.ThrowableError
import io.ethers.solana.idl.IdlDiscriminator
import io.ethers.solana.idl.IdlInstruction
import io.ethers.solana.idl.IdlProgram
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.InputStream

private const val ADDRESS = "11111111111111111111111111111111"

/**
 * A made-up line format, `toy:<name>:<instruction>=<discriminator byte>,...`, standing in for a user's
 * own IDL format. Anything without the [prefix] is not its format.
 */
private class ToyReader(private val prefix: String) : SolanaIdlReader {
    var reads = 0

    override fun read(idl: InputStream): IdlProgram {
        val text = idl.bufferedReader().readText()
        check(text.startsWith(prefix)) { "not a $prefix IDL" }
        reads++
        val (name, instructions) = text.removePrefix(prefix).split(':', limit = 2)
        return IdlProgram(
            name = name,
            version = "1.0.0",
            address = ADDRESS,
            instructions = instructions.split(',').filter(String::isNotEmpty).map { entry ->
                val (ix, byte) = entry.split('=')
                IdlInstruction(ix, IdlDiscriminator(listOf(byte.toInt())))
            },
        )
    }
}

private val ANCHOR = """
    {
      "address": "$ADDRESS",
      "metadata": {"name": "anchor", "version": "0.1.0", "spec": "0.1.0"},
      "instructions": [{"name": "ix", "discriminator": [1], "accounts": [], "args": []}]
    }
"""

class SolanaIdlReaderRegistryTest : FunSpec({
    test("a custom reader reads its own format, and the built-in reader still reads Anchor IDLs") {
        SolanaIdlReaderRegistry.prependReader(ToyReader("toy-a:"))

        val program = SolanaIdlReaderRegistry.tryReadIdl("toy-a:counter:increment=1,reset=2").unwrap()
        program.name shouldBe "counter"
        program.instructions.map { it.name } shouldBe listOf("increment", "reset")

        SolanaIdlReaderRegistry.tryReadIdl(ANCHOR).unwrap().name shouldBe "anchor"
    }

    test("a reader can be a lambda") {
        SolanaIdlReaderRegistry.appendReader { stream ->
            val text = stream.bufferedReader().readText()
            check(text == "lambda-idl") { "not a lambda IDL" }
            IdlProgram("lambda", "1.0.0", ADDRESS)
        }
        SolanaIdlReaderRegistry.tryReadIdl("lambda-idl").unwrap().name shouldBe "lambda"
    }

    test("a custom reader's output is validated, and the remaining readers are not tried") {
        val first = ToyReader("toy-b:")
        val fallback = ToyReader("toy-b:")
        SolanaIdlReaderRegistry.prependReader(first)
        SolanaIdlReaderRegistry.appendReader(fallback)

        val error = SolanaIdlReaderRegistry.tryReadIdl("toy-b:dupe:a=1,b=1").unwrapError()
        val invalid = error.shouldBeInstanceOf<SolanaIdlReaderRegistry.IdlReadError.Invalid>()
        invalid.program.name shouldBe "dupe"
        invalid.cause.message shouldBe "instruction 'b': discriminator [1] cannot be told apart from instruction 'a' [1]"
        first.reads shouldBe 1
        fallback.reads shouldBe 0
    }

    test("when no reader can read the input, every reader's failure is kept") {
        val error = SolanaIdlReaderRegistry.tryReadIdl("{\"neither\": true}").unwrapError()
        val unreadable = error.shouldBeInstanceOf<SolanaIdlReaderRegistry.IdlReadError.Unreadable>()

        // the Anchor reader could not decode it, and each custom reader said it was not its format
        unreadable.causes.filterIsInstance<SerializationException>() shouldHaveSize 1
        unreadable.causes.filterIsInstance<IllegalStateException>().isNotEmpty() shouldBe true

        val exception = shouldThrow<ThrowableError.Exception> { SolanaIdlReaderRegistry.tryReadIdl("{}").unwrap() }
        exception.suppressed.size shouldBe (exception.error as SolanaIdlReaderRegistry.IdlReadError.Unreadable).causes.size
    }

    test("readIdlOrNull returns null for unreadable and invalid input") {
        SolanaIdlReaderRegistry.readIdlOrNull("not an idl").shouldBeNull()
        SolanaIdlReaderRegistry.readIdlOrNull(ANCHOR.replace("[1]", "[]")).shouldBeNull()
        SolanaIdlReaderRegistry.readIdlOrNull(ANCHOR)!!.name shouldBe "anchor"
    }

    test("reads from URLs and streams") {
        val file = File.createTempFile("idl", ".json").apply {
            deleteOnExit()
            writeText(ANCHOR)
        }
        SolanaIdlReaderRegistry.tryReadIdl(file.toURI().toURL()).unwrap().name shouldBe "anchor"
        SolanaIdlReaderRegistry.tryReadIdl(file.inputStream()).unwrap().name shouldBe "anchor"
        AnchorIdlReader.read(file.toURI().toURL()).name shouldBe "anchor"
    }
})
