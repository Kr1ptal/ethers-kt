package io.ethers.solana

import io.ethers.core.FastHex
import io.ethers.core.Kotlinx
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.Programs
import io.ethers.solana.types.PublicKey
import io.ethers.solana.types.Signature
import io.ethers.solana.types.U64Serializer
import io.ethers.solana.utils.BinaryReader
import io.ethers.solana.utils.BinaryWriter
import io.ethers.solana.utils.SolUnit
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PrimitivesTest : FunSpec({
    test("RFC 8032 Ed25519 vector 1 on every platform") {
        val seed = FastHex.decode("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val signer = KeypairSigner.fromSeed(seed)
        signer.publicKey.toByteArray() shouldBe FastHex.decode("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val signature = signer.signMessage(byteArrayOf())
        signature.toByteArray() shouldBe FastHex.decode("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
        signer.publicKey.verify(signature, byteArrayOf()) shouldBe true
        signer.publicKey.verify(signature, byteArrayOf(1)) shouldBe false
        val malleable = signature.toByteArray()
        val order = BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)
        val scalar = BigInteger(1, malleable.copyOfRange(32, 64).reversedArray()).add(order).toByteArray().reversedArray()
        scalar.copyInto(malleable, 32)
        signer.publicKey.verify(Signature(malleable), byteArrayOf()) shouldBe false
        KeypairSigner.fromSecretKey(signer.toSecretKey()).publicKey shouldBe signer.publicKey
        seed.fill(0)
        signer.signMessage(byteArrayOf()) shouldBe signature
        val exported = signer.toSecretKey()
        exported[63] = (exported[63].toInt() xor 1).toByte()
        shouldThrow<IllegalArgumentException> { KeypairSigner.fromSecretKey(exported) }
        shouldThrow<IllegalArgumentException> { KeypairSigner.fromSeed(ByteArray(31)) }
        shouldThrow<IllegalArgumentException> { KeypairSigner.fromSecretKey(ByteArray(32)) }
    }

    test("base58 types validate length and isolate byte arrays") {
        val source = ByteArray(32)
        val key = PublicKey(source)
        source[0] = 1
        key.toString() shouldBe "11111111111111111111111111111111"
        key.toByteArray().fill(2)
        key shouldBe Programs.SYSTEM
        PublicKey(key.toString()) shouldBe key
        shouldThrow<IllegalArgumentException> { PublicKey(ByteArray(31)) }
        shouldThrow<IllegalArgumentException> { Signature(ByteArray(32)) }
        shouldThrow<IllegalArgumentException> { Blockhash(ByteArray(64)) }
        shouldThrow<IllegalArgumentException> { PublicKey("0") }
    }

    test("upstream associated token address vector and PDA validation") {
        val owner = PublicKey("CYLdTZhP8d1GDGeeNapgPdUcPiux1U9B26315x38TtbQ")
        val pda = PublicKey.findAssociatedTokenAddress(owner, owner)
        pda.address.toString() shouldBe "3W9cYxjkWXUPAsfGJ1GNdFiZsGEwcoopwMz4S8eAkkXd"
        pda.bump shouldBe 254
        pda.address.isOnCurve() shouldBe false
        PublicKey.createProgramAddress(listOf(owner.toByteArray(), Programs.TOKEN.toByteArray(), owner.toByteArray(), byteArrayOf(pda.bump.toByte())), Programs.ASSOCIATED_TOKEN) shouldBe pda.address
        shouldThrow<IllegalArgumentException> { PublicKey.findProgramAddress(listOf(ByteArray(33)), Programs.SYSTEM) }
        shouldThrow<IllegalArgumentException> { PublicKey.findProgramAddress(List(16) { byteArrayOf() }, Programs.SYSTEM) }
        // Empty seeds and explicit bump zero are legal inputs, irrespective of whether a given hash is off-curve.
        val zero = (0..255).first { n -> runCatching { PublicKey.createProgramAddress(listOf(byteArrayOf(n.toByte()), byteArrayOf(0)), Programs.SYSTEM) }.isSuccess }
        PublicKey.createProgramAddress(listOf(byteArrayOf(zero.toByte()), byteArrayOf(0)), Programs.SYSTEM).isOnCurve() shouldBe false
    }

    test("u64 JSON and unit conversion preserve precision") {
        val max = BigInteger("18446744073709551615")
        Kotlinx.DEFAULT.encodeToString(U64Serializer, max) shouldBe "18446744073709551615"
        Kotlinx.DEFAULT.decodeFromString(U64Serializer, "18446744073709551615") shouldBe max
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromString(U64Serializer, "18446744073709551616") }
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromString(U64Serializer, "\"42\"") }
        SolUnit.solToLamports("1.000000001") shouldBe bigIntegerOf(1000000001)
        SolUnit.lamportsToSol(bigIntegerOf(1)).toPlainString() shouldBe "0.000000001"
        shouldThrow<ArithmeticException> { SolUnit.solToLamports("0.0000000001") }
    }

    test("shortvec boundaries and malformed encodings") {
        for (n in listOf(0, 1, 127, 128, 255, 16383, 16384, 65535)) {
            val reader = BinaryReader(BinaryWriter().length(n).toByteArray())
            reader.length() shouldBe n
            reader.requireDone()
        }
        for (bytes in listOf(byteArrayOf(), byteArrayOf(128.toByte()), byteArrayOf(128.toByte(), 0), byteArrayOf(255.toByte(), 255.toByte(), 4))) {
            shouldThrow<IllegalArgumentException> { BinaryReader(bytes).length() }
        }
    }
})
