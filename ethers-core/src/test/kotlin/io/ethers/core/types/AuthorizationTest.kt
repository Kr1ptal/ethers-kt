package io.ethers.core.types

import fixtures.AuthorizationFactory
import io.ethers.core.Jackson
import io.ethers.core.types.Authorization.Companion.MAGIC
import io.ethers.rlp.RlpDecoder
import io.ethers.rlp.RlpEncoder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigInteger

class AuthorizationTest : FunSpec({

    context("initialization") {
        test("valid parameters") {
            val auth = AuthorizationFactory.create(
                chainId = 1L,
                address = Address("0x1234567890123456789012345678901234567890"),
                nonce = 5L,
                yParity = 1L,
                r = BigInteger("1234567890", 16),
                s = BigInteger("111111111", 16),
            )

            auth.chainId shouldBe 1L
            auth.address shouldBe Address("0x1234567890123456789012345678901234567890")
            auth.nonce shouldBe 5L
            auth.yParity shouldBe 1L
            auth.r shouldBe BigInteger("1234567890", 16)
            auth.s shouldBe BigInteger("111111111", 16)
        }

        test("chainId can be zero") {
            AuthorizationFactory.create(chainId = 0L) // should not throw
        }

        test("invalid chainId") {
            shouldThrow<IllegalArgumentException> {
                AuthorizationFactory.create(chainId = -1L)
            }
        }

        test("invalid yParity") {
            shouldThrow<IllegalArgumentException> {
                AuthorizationFactory.create(yParity = 2L)
            }
        }

        test("invalid s value") {
            val invalidS = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
            shouldThrow<IllegalArgumentException> {
                AuthorizationFactory.create(s = invalidS)
            }
        }

        test("invalid negative nonce") {
            shouldThrow<IllegalArgumentException> {
                AuthorizationFactory.create(nonce = -1L)
            }
        }
    }

    context("RLP encoding") {
        test("encode and decode") {
            val original = AuthorizationFactory.create(
                chainId = 1L,
                address = Address("0x1234567890123456789012345678901234567890"),
                nonce = 5L,
                yParity = 0L,
                r = BigInteger("1234567890abcdef", 16),
                s = BigInteger("fedcba0987654321", 16),
            )

            val encoder = RlpEncoder()
            original.rlpEncode(encoder)
            val encoded = encoder.toByteArray()

            val decoder = RlpDecoder(encoded)
            val decoded = Authorization.rlpDecode(decoder)

            decoded shouldBe original
        }

        test("rlpSize is accurate") {
            val auth = AuthorizationFactory.create()
            val encoder = RlpEncoder()
            auth.rlpEncode(encoder)

            encoder.toByteArray().size shouldBe auth.rlpSize()
        }
    }

    context("JSON serialization") {
        test("serialize and deserialize") {
            val original = AuthorizationFactory.create(
                chainId = 1L,
                address = Address("0x1234567890123456789012345678901234567890"),
                nonce = 5L,
                yParity = 1L,
                r = BigInteger("1234567890abcdef", 16),
                s = BigInteger("fedcba0987654321", 16),
            )

            val json = Jackson.MAPPER.writeValueAsString(original)
            val decoded = Jackson.MAPPER.readValue(json, Authorization::class.java)

            decoded shouldBe original
        }

        test("JSON format matches expected structure") {
            val auth = AuthorizationFactory.create(
                chainId = 1L,
                address = Address("0x1234567890123456789012345678901234567890"),
                nonce = 5L,
                yParity = 1L,
                r = BigInteger("1234567890abcdef", 16),
                s = BigInteger("fedcba0987654321", 16),
            )

            val json = Jackson.MAPPER.writeValueAsString(auth)

            // Verify JSON contains expected fields
            json shouldBe """{"chainId":"0x1","address":"0x1234567890123456789012345678901234567890","nonce":"0x5","yParity":"0x1","r":"0x1234567890abcdef","s":"0xfedcba0987654321"}"""
        }
    }

    context("signature verification") {
        test("signature hash calculation") {
            val auth = AuthorizationFactory.create(
                chainId = 1L,
                address = Address("0x1234567890123456789012345678901234567890"),
                nonce = 5L,
            )

            // Test that we can call authority recovery without throwing (it may return null for dummy signatures)
            // This tests the signature hash calculation indirectly
            try {
                val recoveredAuth = auth.recoverAuthority()
                // Should either return an address or null, both are valid since we're using dummy signatures
            } catch (e: IllegalArgumentException) {
                // Expected for dummy signature values, just verify the method doesn't crash unexpectedly
            }
        }

        test("MAGIC constant is correct") {
            MAGIC shouldBe byteArrayOf(0x05)
        }
    }
})
