package io.ethers.abi.error

import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abi.StructFactory
import io.ethers.core.isFailure
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

class CustomErrorTest : FunSpec({
    CustomErrorRegistry.prependResolver(MockCustomErrorResolver())

    test("decode complex custom error correctly") {
        val error = ErrorWithStruct(
            bigIntegerOf(123),
            ErrorMsg(
                "hello",
                bigIntegerOf(456),
                listOf(true, false, true),
            ),
        )

        val encoded = ErrorWithStruct.abi.encodeCall(listOf(error.arg0, error.msg))
        val decoded = ContractError.getOrNull(encoded)
        decoded shouldBe error
    }

    test("decode custom error with no args correctly") {
        val error = NoArgsError.INSTANCE

        val encoded = NoArgsError.abi.encodeCall(emptyList())
        val decoded = ContractError.getOrNull(encoded)
        decoded shouldBe error
    }

    test("decode simple custom error correctly") {
        val error = InvalidFlashswapFlags(bigIntegerOf(123), "wrong flags")

        val encoded = InvalidFlashswapFlags.abi.encodeCall(listOf(error.flag, error.name))
        val decoded = ContractError.getOrNull(encoded)
        decoded shouldBe error
    }

    test("decoding unknown custom error returns null") {
        listOf(
            // too short
            Bytes("0x3192"),
            // wrong selector
            Bytes("0x31920d0e0000000000000000000000000000000000000000000000000000000000000001"),
        ).forAll {
            CustomErrorRegistry.getOrNull(it) shouldBe null
        }
    }

    test("decoding malformed matching custom error returns null or failure") {
        val data = ErrorWithStruct.abi.selector

        ErrorWithStruct.decodeOrNull(data) shouldBe null
        ErrorWithStruct.decodeOrNull(data) shouldBe null

        val decoded = ErrorWithStruct.tryDecode(data)
        decoded.isFailure() shouldBe true
        decoded.unwrapError().shouldBeInstanceOf<ContractErrorDecodingError.MalformedError>()
    }

    test("decoding malformed matching revert error returns null or failure") {
        val data = RevertError.FUNCTION.selector

        RevertError.getOrNull(data) shouldBe null
        ContractError.getOrNull(data) shouldBe null

        val decoded = ContractError.tryGet(data)
        decoded.isFailure() shouldBe true
        decoded.unwrapError().shouldBeInstanceOf<ContractErrorDecodingError.MalformedError>()
    }

    test("NoMatchingError names every candidate that was tried") {
        // unknown selector, so nothing matches and all candidates get reported
        val data = Bytes("0x31920d0e0000000000000000000000000000000000000000000000000000000000000001")

        val error = ContractError.tryGet(data)
            .unwrapError()
            .shouldBeInstanceOf<ContractErrorDecodingError.NoMatchingError>()

        error.expectedErrors shouldContain PanicError.FUNCTION
        error.expectedErrors shouldContain RevertError.FUNCTION
    }

    test("resolve keeps searching when a selector-colliding factory fails to decode") {
        // both factories have the same signature, so they share a selector. The first one always fails to decode,
        // and must not prevent the second one from resolving the error.
        CustomErrorFactoryResolver.addFactories(listOf(FailingCollidingErrorFactory, CollidingError))

        val encoded = CollidingError.abi.encodeCall(listOf(bigIntegerOf(7)))

        CustomErrorFactoryResolver.resolve(encoded) shouldBe CollidingError(bigIntegerOf(7))

        // the Result-based path reports the first malformed match instead, since it can tell the two apart
        CustomErrorFactoryResolver.tryResolve(encoded)
            .unwrapError()
            .shouldBeInstanceOf<ContractErrorDecodingError.MalformedError>()
    }
}) {
    private data class CollidingError(val flag: BigInteger) : CustomContractError() {
        companion object : CustomErrorFactory<CollidingError> {
            @JvmStatic
            override val abi: AbiFunction = AbiFunction("CollidingError", listOf(AbiType.UInt(256)), emptyList())

            @JvmStatic
            override fun decode(data: List<Any>): CollidingError = CollidingError(data[0] as BigInteger)
        }
    }

    /** Same signature as [CollidingError], so it matches the same selector, but never decodes successfully. */
    private object FailingCollidingErrorFactory : CustomErrorFactory<CollidingError> {
        override val abi: AbiFunction = AbiFunction("CollidingError", listOf(AbiType.UInt(256)), emptyList())

        override fun decode(data: List<Any>): CollidingError = throw IllegalStateException("cannot decode")
    }

    private class MockCustomErrorResolver : CustomErrorResolver {
        override fun resolve(error: Bytes): CustomContractError? {
            val err1 = ErrorWithStruct.decodeOrNull(error)
            if (err1 != null) {
                return err1
            }

            val err2 = NoArgsError.decodeOrNull(error)
            if (err2 != null) {
                return err2
            }

            val err3 = InvalidFlashswapFlags.decodeOrNull(error)
            if (err3 != null) {
                return err3
            }

            return null
        }
    }

    private data class ErrorWithStruct(
        val arg0: BigInteger,
        val msg: ErrorMsg,
    ) : CustomContractError() {
        companion object : CustomErrorFactory<ErrorWithStruct> {
            @JvmStatic
            override val abi: AbiFunction = AbiFunction(
                "ErrorWithStruct",
                listOf(
                    AbiType.UInt(256),
                    ErrorMsg.abi,
                ),
                emptyList(),
            )

            @JvmStatic
            override fun decode(data: List<Any>): ErrorWithStruct = ErrorWithStruct(
                data[0] as BigInteger,
                data[1] as ErrorMsg,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal data class ErrorMsg(
        val msg: String,
        val `value`: BigInteger,
        val flags: List<Boolean>,
    ) : ContractStruct {
        override val tuple: List<Any> = listOf(msg, value, flags)

        override val abiType: AbiType.Struct<*>
            get() = abi

        companion object : StructFactory<ErrorMsg> {
            @JvmStatic
            override val abi: AbiType.Struct<ErrorMsg> = AbiType.Struct(
                ErrorMsg::class,
                ::fromTuple,
                AbiType.Struct.Field("msg", AbiType.String),
                AbiType.Struct.Field("value", AbiType.UInt(256)),
                AbiType.Struct.Field("flags", AbiType.Array(AbiType.Bool)),
            )

            @JvmStatic
            override fun fromTuple(data: List<Any>): ErrorMsg = ErrorMsg(
                data[0] as String,
                data[1] as BigInteger,
                data[2] as List<Boolean>,
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ErrorMsg

            if (msg != other.msg) return false
            if (`value` != other.`value`) return false
            if (flags != other.flags) return false

            return true
        }

        override fun hashCode(): Int {
            var result = msg.hashCode()
            result = 31 * result + `value`.hashCode()
            result = 31 * result + flags.hashCode()
            return result
        }
    }

    private class NoArgsError : CustomContractError() {
        override fun toString(): String = "NoArgsError"

        companion object : CustomErrorFactory<NoArgsError> {
            @JvmStatic
            override val abi: AbiFunction = AbiFunction("NoArgsError", listOf(), emptyList())

            @JvmField
            val INSTANCE: NoArgsError = NoArgsError()

            @JvmStatic
            override fun decode(data: List<Any>): NoArgsError = INSTANCE
        }
    }

    internal data class InvalidFlashswapFlags(
        val flag: BigInteger,
        val name: String,
    ) : CustomContractError() {
        companion object : CustomErrorFactory<InvalidFlashswapFlags> {
            @JvmStatic
            override val abi: AbiFunction = AbiFunction(
                "InvalidFlashswapFlags",
                listOf(AbiType.UInt(256), AbiType.String),
                emptyList(),
            )

            @JvmStatic
            override fun decode(data: List<Any>): InvalidFlashswapFlags = InvalidFlashswapFlags(
                data[0] as BigInteger,
                data[1] as String,
            )
        }
    }
}
