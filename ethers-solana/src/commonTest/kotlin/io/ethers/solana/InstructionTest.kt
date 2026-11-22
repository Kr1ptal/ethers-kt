package io.ethers.solana

import io.ethers.core.Result
import io.ethers.core.isFailure
import io.ethers.core.success
import io.ethers.solana.codec.SolanaAccountsReader
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaInstructionDecoder
import io.ethers.solana.instruction.ComputeBudgetProgram
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.program.ExecutedInstruction
import io.ethers.solana.program.InstructionPosition
import io.ethers.solana.program.decodeInstructions
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.ComputeBudgetSettings
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

private val PAYER = SolanaAddress(ByteArray(32) { 1 })
private val VAULT = SolanaAddress(ByteArray(32) { 2 })
private val PROGRAM = SolanaAddress(ByteArray(32) { 3 })
private val BLOCKHASH = SolanaBlockhash(ByteArray(32) { 4 })

private fun withdrawKeys(vault: SolanaAddress) = listOf(AccountMeta.signerAndWritable(PAYER), AccountMeta.writable(vault))

private fun withdrawData(amount: Byte) = SolanaBytes.fromBytes(byteArrayOf(7, amount))

/** A typed instruction shaped like a generated IDL binding: wire fields computed from typed ones. */
private class Withdraw(val vault: SolanaAddress, val amount: Byte) : Instruction(PROGRAM, withdrawKeys(vault), withdrawData(amount)) {
    override fun toString(): String = "Withdraw(vault=$vault, amount=$amount)"
}

/** Data classes inherit the final equals/hashCode rather than generating their own. */
private data class WithdrawData(val vault: SolanaAddress, val amount: Byte) : Instruction(PROGRAM, withdrawKeys(vault), withdrawData(amount))

class InstructionTest : FunSpec({
    val typed = Withdraw(VAULT, 5)
    val plain = Instruction(PROGRAM, withdrawKeys(VAULT), withdrawData(5))

    test("a subclass and a plain instruction with the same wire content are equal, both ways") {
        (typed == plain) shouldBe true
        (plain == typed) shouldBe true
        typed.hashCode() shouldBe plain.hashCode()
        (typed == Withdraw(VAULT, 5)) shouldBe true

        typed shouldNotBe Withdraw(VAULT, 6)
        plain shouldNotBe Withdraw(VAULT, 6)
        typed shouldNotBe Instruction(PAYER, typed.keys, typed.data)
        typed shouldNotBe Instruction(PROGRAM, typed.keys.take(1), typed.data)
        (typed.equals(null)) shouldBe false
        (typed.equals("Withdraw")) shouldBe false
    }

    test("a data class subclass keeps wire-content equality symmetric") {
        val data = WithdrawData(VAULT, 5)
        (data == plain) shouldBe true
        (plain == data) shouldBe true
        (data == typed) shouldBe true
        (typed == data) shouldBe true
        data.hashCode() shouldBe plain.hashCode()
        data shouldNotBe WithdrawData(VAULT, 6)
        // toString is open, so the data class still generates its own
        data.toString() shouldBe "WithdrawData(vault=$VAULT, amount=5)"
    }

    test("the ByteArray constructor takes ownership and equals the SolanaBytes one") {
        val bytes = byteArrayOf(7, 5)
        val owned = Instruction(PROGRAM, withdrawKeys(VAULT), bytes)
        owned shouldBe plain
        owned.data.asByteArray() shouldBeSameInstanceAs bytes
    }

    test("toString describes the wire fields and is overridable") {
        plain.toString() shouldBe "Instruction(programId=$PROGRAM, keys=${plain.keys}, data=${plain.data})"
        typed.toString() shouldBe "Withdraw(vault=$VAULT, amount=5)"
    }

    test("copy returns a plain instruction with the given fields replaced") {
        val keys = listOf(AccountMeta.signer(PAYER))
        val copied = typed.copy(keys = keys)
        copied::class shouldBe Instruction::class
        copied.programId shouldBe PROGRAM
        copied.keys shouldBe keys
        copied.data shouldBe typed.data

        val same = typed.copy()
        same::class shouldBe Instruction::class
        same shouldBe typed
        typed.copy(programId = VAULT, data = SolanaBytes.EMPTY) shouldBe Instruction(VAULT, typed.keys, SolanaBytes.EMPTY)
    }

    test("a subclass compiles to the same bytes as the plain instruction on every path") {
        val other = SolanaAddress(ByteArray(32) { 9 })
        val table = AddressLookupTableAccount(SolanaAddress(ByteArray(32) { 8 }), listOf(VAULT, other))
        val typedList = listOf(typed, Withdraw(other, 1))
        val plainList = listOf(plain, Instruction(PROGRAM, withdrawKeys(other), withdrawData(1)))

        SolanaTxLegacy.compile(PAYER, BLOCKHASH, typed).unwrap().serializeMessage() shouldBe
            SolanaTxLegacy.compile(PAYER, BLOCKHASH, plain).unwrap().serializeMessage()
        SolanaTxLegacy.compile(PAYER, BLOCKHASH, typedList).unwrap().serializeMessage() shouldBe
            SolanaTxLegacy.compile(PAYER, BLOCKHASH, plainList).unwrap().serializeMessage()

        SolanaTxV0.compile(PAYER, BLOCKHASH, typed).unwrap().serializeMessage() shouldBe
            SolanaTxV0.compile(PAYER, BLOCKHASH, plain).unwrap().serializeMessage()
        val v0Typed = SolanaTxV0.compile(PAYER, BLOCKHASH, typedList, listOf(table)).unwrap()
        v0Typed.addressLookupTables.size shouldBe 1
        v0Typed.serializeMessage() shouldBe SolanaTxV0.compile(PAYER, BLOCKHASH, plainList, listOf(table)).unwrap().serializeMessage()

        val config = SolanaTransactionConfig(computeUnitLimit = 200_000)
        SolanaTxV1.compile(PAYER, BLOCKHASH, typed, config).unwrap().serializeMessage() shouldBe
            SolanaTxV1.compile(PAYER, BLOCKHASH, plain, config).unwrap().serializeMessage()
        SolanaTxV1.compile(PAYER, BLOCKHASH, typedList, config).unwrap().serializeMessage() shouldBe
            SolanaTxV1.compile(PAYER, BLOCKHASH, plainList, config).unwrap().serializeMessage()

        fun request(instructions: List<Instruction>) = SolanaTransactionRequest {
            feePayer(PAYER)
            blockhash(BLOCKHASH)
            computeUnitLimit(200_000)
            instructions(instructions)
        }
        request(typedList) shouldBe request(plainList)
        request(typedList).compileLegacy().unwrap().serializeMessage() shouldBe request(plainList).compileLegacy().unwrap().serializeMessage()
        request(typedList).compileV0(listOf(table)).unwrap().serializeMessage() shouldBe
            request(plainList).compileV0(listOf(table)).unwrap().serializeMessage()
        request(typedList).compileV1().unwrap().serializeMessage() shouldBe request(plainList).compileV1().unwrap().serializeMessage()
        request(typedList).compile().unwrap().serializeMessage() shouldBe request(plainList).compile().unwrap().serializeMessage()
    }

    test("a compiled subclass decompiles to an equal plain instruction") {
        val decompiled = SolanaTxLegacy.compile(PAYER, BLOCKHASH, typed).unwrap().toRequest().unwrap().instructions.single()
        decompiled::class shouldBe Instruction::class
        decompiled shouldBe typed
        typed shouldBe decompiled
    }

    test("compute budget decoding keeps subclass instances in the remaining instructions") {
        val settings = ComputeBudgetSettings.decode(listOf(ComputeBudgetProgram.setComputeUnitLimit(1000), typed))
        settings.computeUnitLimit shouldBe 1000L
        settings.remaining.single() shouldBeSameInstanceAs typed
    }

    test("decoders read a subclass instance as they read the plain instruction") {
        val decoder = SolanaInstructionDecoder { instruction, checkSigners ->
            SolanaAccountsReader.decode(instruction, checkSigners) { reader ->
                val authority = reader.required("authority", signer = true, writable = true)
                val vault = reader.required("vault", signer = false, writable = true)
                Triple(authority, vault, instruction.data[1])
            }
        }
        val expected: Result<Triple<SolanaAddress, SolanaAddress, Byte>, SolanaDecodeError> = success(Triple(PAYER, VAULT, 5.toByte()))
        decoder.decode(typed, true) shouldBe expected
        decoder.decode(typed, true) shouldBe decoder.decode(plain, true)

        val unsigned = Withdraw(VAULT, 5).let { Instruction(it.programId, listOf(AccountMeta.writable(PAYER), AccountMeta.writable(VAULT)), it.data) }
        decoder.decode(unsigned, true).isFailure() shouldBe true

        val executed = listOf(ExecutedInstruction(InstructionPosition(0), typed), ExecutedInstruction(InstructionPosition(1), plain))
        executed.decodeInstructions(PROGRAM, decoder).map { it.value } shouldBe listOf(expected, expected)
    }
})
