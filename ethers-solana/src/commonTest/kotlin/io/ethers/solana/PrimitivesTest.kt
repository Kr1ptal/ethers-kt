package io.ethers.solana

import io.ethers.core.FastHex
import io.ethers.core.Kotlinx
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.U64Serializer
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
        signer.publicKey.verify(SolanaSignature(malleable), byteArrayOf()) shouldBe false
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
        val key = SolanaAddress(source)
        source[0] = 1
        key.toString() shouldBe "11111111111111111111111111111111"
        key.toByteArray().fill(2)
        key shouldBe Programs.SYSTEM
        SolanaAddress(key.toString()) shouldBe key
        shouldThrow<IllegalArgumentException> { SolanaAddress(ByteArray(31)) }
        shouldThrow<IllegalArgumentException> { SolanaSignature(ByteArray(32)) }
        shouldThrow<IllegalArgumentException> { SolanaBlockhash(ByteArray(64)) }
        shouldThrow<IllegalArgumentException> { SolanaAddress("0") }
    }

    test("upstream associated token address vector and PDA validation") {
        val owner = SolanaAddress("CYLdTZhP8d1GDGeeNapgPdUcPiux1U9B26315x38TtbQ")
        val pda = SolanaAddress.findAssociatedTokenAddress(owner, owner)
        pda.address.toString() shouldBe "3W9cYxjkWXUPAsfGJ1GNdFiZsGEwcoopwMz4S8eAkkXd"
        pda.bump shouldBe 254
        pda.address.isOnCurve() shouldBe false
        SolanaAddress.createProgramAddress(listOf(owner.toByteArray(), Programs.TOKEN.toByteArray(), owner.toByteArray(), byteArrayOf(pda.bump.toByte())), Programs.ASSOCIATED_TOKEN) shouldBe pda.address
        shouldThrow<IllegalArgumentException> { SolanaAddress.findProgramAddress(listOf(ByteArray(33)), Programs.SYSTEM) }
        shouldThrow<IllegalArgumentException> { SolanaAddress.findProgramAddress(List(16) { byteArrayOf() }, Programs.SYSTEM) }
        // Empty seeds and explicit bump zero are legal inputs, irrespective of whether a given hash is off-curve.
        val zero = (0..255).first { n -> runCatching { SolanaAddress.createProgramAddress(listOf(byteArrayOf(n.toByte()), byteArrayOf(0)), Programs.SYSTEM) }.isSuccess }
        SolanaAddress.createProgramAddress(listOf(byteArrayOf(zero.toByte()), byteArrayOf(0)), Programs.SYSTEM).isOnCurve() shouldBe false
    }

    test("SolanaAddress serializer preserves base58 addresses including off-curve PDAs") {
        val owner = KeypairSigner.fromSeed(ByteArray(32)).publicKey
        val pda = SolanaAddress.findAssociatedTokenAddress(owner, Programs.TOKEN).address
        pda.isOnCurve() shouldBe false
        val serializer = SolanaAddress.serializer()
        serializer.descriptor.serialName shouldBe "io.ethers.solana.SolanaAddress"
        for (address in listOf(Programs.SYSTEM, owner, pda)) {
            val encoded = "\"${address.toBase58()}\""
            Kotlinx.DEFAULT.encodeToString(serializer, address) shouldBe encoded
            val decoded = Kotlinx.DEFAULT.decodeFromString(serializer, encoded)
            decoded shouldBe address
            decoded.hashCode() shouldBe address.hashCode()
            decoded.toByteArray() shouldBe address.toByteArray()
        }
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromString(serializer, "\"0\"") }
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromString(serializer, "\"111\"") }
    }

    test("u64 JSON and unit conversion preserve precision") {
        val max = BigInteger("18446744073709551615")
        Kotlinx.DEFAULT.encodeToString(U64Serializer, max) shouldBe "18446744073709551615"
        Kotlinx.DEFAULT.decodeFromString(U64Serializer, "18446744073709551615") shouldBe max
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromString(U64Serializer, "18446744073709551616") }
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromString(U64Serializer, "\"42\"") }
        SolUnit.SOL.toLamports("1.000000001").toBigIntegerExact() shouldBe bigIntegerOf(1000000001)
        SolUnit.LAMPORT.toSol(bigIntegerOf(1)).toPlainString() shouldBe "0.000000001"
        shouldThrow<ArithmeticException> { SolUnit.SOL.toLamports("0.0000000001").toBigIntegerExact() }
    }
})
