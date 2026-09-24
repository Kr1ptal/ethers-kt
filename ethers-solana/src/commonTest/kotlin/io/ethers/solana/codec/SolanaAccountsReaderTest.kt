package io.ethers.solana.codec

import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBytes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val PROGRAM = SolanaAddress("11111111111111111111111111111112")
private val A = SolanaAddress(ByteArray(32) { 1 })
private val B = SolanaAddress(ByteArray(32) { 2 })
private val C = SolanaAddress(ByteArray(32) { 3 })

private fun instruction(vararg keys: AccountMeta) = Instruction(PROGRAM, keys.toList(), SolanaBytes.EMPTY)

class SolanaAccountsReaderTest : FunSpec({
    test("assigns accounts to roles in order and keeps the rest as remaining") {
        val decoded = SolanaAccountsReader.decode(instruction(AccountMeta.signerAndWritable(A), AccountMeta(B), AccountMeta.writable(C))) { reader ->
            Triple(reader.required("payer", signer = true, writable = true), reader.required("mint", signer = false, writable = false), reader.remaining())
        }.unwrap()
        decoded shouldBe Triple(A, B, listOf(AccountMeta.writable(C)))
    }

    test("elevated privileges are accepted") {
        SolanaAccountsReader.decode(instruction(AccountMeta.signerAndWritable(A))) { it.required("readonly", signer = false, writable = false) }
            .unwrap() shouldBe A
    }

    test("missing privileges and missing accounts are invalid") {
        val metas = listOf(AccountMeta(A))
        val signer = SolanaAccountsReader.decode(instruction(*metas.toTypedArray())) { it.required("authority", signer = true, writable = false) }
            .unwrapError().shouldBeInstanceOf<SolanaDecodeError.InvalidAccounts>()
        signer.message shouldBe "Account 'authority' at position 0 must be a signer"
        signer.accounts shouldBe metas

        SolanaAccountsReader.decode(instruction(AccountMeta(A))) { it.required("vault", signer = false, writable = true) }
            .unwrapError().shouldBeInstanceOf<SolanaDecodeError.InvalidAccounts>().message shouldBe "Account 'vault' at position 0 must be writable"

        SolanaAccountsReader.decode(instruction(AccountMeta(A))) {
            it.required("a", signer = false, writable = false)
            it.required("b", signer = false, writable = false)
        }.unwrapError().shouldBeInstanceOf<SolanaDecodeError.InvalidAccounts>().message shouldBe "Missing account 'b' at position 1"
    }

    test("an optional account passed as the program id is absent, and its privileges are not checked") {
        val (absent, present) = SolanaAccountsReader.decode(instruction(AccountMeta(PROGRAM), AccountMeta.signer(B))) {
            it.optional("first", signer = true, writable = true) to it.optional("second", signer = true, writable = false)
        }.unwrap()
        absent.shouldBeNull()
        present shouldBe B
    }
})
