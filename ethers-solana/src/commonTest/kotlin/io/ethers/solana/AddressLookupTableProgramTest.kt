package io.ethers.solana

import io.ethers.solana.instruction.AddressLookupTableProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AddressLookupTableProgramTest : FunSpec({
    val authority = SolanaAddress(ByteArray(32) { 1 })
    val payer = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val table = SolanaAddress(ByteArray(32) { 3 })
    val recipient = SolanaAddress(ByteArray(32) { 4 })

    test("create encodes a u32 tag, u64 slot and PDA bump and only requires the payer signature") {
        val created = AddressLookupTableProgram.createLookupTable(authority, payer.publicKey, 258L)
        val derived = AddressLookupTableProgram.deriveLookupTableAddress(authority, 258L)
        created.address shouldBe derived.address
        created.instruction.programId shouldBe Programs.ADDRESS_LOOKUP_TABLE
        val data = created.instruction.data.asByteArray()
        data.copyOfRange(0, 12) shouldBe byteArrayOf(0, 0, 0, 0, 2, 1, 0, 0, 0, 0, 0, 0)
        data.size shouldBe 13
        (data[12].toInt() and 255) shouldBe derived.bump
        SolanaAddress.createProgramAddress(
            listOf(authority.asByteArray(), byteArrayOf(2, 1, 0, 0, 0, 0, 0, 0), byteArrayOf(data[12])),
            SolanaAddress("AddressLookupTab1e1111111111111111111111111"),
        ) shouldBe created.address
        created.instruction.keys shouldBe listOf(AccountMeta.writable(created.address), AccountMeta(authority), AccountMeta.signerAndWritable(payer.publicKey), AccountMeta(Programs.SYSTEM))
        AddressLookupTableProgram.createLookupTable(authority, recipient, bigIntegerOf(258)).address shouldBe created.address
        val tx = SolanaTransactionRequest {
            feePayer(payer.publicKey)
            blockhash(SolanaBlockhash(ByteArray(32) { 5 }))
            instruction(created.instruction)
        }.compileLegacy().unwrap()
        tx.signers shouldBe listOf(payer.publicKey)
        tx.sign(payer).signatures.size shouldBe 1
    }

    test("lookup table slots support full u64 and reject out-of-range values") {
        val maximum = BigInteger("18446744073709551615")
        AddressLookupTableProgram.createLookupTable(authority, payer.publicKey, maximum).instruction.data.toHex().substring(8, 24) shouldBe "ffffffffffffffff"
        shouldThrow<IllegalArgumentException> { AddressLookupTableProgram.createLookupTable(authority, payer.publicKey, -1L) }
        shouldThrow<IllegalArgumentException> { AddressLookupTableProgram.deriveLookupTableAddress(authority, BigInteger("18446744073709551616")) }
    }

    test("extend uses an eight-byte vector length and optional funding accounts") {
        val addresses = listOf(authority, recipient)
        val unfunded = AddressLookupTableProgram.extendLookupTable(table, authority, addresses)
        unfunded.programId shouldBe Programs.ADDRESS_LOOKUP_TABLE
        unfunded.data.toHex() shouldBe "020000000200000000000000" + "01".repeat(32) + "04".repeat(32)
        unfunded.keys shouldBe listOf(AccountMeta.writable(table), AccountMeta.signer(authority))
        val funded = AddressLookupTableProgram.extendLookupTable(table, authority, addresses, payer.publicKey)
        funded.data shouldBe unfunded.data
        funded.keys shouldBe unfunded.keys + listOf(AccountMeta.signerAndWritable(payer.publicKey), AccountMeta(Programs.SYSTEM))
        shouldThrow<IllegalArgumentException> { AddressLookupTableProgram.extendLookupTable(table, authority, emptyList()) }
        shouldThrow<IllegalArgumentException> { AddressLookupTableProgram.extendLookupTable(table, authority, List(257) { recipient }) }
    }

    test("freeze deactivate and close use distinct tags and preserve authority and recipient flags") {
        val freeze = AddressLookupTableProgram.freezeLookupTable(table, authority)
        val deactivate = AddressLookupTableProgram.deactivateLookupTable(table, authority)
        val close = AddressLookupTableProgram.closeLookupTable(table, authority, recipient)
        freeze.data.toHex() shouldBe "01000000"
        deactivate.data.toHex() shouldBe "03000000"
        close.data.toHex() shouldBe "04000000"
        freeze.keys shouldBe listOf(AccountMeta.writable(table), AccountMeta.signer(authority))
        deactivate.keys shouldBe freeze.keys
        close.keys shouldBe freeze.keys + AccountMeta.writable(recipient)
        listOf(freeze, deactivate, close).all { it.programId == Programs.ADDRESS_LOOKUP_TABLE } shouldBe true
    }
})
