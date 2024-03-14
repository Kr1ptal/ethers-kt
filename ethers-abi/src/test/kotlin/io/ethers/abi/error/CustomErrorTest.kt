package io.ethers.abi.error

import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.abi.StructFactory
import io.ethers.core.types.Bytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class CustomErrorTest : FunSpec({
    CustomErrorRegistry.prependResolver(MockCustomErrorResolver())

    test("decode complex custom error correctly") {
        val error = ErrorWithStruct(
            BigInteger.valueOf(123),
            ErrorMsg(
                "hello",
                BigInteger.valueOf(456),
                arrayOf(true, false, true),
            ),
        )

        val encoded = ErrorWithStruct.abi.encodeCall(arrayOf(error.arg0, error.msg))
        val decoded = ContractError.getOrNull(encoded)
        decoded shouldBe error
    }

    test("decode custom error with no args correctly") {
        val error = NoArgsError.INSTANCE

        val encoded = NoArgsError.abi.encodeCall(emptyArray())
        val decoded = ContractError.getOrNull(encoded)
        decoded shouldBe error
    }

    test("decode simple custom error correctly") {
        val error = InvalidFlashswapFlags(BigInteger.valueOf(123), "wrong flags")

        val encoded = InvalidFlashswapFlags.abi.encodeCall(arrayOf(error.flag, error.name))
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
}) {
    private class MockCustomErrorResolver : CustomErrorResolver {
        override fun resolve(error: Bytes): CustomContractError? {
            val err1 = ErrorWithStruct.decode(error)
            if (err1 != null) {
                return err1
            }

            val err2 = NoArgsError.decode(error)
            if (err2 != null) {
                return err2
            }

            val err3 = InvalidFlashswapFlags.decode(error)
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
                    AbiType.Tuple.struct(
                        ErrorMsg::class,
                        AbiType.String,
                        AbiType.UInt(256),
                        AbiType.Array(AbiType.Bool),
                    ),
                ),
                emptyList(),
            )

            @JvmStatic
            override fun decode(data: Array<Any>): ErrorWithStruct = ErrorWithStruct(
                data[0] as BigInteger,
                data[1] as ErrorMsg,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal data class ErrorMsg(
        val msg: String,
        val `value`: BigInteger,
        val flags: Array<Boolean>,
    ) : ContractStruct {
        override val tuple: Array<Any> = arrayOf(msg, value, flags)

        companion object : StructFactory<ErrorMsg> {
            @JvmStatic
            override fun fromTuple(`data`: Array<Any>): ErrorMsg = ErrorMsg(
                data[0] as String,
                data[1] as BigInteger,
                data[2] as Array<Boolean>,
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ErrorMsg

            if (msg != other.msg) return false
            if (`value` != other.`value`) return false
            if (!flags.contentEquals(other.flags)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = msg.hashCode()
            result = 31 * result + `value`.hashCode()
            result = 31 * result + flags.contentHashCode()
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
            override fun decode(data: Array<Any>): NoArgsError = INSTANCE
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
            override fun decode(data: Array<Any>): InvalidFlashswapFlags = InvalidFlashswapFlags(
                data[0] as BigInteger,
                data[1] as String,
            )
        }
    }
}
