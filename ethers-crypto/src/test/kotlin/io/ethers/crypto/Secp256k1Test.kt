package io.ethers.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bigInt
import io.kotest.property.checkAll
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve
import java.math.BigInteger

class Secp256k1Test : FunSpec({
    context("publicKeyToAddress") {
        test("non-uncompressed public key fails") {
            val dummyNonUncompressedKey = ByteArray(1)
            shouldThrow<IllegalArgumentException> {
                Secp256k1.publicKeyToAddress(dummyNonUncompressedKey)
            }
        }

        test("uncompressed public key") {
            val uncompressedPublicKeyToAddress = mapOf(
                "0405c4a32827fe615e55af5fbcc0132af3dae35d905c0ef7b74b8450acf5994e67918b372275381c01d013f4afd3e8dda004e8e71cb14b36c18488644c78c73f96" to "2e15ac33d935d45fcedee087844d425597f5b4ee",
                "04168b25bef99a1c395ebbd9d108ac250bf6d312a360952eaaf359ced02ac0921dea650566399d0fcacc77a3f79282636c5228de068256c042a3f316f18acb48a4" to "84d723e215d77ee3a316125a9300d2ae23b67c73",
                "0447d906be81a2ae24a9665c2ae4cb44d9d501b670e245589e6e4444a4d71304b76cf63025bb3882e95177460cc9ba65715fcaa648ad73b11f3310b00b9252780b" to "59f219d4ec4cefeab34dc91a5c40ebd76a2c861e",
                "047de9d22f403fdff5b87bf9999819cc7d2179b8b29414a45d69ea093e7bba51c1bfed92a5eda8b88511d58e311c062d837326856b60b31f2f9816d6dacee5677c" to "96acaa37759bb8c47a9e6be61aaffc0dafba2c7d",
                "04c7b9430d79e64d829bd88153f9c8fa8a5946774152dacc74ef63eaed6cdc014f5de7acb6dff74c97aded61ff2054dbb7450cfe3fe937f78a0b5b87dcad468dfa" to "81a28a299419c7d49cf013a72ce73ee944f7ecc5",
                "04f179103948210b8e95d5beb0b904b5e39184c6da4d83aac485765b44b954d515d01eef43308c600c2040bf9f83efe316cc8f16d72c6a453756283a9af4f87c68" to "cff0b49e6507749490c69dc85288c9cb0436b38b",
            )

            uncompressedPublicKeyToAddress.forAll { (publicKey, address) ->
                Secp256k1.publicKeyToAddress(publicKey.hexToByteArray()) shouldBe address.hexToByteArray()
            }
        }
    }

    context("recoverPublicKey") {
        test("invalid 'r' parameter") {
            shouldThrow<IllegalArgumentException> {
                Secp256k1.recoverPublicKey(ByteArray(0), BigInteger("-1"), BigInteger.ZERO, 0)
            }
        }

        test("invalid 's' parameter") {
            shouldThrow<IllegalArgumentException> {
                Secp256k1.recoverPublicKey(ByteArray(0), BigInteger.ZERO, BigInteger("-1"), 0)
            }
        }

        test("invalid 'recId' parameter") {
            shouldThrow<IllegalArgumentException> {
                Secp256k1.recoverPublicKey(ByteArray(0), BigInteger.ZERO, BigInteger.ZERO, -1)
            }
        }

        test("x-coordinate overflow") {
            val recoveredPublicKey = Secp256k1.recoverPublicKey(ByteArray(0), SecP256K1Curve.q, BigInteger.ZERO, 0)
            recoveredPublicKey shouldBe null
        }

        test("recover successful") {
            val messages = listOf(
                "Hello World!",
                "Goodbye Jupiter!",

                // a test that exercises key recovery with findRecoveryId() on a test vector
                // from https://crypto.stackexchange.com/a/41339
                "Maarten Bodewes generated this test vector on 2016-11-08",
            )

            messages.forAll { message ->
                Arb.bigInt(0, 256).checkAll {
                    if (it == BigInteger.ZERO) {
                        return@checkAll
                    }

                    val messageHash = Hashing.hashMessage(message.toByteArray())
                    val signingKey = Secp256k1.SigningKey(it)
                    val signature = signingKey.signHash(messageHash)

                    val recoveredPublicKey = Secp256k1.recoverPublicKey(messageHash, signature[0], signature[1], signature[2].toLong())

                    recoveredPublicKey shouldNotBe null
                    recoveredPublicKey!! shouldBe signingKey.publicKey
                }
            }
        }
    }

    test("SigningKey.signHash") {
        val signingKey = Secp256k1.SigningKey(BigInteger("ebb2c082fd7727890a28ac82f6bdf97bad8de9f5d7c9028692de1a255cad3e0f", 16))
        val messageToSignature = mapOf(
            "Hello World!" to listOf(BigInteger("84572906993412228422871642307501242289993000969311520014597794884839393205535"), BigInteger("26244937144775656547977617151742385694940367005398956818746682468573787954744"), BigInteger.ONE),
            "Goodbye Jupiter!" to listOf(BigInteger("12214342688069944347190031536677236041083812119384431199873468508255367379356"), BigInteger("28646747756097151676398005985657445829994264100612970196164466070463142098670"), BigInteger.ONE),
            "Maarten Bodewes generated this test vector on 2016-11-08" to listOf(BigInteger("20626276573946388582327645212475513911425089904966284059462614374701078664109"), BigInteger("11741197901064219810343800021622017182168173453043216148594072691886809006848"), BigInteger.ZERO),
        )

        messageToSignature.forAll { (message, signature) ->
            val messageHash = Hashing.hashMessage(message.toByteArray())
            signingKey.signHash(messageHash) shouldBe signature
        }
    }
})
