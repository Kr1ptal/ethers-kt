package io.ethers.core.types

import io.ethers.core.isFailure
import io.ethers.core.isSuccess
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigInteger

class SignatureTest : FunSpec({
    context("invalid recoveryId") {
        val invalidSignature =
            Signature(
                v = Signature.V_EIP155_OFFSET - 1,
                r = BigInteger.ZERO,
                s = BigInteger.ZERO,
            )

        test("recoveryId() fails") {
            shouldThrow<IllegalStateException> {
                invalidSignature.recoveryId()
            }
        }

        test("recoverFromHash() fails") {
            invalidSignature.recoverFromHash(ByteArray(0)) shouldBe null
        }
    }

    context("toByteArray/fromByteArray") {
        withData(
            Signature(
                "10".toBigInteger(),
                "12441241".toBigInteger(),
                27L,
            ) to "000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000bdd6991b",
            Signature(
                "10".toBigInteger(),
                "12441241".toBigInteger(),
                28L,
            ) to "000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000bdd6991c",
            Signature(
                "18515461264373351373200002665853028612451056578545711640558177340181847433846".toBigInteger(),
                "46948507304638947509940763649030358759909902576025900602547168820602576006531".toBigInteger(),
                27L,
            ) to "28ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa63627667cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d831b",
            Signature(
                "46948507304638947509940763649030358759909902576025900602547168820602576006531".toBigInteger(),
                "18515461264373351373200002665853028612451056578545711640558177340181847433846".toBigInteger(),
                28L,
            ) to "67cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d8328ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa6362761c",
            Signature(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff".toBigInteger(16),
                "46948507304638947509940763649030358759909902576025900602547168820602576006531".toBigInteger(),
                28L,
            ) to "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff67cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d831c",
            Signature(
                "18515461264373351373200002665853028612451056578545711640558177340181847433846".toBigInteger(),
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff".toBigInteger(16),
                27L,
            ) to "28ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff1b",
        ) { (signature, expected) ->
            val bytes = signature.toByteArray()
            bytes.toHexString() shouldBe expected

            Signature.fromByteArray(bytes).unwrap() shouldBe signature
        }
    }

    context("fromHex should decode valid hex strings correctly") {
        withData(
            "000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000bdd6991b" to
                Signature(
                    "10".toBigInteger(),
                    "12441241".toBigInteger(),
                    27L,
                ),
            "000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000bdd6991c" to
                Signature(
                    "10".toBigInteger(),
                    "12441241".toBigInteger(),
                    28L,
                ),
        ) { (hex, expectedSignature) ->
            val result = Signature.fromHex(hex)
            result.isSuccess() shouldBe true
            result.unwrap() shouldBe expectedSignature
        }
    }

    context("fromHex should handle invalid formats and sizes correctly") {
        withData(
            "0x" + "1".repeat(64),
            "1".repeat(200),
        ) { hex ->
            val result = Signature.fromHex(hex)
            result.isFailure() shouldBe true
            result.unwrapOrNull()?.shouldBeInstanceOf<InvalidSignatureError>()
        }
    }
})
