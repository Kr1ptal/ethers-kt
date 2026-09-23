package io.ethers.solana

import io.ethers.solana.instruction.Ed25519Program
import io.ethers.solana.instruction.MemoProgram
import io.ethers.solana.instruction.Secp256k1Program
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import io.github.artificialpb.bignum.BigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AdditionalInstructionsTest : FunSpec({
    val base = SolanaAddress(ByteArray(32) { 1 })
    val owner = SolanaAddress(ByteArray(32) { 2 })
    val payer = SolanaAddress(ByteArray(32) { 3 })
    val seed = "é"
    val account = SolanaAddress.createWithSeed(base, seed, owner)
    val prefix = "01".repeat(32) + "0200000000000000c3a9"
    val ownerHex = "02".repeat(32)

    test("seed derivation matches independent SHA256 fixture and validates UTF-8 byte length and owner") {
        SolanaBytes.fromBytes(account.asByteArray()).toHex() shouldBe "36daf6fd3a22d28a98566f56572a7a706abd4f6e611cd65af89cda28e19f079c"
        SolanaAddress.createWithSeed(base, "é".repeat(16), owner)
        SolanaAddress.createWithSeed(base, "", owner)
        shouldThrow<IllegalArgumentException> { SolanaAddress.createWithSeed(base, "é".repeat(17), owner) }
        shouldThrow<IllegalArgumentException> { SolanaAddress.createWithSeed(base, "a", SolanaAddress(ByteArray(11) + "ProgramDerivedAddress".encodeToByteArray())) }
    }

    test("seeded creation encodes UTF-8 byte length and does not require the derived account to sign") {
        val ix = SystemProgram.createAccountWithSeed(payer, account, base, seed, 258L, 80, owner)
        ix.data.toHex() shouldBe "03000000" + prefix + "02010000000000005000000000000000" + ownerHex
        ix.keys shouldBe listOf(AccountMeta.signerAndWritable(payer), AccountMeta.writable(account), AccountMeta.signer(base))
        ix.programId shouldBe Programs.SYSTEM
        SystemProgram.createAccountWithSeed(base, account, base, seed, 0L, 0, owner).keys shouldBe listOf(AccountMeta.signerAndWritable(base), AccountMeta.writable(account))
    }

    test("seeded allocate assign and transfer match System instruction layouts") {
        val allocate = SystemProgram.allocateWithSeed(account, base, seed, 80, owner)
        allocate.data.toHex() shouldBe "09000000" + prefix + "5000000000000000" + ownerHex
        allocate.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta.signer(base))
        val assign = SystemProgram.assignWithSeed(account, base, seed, owner)
        assign.data.toHex() shouldBe "0a000000" + prefix + ownerHex
        assign.keys shouldBe allocate.keys
        val transfer = SystemProgram.transferWithSeed(account, base, seed, owner, payer, BigInteger("18446744073709551615"))
        transfer.data.toHex() shouldBe "0b000000ffffffffffffffff0200000000000000c3a9" + ownerHex
        transfer.keys shouldBe allocate.keys + AccountMeta.writable(payer)
        shouldThrow<IllegalArgumentException> { SystemProgram.transferWithSeed(account, base, seed, owner, payer, -1L) }
        shouldThrow<IllegalArgumentException> { SystemProgram.transferWithSeed(account, base, seed, owner, payer, BigInteger("18446744073709551616")) }
        shouldThrow<IllegalArgumentException> { SystemProgram.allocateWithSeed(account, base, seed, -1, owner) }
        shouldThrow<IllegalArgumentException> { SystemProgram.createAccountWithSeed(payer, account, base, seed, 0L, SystemProgram.MAX_PERMITTED_DATA_LENGTH + 1, owner) }
        shouldThrow<IllegalArgumentException> { SystemProgram.assignWithSeed(payer, base, seed, owner) }
    }

    test("nonce authorization and upgrade have exact tags and authority requirements") {
        val authorize = SystemProgram.authorizeNonceAccount(account, base, owner)
        authorize.data.toHex() shouldBe "07000000" + ownerHex
        authorize.keys shouldBe listOf(AccountMeta.writable(account), AccountMeta.signer(base))
        val upgrade = SystemProgram.upgradeNonceAccount(account)
        upgrade.data.toHex() shouldBe "0c000000"
        upgrade.keys shouldBe listOf(AccountMeta.writable(account))
    }

    test("memo preserves UTF-8 and optional required signers") {
        val ix = MemoProgram.memo("é", listOf(base, payer))
        ix.data.toHex() shouldBe "c3a9"
        ix.keys shouldBe listOf(AccountMeta.signer(base), AccountMeta.signer(payer))
        ix.programId.toString() shouldBe "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
        MemoProgram.memo("").data.asByteArray().size shouldBe 0
        MemoProgram.memo("hello").keys shouldBe emptyList()
    }

    test("Ed25519 embeds all data with current-instruction references") {
        val signature = SolanaSignature(ByteArray(64) { 3 })
        val ix = Ed25519Program.verify(base, byteArrayOf(4, 5), signature)
        ix.data.toHex() shouldBe "01003000ffff1000ffff70000200ffff" + "01".repeat(32) + "03".repeat(64) + "0405"
        ix.programId.toString() shouldBe "Ed25519SigVerify111111111111111111111111111"
        ix.keys shouldBe emptyList()
        Ed25519Program.verify(base, ByteArray(65535), signature).data.asByteArray().size shouldBe 65647
        shouldThrow<IllegalArgumentException> { Ed25519Program.verify(base, ByteArray(65536), signature) }
    }

    test("secp256k1 embeds explicit instruction index and recovery byte") {
        val ix = Secp256k1Program.verify(ByteArray(20) { 1 }, byteArrayOf(4, 5), ByteArray(64) { 3 }, 1, 2)
        ix.data.toHex() shouldBe "012000020c00026100020002" + "01".repeat(20) + "03".repeat(64) + "010405"
        ix.keys shouldBe emptyList()
        ix.programId.toString() shouldBe "KeccakSecp256k11111111111111111111111111111"
        for (index in listOf(-1, 256)) {
            shouldThrow<IllegalArgumentException> { Secp256k1Program.verify(ByteArray(20), ByteArray(0), ByteArray(64), 0, index) }
        }
        for (recovery in listOf(-1, 4, 27)) {
            shouldThrow<IllegalArgumentException> { Secp256k1Program.verify(ByteArray(20), ByteArray(0), ByteArray(64), recovery, 0) }
        }
        shouldThrow<IllegalArgumentException> { Secp256k1Program.verify(ByteArray(19), ByteArray(0), ByteArray(64), 0, 0) }
        shouldThrow<IllegalArgumentException> { Secp256k1Program.verify(ByteArray(20), ByteArray(0), ByteArray(65), 0, 0) }
        shouldThrow<IllegalArgumentException> { Secp256k1Program.verify(ByteArray(20), ByteArray(65536), ByteArray(64), 0, 0) }
        Secp256k1Program.verify(ByteArray(20), ByteArray(65535), ByteArray(64), 3, 255).data.asByteArray().size shouldBe 65632
    }

    test("secp256k1 public key conversion matches the private-key-one Ethereum address") {
        val key = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798" + "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8"
        val bytes = key.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val address = Secp256k1Program.publicKeyToEthAddress(bytes)
        SolanaBytes.fromBytes(address).toHex() shouldBe "7e5f4552091a69125d5dfcb7b8c2659029395bdf"
        Secp256k1Program.verifyWithPublicKey(bytes, ByteArray(0), ByteArray(64), 0, 0) shouldBe Secp256k1Program.verify(address, ByteArray(0), ByteArray(64), 0, 0)
        shouldThrow<IllegalArgumentException> { Secp256k1Program.publicKeyToEthAddress(ByteArray(65)) }
    }
})
