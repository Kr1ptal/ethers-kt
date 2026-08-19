package io.ethers.abigen

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.abi.error.CustomErrorRegistry
import io.ethers.abigen.gen.Errors
import io.ethers.abigen.gen.Events
import io.ethers.abigen.gen.Functions
import io.ethers.abigen.gen.Structs
import io.ethers.abigen.loaders.DefaultAbigenCustomErrorLoader
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.github.artificialpb.bignum.BigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Exercises contract wrappers generated into this module's `commonTest` source set by `generateContractWrappers`,
 * on every target the project supports.
 *
 * Compiling them is already most of the check: anything JVM-only the generator emits - a `java.math` type,
 * `javaClass` - fails `compileTestKotlinMacosArm64` and friends, while the rest of the suite, which compiles the
 * same ABIs in-memory through `AbigenCompiler`, would stay green. The assertions below add the second half, that
 * the generated code also *behaves* the same everywhere, in particular that the multiplatform `BigInteger` really
 * is carrying uint/int values on native rather than the JVM alias the generator used to hardcode.
 * */
class GeneratedContractsKmpTest : FunSpec({
    // above Long.MAX_VALUE, so every assertion goes through a real big-integer implementation
    val bigStatus = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639933")

    context("functions") {
        test("call arguments round-trip") {
            val args = listOf<Any>(bigStatus, "hello multiplatform")
            val calldata = Functions.FUNCTION_SIMPLE_ARGS.encodeCall(args)
            val decoded = Functions.FUNCTION_SIMPLE_ARGS.decodeCall(calldata)

            decoded shouldBe args
            decoded[0].shouldBeInstanceOf<BigInteger>()
        }

        test("response decodes into the multiplatform BigInteger") {
            val response = Bytes(AbiCodec.encode(listOf(AbiType.UInt(256)), listOf(bigStatus)))

            Functions.FUNCTION_NO_ARGS_RETURNS.decodeResponse(response) shouldBe listOf(bigStatus)
        }

        test("overloads keep distinct selectors") {
            Functions.FUNCTION_OVERLOADED.selector shouldNotBe Functions.FUNCTION_OVERLOADED_1.selector
        }
    }

    context("structs") {
        test("simple struct round-trips through its generated factory") {
            val simple = Structs.Simple(success = true, data = Bytes("0xdeadbeef"))
            val encoded = AbiCodec.encode(Structs.Simple.abi, simple)

            AbiCodec.decode(Structs.Simple.abi, encoded) shouldBe simple
        }

        test("nested dynamic struct round-trips") {
            val nested = Structs.Nested(
                desc = "nested",
                simple = Structs.Simple(success = false, data = Bytes("0x01")),
                complex = Structs.Complex(
                    status = listOf(listOf(listOf(bigStatus, BigInteger("1")))),
                    msg = listOf("a", "b", "c"),
                ),
            )
            val encoded = AbiCodec.encode(Structs.Nested.abi, nested)

            AbiCodec.decode(Structs.Nested.abi, encoded) shouldBe nested
        }

        test("struct exposes its tuple in declaration order") {
            Structs.Simple(success = true, data = Bytes("0xff")).tuple shouldBe listOf(true, Bytes("0xff"))
        }
    }

    context("events") {
        test("decodes a log into the generated event") {
            val log = logOf(
                topics = listOf(Events.NoIndexedArgsEvent.abi.topicId),
                data = Bytes(
                    AbiCodec.encode(
                        listOf(AbiType.UInt(256), AbiType.String),
                        listOf(bigStatus, "emitted"),
                    ),
                ),
            )

            Events.NoIndexedArgsEvent.decodeOrNull(log) shouldBe Events.NoIndexedArgsEvent(bigStatus, "emitted", log)
        }

        test("rejects a log with a foreign topic") {
            val log = logOf(topics = listOf(Events.OnlyIndexedArgsEvent.abi.topicId), data = Bytes.EMPTY)

            Events.NoIndexedArgsEvent.decodeOrNull(log) shouldBe null
        }
    }

    context("custom errors") {
        test("decodes revert data through the generated factory") {
            val revertData = Errors.SimpleArgsError.abi.encodeCall(listOf(bigStatus, "boom", true))

            Errors.SimpleArgsError.decodeOrNull(revertData) shouldBe
                Errors.SimpleArgsError(bigStatus, "boom", true)
        }

        test("generated loader registers errors with the global registry") {
            DefaultAbigenCustomErrorLoader.load()

            val revertData = Errors.SimpleArgsError.abi.encodeCall(listOf(bigStatus, "boom", true))

            // compared structurally: on JVM the rest of the suite registers a second copy of these same ABIs
            // (compiled by `AbigenCompiler` into `io.ethers.abigen.test`), and either copy may win the lookup
            CustomErrorRegistry.getOrNull(revertData).toString() shouldBe
                Errors.SimpleArgsError(bigStatus, "boom", true).toString()
        }

        // a fieldless error is a plain class, so abigen writes its equals/hashCode by hand - the only place in the
        // generated output that needs a class identity check, and the reason it cannot use `javaClass`
        test("fieldless error compares by class identity") {
            Errors.NoArgsError() shouldBe Errors.NoArgsError()
            Errors.NoArgsError().hashCode() shouldBe Errors.NoArgsError().hashCode()

            Errors.NoArgsError().equals(null) shouldBe false
            Errors.NoArgsError().equals(Errors.SimpleArgsError(bigStatus, "boom", true)) shouldBe false
        }
    }
})

private fun logOf(topics: List<Hash>, `data`: Bytes) = Log(
    address = Address("0x197e90f9fad81970ba7976f33cbd77088e5d7cf7"),
    topics = topics,
    data = data,
    blockHash = Hash("0x8bbd497a03cf0a0690bbb91b38afc539e8552da391cff2d5861abb28a24d3129"),
    blockNumber = 18293121,
    blockTimestamp = 1713693012,
    transactionHash = Hash("0x24ab1ac3496270b0f7719c23e32aa1bac92e15c8f00682a3dd00ebe88d89a9c8"),
    transactionIndex = 143,
    logIndex = 309,
)
