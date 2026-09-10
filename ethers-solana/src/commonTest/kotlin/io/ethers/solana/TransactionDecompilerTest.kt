package io.ethers.solana

import io.ethers.solana.instruction.ComputeBudgetProgram
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.LoadedAddresses
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.SolanaRPCInstruction
import io.ethers.solana.types.SolanaRPCMessage
import io.ethers.solana.types.SolanaRPCTransaction
import io.ethers.solana.types.SolanaRPCTransactionData
import io.ethers.solana.types.SolanaRPCTransactionMeta
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionException
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TransactionDecompilerTest : FunSpec({
    val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val carol = KeypairSigner.fromSeed(ByteArray(32) { 3 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 9 })

    // enough movable accounts that a table pays for itself
    val movable = List(6) { SolanaAddress(ByteArray(32) { i -> if (i == 0) (100 + it).toByte() else 7 }) }
    val program = SolanaAddress(ByteArray(32) { 42 })

    fun readIndexed(): Instruction = Instruction(
        program,
        listOf(AccountMeta.signerAndWritable(alice.publicKey), AccountMeta.signer(bob.publicKey), AccountMeta.writable(carol.publicKey)) +
            movable.mapIndexed { i, address -> AccountMeta(address, writable = i % 2 == 0) },
        byteArrayOf(1, 2, 3),
    )

    val table = AddressLookupTableAccount(SolanaAddress(ByteArray(32) { 77 }), movable)

    test("a legacy transaction round-trips to identical bytes") {
        val original = requestOf(alice.publicKey, blockhash, listOf(SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L), readIndexed()))
            .compileLegacy()

        original.toRequest().compileLegacy().serializeMessage() shouldBe original.serializeMessage()
    }

    test("a v0 transaction round-trips to identical bytes when given its tables") {
        val original = requestOf(alice.publicKey, blockhash, listOf(readIndexed())).compileV0(listOf(table))
        original.addressLookupTables.size shouldBe 1

        val recompiled = original.toRequest(listOf(table)).compileV0(listOf(table))
        recompiled.serializeMessage() shouldBe original.serializeMessage()
    }

    test("every account keeps its signer and writable flags through the round trip") {
        val instruction = readIndexed()
        val original = requestOf(alice.publicKey, blockhash, listOf(instruction)).compileV0(listOf(table))

        val recovered = original.toRequest(listOf(table)).instructions.single()
        recovered.programId shouldBe instruction.programId
        recovered.data shouldBe instruction.data
        // the compiler may reorder accounts, so compare as sets of (address, signer, writable)
        recovered.keys.toSet() shouldBe instruction.keys.toSet()
    }

    test("compute budget instructions survive a legacy round trip without moving or duplicating") {
        val original = requestOf(
            alice.publicKey,
            blockhash,
            listOf(
                ComputeBudgetProgram.setComputeUnitLimit(200_000),
                SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L),
                ComputeBudgetProgram.setComputeUnitPrice(bigIntegerOf(5_000)),
            ),
        ).compileLegacy()

        val request = original.toRequest()
        request.instructions.size shouldBe 3
        // the settings stay encoded as instructions, so the request does not restate them
        request.computeUnitLimit shouldBe null
        request.computeUnitPrice shouldBe null
        request.compileLegacy().serializeMessage() shouldBe original.serializeMessage()
    }

    test("a v1 transaction carries its inline config onto the request") {
        val config = SolanaTransactionConfig(priorityFee = bigIntegerOf(1_000), computeUnitLimit = 200_000, loadedAccountsDataSizeLimit = 64_000, heapSize = 65_536)
        val original = SolanaTxV1.compile(alice.publicKey, blockhash, listOf(SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L)), config)

        val request = original.toRequest()
        request.computeUnitLimit shouldBe 200_000
        request.priorityFee shouldBe bigIntegerOf(1_000)
        request.loadedAccountsDataSizeLimit shouldBe 64_000
        request.heapSize shouldBe 65_536
        request.compileV1().serializeMessage() shouldBe original.serializeMessage()
    }

    test("a node's resolved addresses are preferred over the tables on hand") {
        val original = requestOf(alice.publicKey, blockhash, listOf(readIndexed())).compileV0(listOf(table))
        val lookup = original.addressLookupTables.single()

        // the same table, since extended with different addresses: what the node resolved must win
        val stale = AddressLookupTableAccount(table.key, List(movable.size) { SolanaAddress(ByteArray(32) { _ -> 55 }) })
        val loaded = LoadedAddresses(
            writable = lookup.writableIndexes.map { movable[it] },
            readonly = lookup.readonlyIndexes.map { movable[it] },
        )

        val rpc = rpcTransaction(original.header, original.accounts, blockhash, original.instructions.map { SolanaRPCInstruction(it.programIdIndex, it.accounts, it.data) }, SolanaTxType.V0, lookups = listOf(lookup), loaded = loaded)
        rpc.toRequest(listOf(stale)).compileV0(listOf(table)).serializeMessage() shouldBe original.serializeMessage()
    }

    test("a v0 message without its tables reports the table it cannot resolve") {
        val original = requestOf(alice.publicKey, blockhash, listOf(readIndexed())).compileV0(listOf(table))

        val error = original.tryToRequest().unwrapError()
        error shouldBe SolanaTransactionError.InvalidMessage(
            SolanaTransactionError.Reason.UNKNOWN_LOOKUP_TABLE,
            "Message loads addresses from lookup table ${table.key}, whose contents were not supplied",
        )
        shouldThrow<SolanaTransactionException> { original.toRequest() }
    }

    test("a lookup slot the table does not hold is reported") {
        val original = requestOf(alice.publicKey, blockhash, listOf(readIndexed())).compileV0(listOf(table))
        val truncated = AddressLookupTableAccount(table.key, movable.take(1))

        val error = original.tryToRequest(listOf(truncated)).unwrapError()
        error.shouldBeInvalidMessage(SolanaTransactionError.Reason.LOOKUP_INDEX)
    }

    test("resolved addresses that do not match the message's lookups are rejected") {
        val original = requestOf(alice.publicKey, blockhash, listOf(readIndexed())).compileV0(listOf(table))
        val lookup = original.addressLookupTables.single()
        val rpc = rpcTransaction(
            original.header,
            original.accounts,
            blockhash,
            original.instructions.map { SolanaRPCInstruction(it.programIdIndex, it.accounts, it.data) },
            SolanaTxType.V0,
            lookups = listOf(lookup),
            loaded = LoadedAddresses(writable = emptyList(), readonly = emptyList()),
        )

        rpc.tryToRequest().unwrapError().shouldBeInvalidMessage(SolanaTransactionError.Reason.LOOKUP_INDEX)
    }

    test("an instruction pointing past the resolved accounts is reported") {
        val rpc = rpcTransaction(
            MessageHeader(1, 0, 1),
            listOf(alice.publicKey, Programs.SYSTEM),
            blockhash,
            listOf(SolanaRPCInstruction(1, listOf(0, 5), SolanaBytes.EMPTY)),
            SolanaTxType.Legacy,
        )

        rpc.tryToRequest().unwrapError().shouldBeInvalidMessage(SolanaTransactionError.Reason.ACCOUNT_INDEX)
    }

    test("a version this library cannot compile is reported rather than guessed") {
        val rpc = rpcTransaction(
            MessageHeader(1, 0, 1),
            listOf(alice.publicKey, Programs.SYSTEM),
            blockhash,
            listOf(SolanaRPCInstruction(1, listOf(0), SolanaBytes.EMPTY)),
            SolanaTxType.Unsupported(3),
        )

        rpc.tryToRequest().unwrapError() shouldBe SolanaTransactionError.UnsupportedVersion(3)
    }

    test("resolving accounts tags every slot with the flags its position implies") {
        val instruction = readIndexed()
        val tx = requestOf(alice.publicKey, blockhash, listOf(instruction)).compileV0(listOf(table))

        val resolved = tx.resolveAccounts(listOf(table))
        resolved.size shouldBe tx.accounts.size + table.addresses.size
        resolved[0] shouldBe AccountMeta.signerAndWritable(alice.publicKey)
        // the header counts signatures over the inline accounts only, so a loaded address never signs
        resolved.drop(tx.accounts.size).none { it.signer } shouldBe true
        resolved.count { it.writable } shouldBe instruction.keys.count { it.writable }

        // every index a compiled instruction names resolves to the meta the instruction was built with
        val compiled = tx.instructions.single()
        resolved[compiled.programIdIndex].publicKey shouldBe instruction.programId
        compiled.accounts.map { resolved[it] } shouldBe instruction.keys
    }

    test("resolving accounts reports the same failures as decompiling") {
        val tx = requestOf(alice.publicKey, blockhash, listOf(readIndexed())).compileV0(listOf(table))

        tx.tryResolveAccounts().unwrapError().shouldBeInvalidMessage(SolanaTransactionError.Reason.UNKNOWN_LOOKUP_TABLE)
        shouldThrow<SolanaTransactionException> { tx.resolveAccounts() }

        val unsupported = rpcTransaction(MessageHeader(1, 0, 1), listOf(alice.publicKey, Programs.SYSTEM), blockhash, listOf(SolanaRPCInstruction(1, listOf(0), SolanaBytes.EMPTY)), SolanaTxType.Unsupported(3))
        unsupported.tryResolveAccounts().unwrapError() shouldBe SolanaTransactionError.UnsupportedVersion(3)
    }

    test("a node's resolved addresses win when resolving accounts too") {
        val original = requestOf(alice.publicKey, blockhash, listOf(readIndexed())).compileV0(listOf(table))
        val lookup = original.addressLookupTables.single()
        val stale = AddressLookupTableAccount(table.key, List(movable.size) { SolanaAddress(ByteArray(32) { _ -> 55 }) })
        val loaded = LoadedAddresses(lookup.writableIndexes.map { movable[it] }, lookup.readonlyIndexes.map { movable[it] })

        val rpc = rpcTransaction(original.header, original.accounts, blockhash, original.instructions.map { SolanaRPCInstruction(it.programIdIndex, it.accounts, it.data) }, SolanaTxType.V0, lookups = listOf(lookup), loaded = loaded)
        rpc.resolveAccounts(listOf(stale)) shouldBe original.resolveAccounts(listOf(table))
    }

    test("a fetched transaction can be re-priced and recompiled") {
        val original = requestOf(alice.publicKey, blockhash, listOf(SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L))).compileLegacy()
        val rpc = rpcTransaction(original.header, original.accounts, blockhash, original.instructions.map { SolanaRPCInstruction(it.programIdIndex, it.accounts, it.data) }, SolanaTxType.Legacy)

        val fresh = SolanaBlockhash(ByteArray(32) { 4 })
        val repriced = rpc.toRequest()
            .blockhash(fresh)
            .computeUnitLimit(200_000)
            .computeUnitPrice(bigIntegerOf(1_000))
            .compileLegacy()

        repriced.recentBlockhash shouldBe fresh
        repriced.computeUnitLimit shouldBe 200_000
        repriced.computeUnitPrice shouldBe bigIntegerOf(1_000)
    }
})

private fun requestOf(feePayer: SolanaAddress, blockhash: SolanaBlockhash, instructions: List<Instruction>) = SolanaTransactionRequest()
    .feePayer(feePayer)
    .blockhash(blockhash)
    .instructions(instructions)

private fun SolanaTransactionError.shouldBeInvalidMessage(reason: SolanaTransactionError.Reason) {
    (this as SolanaTransactionError.InvalidMessage).reason shouldBe reason
}

private fun rpcTransaction(
    header: MessageHeader,
    accounts: List<SolanaAddress>,
    blockhash: SolanaBlockhash,
    instructions: List<SolanaRPCInstruction>,
    type: SolanaTxType,
    lookups: List<CompiledAddressLookupTable> = emptyList(),
    loaded: LoadedAddresses? = null,
) = SolanaRPCTransaction(
    slot = bigIntegerOf(1),
    blockTime = null,
    transaction = SolanaRPCTransactionData(
        signatures = emptyList(),
        message = SolanaRPCMessage(
            header = header,
            accountKeys = accounts,
            recentBlockhash = blockhash,
            instructions = instructions,
            addressTableLookups = lookups,
        ),
    ),
    meta = loaded?.let {
        SolanaRPCTransactionMeta(err = null, fee = bigIntegerOf(5_000), preBalances = emptyList(), postBalances = emptyList(), loadedAddresses = it)
    },
    type = type,
)
