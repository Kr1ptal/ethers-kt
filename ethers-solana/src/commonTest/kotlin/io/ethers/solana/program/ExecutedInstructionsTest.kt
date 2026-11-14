package io.ethers.solana.program

import io.ethers.core.Kotlinx
import io.ethers.core.success
import io.ethers.solana.codec.SolanaInstructionDecoder
import io.ethers.solana.corpus.transactionCorpus
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.TransactionSimulation
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

private val PAYER = SolanaAddress(ByteArray(32) { 1 })
private val VAULT = SolanaAddress(ByteArray(32) { 2 })
private val PROGRAM = SolanaAddress(ByteArray(32) { 3 })
private val OTHER = SolanaAddress(ByteArray(32) { 4 })

/** Two top-level instructions to [PROGRAM] and [OTHER]; the first makes one inner call back into [PROGRAM]. */
private fun transaction(innerAccounts: String = "[1]") = Kotlinx.DEFAULT.decodeFromString<SolanaRPCTransaction>(
    """
    {
      "slot": 1,
      "blockTime": null,
      "transaction": {
        "signatures": ["${SolanaSignature(ByteArray(64) { 7 })}"],
        "message": {
          "header": {"numRequiredSignatures": 1, "numReadonlySignedAccounts": 0, "numReadonlyUnsignedAccounts": 2},
          "accountKeys": ["$PAYER", "$VAULT", "$PROGRAM", "$OTHER"],
          "recentBlockhash": "${SolanaBlockhash(ByteArray(32) { 5 })}",
          "instructions": [
            {"programIdIndex": 2, "accounts": [0, 1], "data": "${SolanaBytes.fromHex("0102").toBase58()}"},
            {"programIdIndex": 3, "accounts": [1], "data": ""}
          ]
        }
      },
      "meta": {
        "err": null, "fee": 5000, "preBalances": [], "postBalances": [],
        "innerInstructions": [
          {"index": 0, "instructions": [{"programIdIndex": 2, "accounts": $innerAccounts, "data": "${SolanaBytes.fromHex("03").toBase58()}", "stackHeight": 2}]}
        ]
      },
      "version": "legacy"
    }
    """,
)

class ExecutedInstructionsTest : FunSpec({
    test("instructions come in execution order with their positions and resolved accounts") {
        val executed = transaction().executedInstructions().unwrap()
        executed shouldBe listOf(
            ExecutedInstruction(
                InstructionPosition(0),
                Instruction(PROGRAM, listOf(AccountMeta.signerAndWritable(PAYER), AccountMeta.writable(VAULT)), SolanaBytes.fromHex("0102")),
            ),
            ExecutedInstruction(InstructionPosition(0, 0, 2), Instruction(PROGRAM, listOf(AccountMeta.writable(VAULT)), SolanaBytes.fromHex("03"))),
            ExecutedInstruction(InstructionPosition(1), Instruction(OTHER, listOf(AccountMeta.writable(VAULT)), SolanaBytes.EMPTY)),
        )
    }

    test("an account index outside the message is invalid") {
        transaction(innerAccounts = "[9]").executedInstructions().unwrapError()
            .shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>().reason shouldBe SolanaTransactionError.Reason.ACCOUNT_INDEX
    }

    test("decoding selects one program's instructions and checks signers only at the top level") {
        val calls = ArrayList<Pair<SolanaBytes, Boolean>>()
        val decoder = SolanaInstructionDecoder { instruction, checkSigners ->
            calls.add(instruction.data to checkSigners)
            success(instruction.data.size)
        }
        val decoded = transaction().executedInstructions().unwrap().decodeInstructions(PROGRAM, decoder)
        decoded.map { it.position } shouldBe listOf(InstructionPosition(0), InstructionPosition(0, 0, 2))
        decoded.map { it.value.unwrap() } shouldBe listOf(2, 1)
        calls shouldBe listOf(SolanaBytes.fromHex("0102") to true, SolanaBytes.fromHex("03") to false)
    }

    test("simulated instructions resolve against the simulated transaction") {
        val tx = transaction()
        val simulation = Kotlinx.DEFAULT.decodeFromString<TransactionSimulation>(
            """{"err":null,"innerInstructions":[{"index":1,"instructions":[{"programIdIndex":2,"accounts":[],"data":""}]}]}""",
        )
        simulation.executedInstructions(tx).unwrap().map { it.position } shouldBe listOf(
            InstructionPosition(0),
            InstructionPosition(1),
            InstructionPosition(1, 0, null),
        )
    }

    test("every corpus transaction resolves each instruction, inner ones included") {
        var inner = 0
        for (record in transactionCorpus()) {
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCTransaction>(Kotlinx.DEFAULT.parseToJsonElement(record).jsonObject.getValue("rpc"))
            val executed = tx.executedInstructions().unwrap()
            val innerCount = tx.meta?.innerInstructions.orEmpty().sumOf { it.instructions.size }
            executed.size shouldBe tx.instructions.size + innerCount
            executed.count { !it.position.isInner } shouldBe tx.instructions.size
            // positions never go backwards
            executed.map { it.position.instructionIndex } shouldBe executed.map { it.position.instructionIndex }.sorted()
            inner += innerCount
        }
        (inner > 0) shouldBe true
    }
})
