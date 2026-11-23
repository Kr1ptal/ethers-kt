package io.ethers.aa.webauthn

import io.ethers.core.FastHex
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WebAuthnTest : FunSpec({
    // Passkey assertions from a browser, published with ox's WebAuthn tests
    val publicKey = P256PublicKey(
        BigInteger(1, FastHex.decode("0x21e1cbfd809fcc978340e1d3442bf9893391628620c065ff9b519b537afceba0")),
        BigInteger(1, FastHex.decode("0xa5b31085b870a1de535866d0aec5df21bc6c98de71f356d96886e84510b8f374")),
    )
    val challenge = FastHex.decode("0xf631058a3ba1116acce12396fad0a125b5041c43f8e15723709f81aa8d5f4ccf")
    val authenticatorData = Bytes("0x49960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d97630500000000")

    fun signature(r: String, s: String) = P256Signature(BigInteger(1, FastHex.decode(r)), BigInteger(1, FastHex.decode(s)))

    val browserAssertion = WebAuthnAssertion(
        credentialId = Bytes.EMPTY,
        authenticatorData = authenticatorData,
        clientDataJSON = """{"type":"webauthn.get","challenge":"9jEFijuhEWrM4SOW-tChJbUEHEP44VcjcJ-Bqo1fTM8","origin":"http://localhost:5173","crossOrigin":false}""",
        challengeIndex = 23,
        typeIndex = 1,
        signature = signature(
            "0x16d6f4bd3231c71c5e58927b9cf2ee701df03b52e3db71efc03d1139122f854f",
            "0x67f32a4fcb17b07ab9b7755b61e999b99139074fc8e1aa6d33d25beccbb2fbd4",
        ),
    )

    context("browser assertions") {
        test("verify") {
            browserAssertion.verify(publicKey, challenge) shouldBe true
        }

        test("verify with extra client data keys") {
            browserAssertion.copy(
                clientDataJSON = """{"type":"webauthn.get","challenge":"9jEFijuhEWrM4SOW-tChJbUEHEP44VcjcJ-Bqo1fTM8","origin":"http://localhost:5173","crossOrigin":false,"other_keys_can_be_added_here":"do not compare clientDataJSON against a template. See https://goo.gl/yabPex"}""",
                signature = signature(
                    "0xcbe10bc71cbbc0552f58aa5b8742c954f10335943abb9cfd186ec4d066e44d22",
                    "0x3901d4c1c5e613cfbcd1ce32c9679d4daafda3c73e745017cad55ec2ffd0a895",
                ),
            ).verify(publicKey, challenge) shouldBe true
        }

        test("do not verify for another challenge, key, or signature") {
            browserAssertion.verify(publicKey, ByteArray(32)) shouldBe false
            browserAssertion.verify(P256PublicKey(publicKey.y, publicKey.x), challenge) shouldBe false
            browserAssertion.copy(signature = P256Signature(bigIntegerOf(1), bigIntegerOf(1))).verify(publicKey, challenge) shouldBe false
        }

        test("do not verify without user verification, unless not required") {
            val raw = authenticatorData.toByteArray()
            raw[32] = 0x01
            val unverified = browserAssertion.copy(authenticatorData = Bytes(raw))
            unverified.userPresent shouldBe true
            unverified.userVerified shouldBe false
            unverified.verify(publicKey, challenge) shouldBe false
            // the flags are signed, so the modified assertion fails the signature check either way
            unverified.verify(publicKey, challenge, requireUserVerification = false) shouldBe false
        }

        test("do not verify a registration") {
            browserAssertion.copy(clientDataJSON = browserAssertion.clientDataJSON.replace("webauthn.get", "webauthn.create"))
                .verify(publicKey, challenge) shouldBe false
        }
    }

    context("software authenticator") {
        val authenticator = SoftwareWebAuthnAuthenticator("https://example.com")

        test("produces assertions that verify") {
            val assertion = WebAuthn.sign(authenticator, "example.com", challenge)
            assertion.credentialId shouldBe authenticator.credentialId
            assertion.userVerified shouldBe true
            assertion.signature.isLowS shouldBe true
            assertion.clientDataJSON.substring(assertion.challengeIndex).startsWith("\"challenge\":") shouldBe true
            assertion.clientDataJSON.substring(assertion.typeIndex).startsWith("\"type\":") shouldBe true
            assertion.verify(authenticator.publicKey, challenge) shouldBe true
            assertion.verify(authenticator.publicKey, ByteArray(32)) shouldBe false
        }

        test("signs only with an allowed credential") {
            shouldThrow<IllegalArgumentException> {
                WebAuthn.sign(authenticator, "example.com", challenge, listOf(Bytes("0x01")))
            }
            WebAuthn.sign(authenticator, "example.com", challenge, listOf(authenticator.credentialId)).verify(authenticator.publicKey, challenge) shouldBe true
        }
    }

    context("P-256 signatures") {
        test("DER parsing") {
            // SEQUENCE { INTEGER 0x0080.. (33 bytes, sign padded), INTEGER 0x01 }
            val der = FastHex.decode("0x3026022100" + "80" + "00".repeat(31) + "020101")
            val parsed = P256Signature.fromDer(der)
            parsed.r shouldBe bigIntegerOf(1).shiftLeft(255)
            parsed.s shouldBe bigIntegerOf(1)
        }

        test("malformed DER is rejected") {
            shouldThrow<IllegalArgumentException> { P256Signature.fromDer(FastHex.decode("0x3006020101020101ff")) }
            shouldThrow<IllegalArgumentException> { P256Signature.fromDer(FastHex.decode("0x3106020101020101")) }
            shouldThrow<IllegalArgumentException> { P256Signature.fromDer(FastHex.decode("0x3006020100020101")) }
        }

        test("high-s signatures normalize to low-s, and both verify") {
            val high = P256Signature(browserAssertion.signature.r, P256.N.subtract(browserAssertion.signature.s))
            high.isLowS shouldBe false
            high.normalized() shouldBe browserAssertion.signature
            WebAuthn.verifySignature(publicKey, browserAssertion.signedMessage(), high) shouldBe true
        }

        test("raw encoding round trip") {
            P256Signature.fromBytes(browserAssertion.signature.toBytes().asByteArray()) shouldBe browserAssertion.signature
        }
    }

    context("P-256 public keys") {
        test("encodings round trip") {
            publicKey.toBytes().size shouldBe 64
            P256PublicKey.fromBytes(publicKey.toBytes().asByteArray()) shouldBe publicKey
            P256PublicKey.fromBytes(publicKey.toUncompressed()) shouldBe publicKey
        }

        test("SubjectPublicKeyInfo") {
            val spki = FastHex.decode("0x3059301306072a8648ce3d020106082a8648ce3d030107034200") + publicKey.toUncompressed()
            P256PublicKey.fromSpki(spki) shouldBe publicKey
            shouldThrow<IllegalArgumentException> { P256PublicKey.fromSpki(spki.copyOf(spki.size - 1)) }
        }

        test("invalid encodings are rejected") {
            shouldThrow<IllegalArgumentException> { P256PublicKey.fromBytes(ByteArray(33)) }
            shouldThrow<IllegalArgumentException> { P256PublicKey.fromBytes(ByteArray(65) { 0x02 }) }
            shouldThrow<IllegalArgumentException> { P256PublicKey(P256.P, bigIntegerOf(1)) }
        }
    }

    test("base64url without padding") {
        WebAuthn.base64Url(challenge) shouldBe "9jEFijuhEWrM4SOW-tChJbUEHEP44VcjcJ-Bqo1fTM8"
        WebAuthn.base64Url(byteArrayOf(0xfb.toByte())) shouldBe "-w"
    }
})
