package io.ethers.signers

import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Signature
import io.ethers.core.types.transaction.TxDynamicFee
import io.ethers.core.types.transaction.TxLegacy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class PrivateKeySignerTest : FunSpec({
    val privateKeysToAddress = mapOf(
        "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef" to Address("0x1be31a94361a391bbafb2a4ccd704f57dc04d4bb"),
        "0x348ce564d427a3311b6536bbcff9390d69395b06ed6c486954e971d960fe8709" to Address("0xb8ce9ab6943e0eced004cde8e3bbed6568b2fa01"),
        "0xd7325de5c2c1cf0009fac77d3d04a9c004b038883446b065871bc3e831dcd098" to Address("0xf2cd2aa0c7926743b1d4310b2bc984a0a453c3d4"),
        "0xcc505ee6067fba3f6fc2050643379e190e087aeffe5d958ab9f2f3ed3800fa4e" to Address("0xe78150facd36e8eb00291e251424a0515aa1ff05"),
    )

    test("signer has correct address") {
        privateKeysToAddress.forAll { (pk, address) ->
            PrivateKeySigner(pk).address shouldBe address
        }
    }

    test("throws on invalid private key") {
        listOf(
            // too short
            "0x1234567890abcdef1234567890abcdef1234567890abcdef1234",
            // too long
            "0x1234567890abcdef1234567890abcdef1234567890abcdef12341234567890abcdef1234567890abcdef1234567890abcdef1234",
            // invalid hex characters
            "0x1234567890abcdef1234567890abcdef1234567890abcdef1234abcdefghijkl",
            // can't be zero
            "0x0000000000000000000000000000000000000000000000000000000000000000",
            // can't be more than the order of the curve
            "0xfffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141",
        ).forAll { pk ->
            shouldThrow<IllegalArgumentException> { PrivateKeySigner(pk) }
        }
    }

    val validSigners = privateKeysToAddress.keys.map { PrivateKeySigner(it) }
    test("recover/verify from message") {
        Arb.byteArray(Arb.int(0..512), Arb.byte()).checkAll { message ->
            Exhaustive.of(*validSigners.toTypedArray()).checkAll { signer ->
                val signature = signer.signMessage(message)
                val recoveredAddress = signature.recoverFromMessage(message)

                recoveredAddress shouldBe signer.address
                signature.verifyFromMessage(message, signer.address) shouldBe true
            }
        }
    }

    val privateKeyToTxSignatureEIP155 = mapOf(
        "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef" to Signature(
            "79440387067578648643008012733832802856903723450045762070555193125330762627918".toBigInteger(),
            "24830859692978839778370969737964141525080645226171071268096642880992234125891".toBigInteger(),
            38,
        ),
        "0x348ce564d427a3311b6536bbcff9390d69395b06ed6c486954e971d960fe8709" to Signature(
            "113413585705199877282342106131083611984559557475244316040986828406111415989395".toBigInteger(),
            "22252976016595162558772243886012941606497586198951370546379360980634134931625".toBigInteger(),
            37,
        ),
        "0xd7325de5c2c1cf0009fac77d3d04a9c004b038883446b065871bc3e831dcd098" to Signature(
            "39881024817859232543909063235816605494926416229725845016109190040356436610286".toBigInteger(),
            "38401138531250945520112501717491307406842312302616027945673494271746105812134".toBigInteger(),
            38,
        ),
        "0xcc505ee6067fba3f6fc2050643379e190e087aeffe5d958ab9f2f3ed3800fa4e" to Signature(
            "18115692211953142883783221366877787976369268266109215834410438670659171604048".toBigInteger(),
            "37858764062283213775724377694806707185297766006428243445355665105141775885598".toBigInteger(),
            37,
        ),
    )

    test("correctly applies EIP155 replay protection") {
        val tx = TxLegacy(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 0,
            gas = 2000000,
            gasPrice = "21000000000".toBigInteger(),
            data = null,
            chainId = 1L,
        )

        privateKeyToTxSignatureEIP155.forAll { (pk, expectedSignature) ->
            val signer = PrivateKeySigner(pk)
            val signedTx = tx.sign(signer)

            signedTx.tx shouldBe tx
            signedTx.signature shouldBe expectedSignature
            signedTx.from shouldBe signer.address
            signedTx.signature.recoverFromHash(tx.signatureHash()) shouldBe signedTx.from
        }
    }

    test("applies V electrum offset to legacy tx without chain ID") {
        val tx = TxLegacy(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 0,
            gas = 2000000,
            gasPrice = "21000000000".toBigInteger(),
            data = null,
            chainId = -1L,
        )
        val expectedSignature = Signature(
            "34938453632977747480852205638216815423113835132734453790662953807667546091950".toBigInteger(),
            "28687792139993720944066466016490066427727379935369029872950002778088619358223".toBigInteger(),
            28L,
        )

        val signer = PrivateKeySigner("0xcc505ee6067fba3f6fc2050643379e190e087aeffe5d958ab9f2f3ed3800fa4e")
        val signedTx = tx.sign(signer)

        signedTx.tx shouldBe tx
        signedTx.signature shouldBe expectedSignature
        signedTx.from shouldBe signer.address
        signedTx.signature.recoverFromHash(tx.signatureHash()) shouldBe signedTx.from
    }

    test("does not apply EIP155 replay protection to non-legacy tx") {
        val tx = TxDynamicFee(
            to = Address("0xF0109fC8DF283027b6285cc889F5aA624EaC1F55"),
            value = "1000000000".toBigInteger(),
            nonce = 12425132,
            gas = 2000000,
            gasFeeCap = "210000000000".toBigInteger(),
            gasTipCap = "21000000000".toBigInteger(),
            data = Bytes("0x1214abcdef12445980"),
            chainId = 1L,
            accessList = null,
        )

        val expectedSignature = Signature(
            "16573383210353771677492454383955932288670496229556868839865598480202955741615".toBigInteger(),
            "10750233622853357006922125749365624889215536362658281202655991441151548708921".toBigInteger(),
            0L,
        )

        val signer = PrivateKeySigner("0xcc505ee6067fba3f6fc2050643379e190e087aeffe5d958ab9f2f3ed3800fa4e")
        val signedTx = tx.sign(signer)

        signedTx.tx shouldBe tx
        signedTx.signature shouldBe expectedSignature
        signedTx.from shouldBe signer.address
        signedTx.signature.recoverFromHash(tx.signatureHash()) shouldBe signedTx.from
    }
})
