package io.ethers.solana

import io.ethers.core.isFailure
import io.ethers.solana.instruction.ComputeBudgetProgram
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.providers.ConfirmationTracking
import io.ethers.solana.providers.confirmationTracking
import io.ethers.solana.providers.decodeConfirmationTracking
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.rpc.SolanaRPCInstruction
import io.ethers.solana.types.rpc.SolanaRPCMessage
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.SolanaRPCTransactionData
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTransaction
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SolanaTransactionRequestTest : FunSpec({
    val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
    val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
    val blockhash = SolanaBlockhash(ByteArray(32) { 3 })
    val transfer = SystemProgram.transfer(alice.publicKey, bob.publicKey, 42L)

    fun request() = SolanaTransactionRequest {
        feePayer(alice.publicKey)
        blockhash(blockhash)
        instruction(transfer)
    }

    test("a request compiles to the same message as the version's own compile") {
        request().compileLegacy().unwrap().serializeMessage() shouldBe
            SolanaTxLegacy.compile(alice.publicKey, blockhash, transfer).unwrap().serializeMessage()
        request().compileV0().unwrap().serializeMessage() shouldBe
            SolanaTxV0.compile(alice.publicKey, blockhash, transfer).unwrap().serializeMessage()
        request().compileV1().unwrap().serializeMessage() shouldBe
            SolanaTxV1.compile(alice.publicKey, blockhash, transfer, io.ethers.solana.types.transaction.SolanaTransactionConfig()).unwrap().serializeMessage()
    }

    test("the DSL, chained setters and copy constructor agree") {
        val chained = SolanaTransactionRequest()
            .feePayer(alice.publicKey)
            .blockhash(blockhash)
            .instruction(transfer)
        chained.compileV0().unwrap().serializeMessage() shouldBe request().compileV0().unwrap().serializeMessage()
        SolanaTransactionRequest(chained).compileV0().unwrap().serializeMessage() shouldBe chained.compileV0().unwrap().serializeMessage()

        val many = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instructions(listOf(transfer))
            instruction(transfer)
        }
        many.instructions.size shouldBe 2
    }

    test("a request without a fee payer or blockhash names the missing field") {
        SolanaTransactionRequest { blockhash(blockhash) }.compileV0().unwrapError() shouldBe
            SolanaTransactionError.MissingFeePayer
        SolanaTransactionRequest { feePayer(alice.publicKey) }.compileV1().unwrapError() shouldBe
            SolanaTransactionError.MissingBlockhash
        SolanaTransactionRequest { }.compileLegacy().isFailure() shouldBe true
    }

    test("legacy and v0 encode compute budget as prepended ComputeBudget instructions") {
        val tx = request().apply {
            computeUnitLimit(200_000)
            computeUnitPrice(1_000)
        }.compileV0().unwrap()

        val budget = tx.instructions.filter { tx.accounts[it.programIdIndex] == Programs.COMPUTE_BUDGET }
        budget.map { it.data.toHex() } shouldBe listOf("02400d0300", "03e803000000000000")
        // compute budget is prepended, ahead of the caller's own instructions
        tx.instructions.take(2) shouldBe budget
    }

    test("compute budget insertion preserves the leading nonce advance in every compilation path") {
        val nonceAccount = SolanaAddress(ByteArray(32) { 9 })
        val advance = SystemProgram.advanceNonceAccount(nonceAccount, alice.publicKey)
        val req = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instructions(listOf(advance, ComputeBudgetProgram.setComputeUnitLimit(1), transfer))
            computeUnitLimit(200_000)
            computeUnitPrice(1_000)
            heapSize(32768)
            loadedAccountsDataSizeLimit(65536)
        }
        val expected = listOf(
            advance,
            ComputeBudgetProgram.requestHeapFrame(32768),
            ComputeBudgetProgram.setComputeUnitLimit(200_000),
            ComputeBudgetProgram.setComputeUnitPrice(1_000),
            ComputeBudgetProgram.setLoadedAccountsDataSizeLimit(65536),
            transfer,
        )
        for (tx in listOf(req.compile().unwrap(), req.compileLegacy().unwrap(), req.compileV0().unwrap())) {
            tx.instructions.map { tx.accounts[it.programIdIndex] to it.data } shouldBe expected.map { it.programId to it.data }
            tx.instructions.count { it.data == advance.data && tx.accounts[it.programIdIndex] == Programs.SYSTEM } shouldBe 1
            tx.confirmationTracking() shouldBe ConfirmationTracking.DurableNonce(nonceAccount, blockhash)
            decodeConfirmationTracking(tx.sign(alice).serialize()) shouldBe tx.confirmationTracking()
        }
        // V1 has inline compute settings, so the original instruction sequence stays intact.
        val v1 = req.compileV1().unwrap()
        v1.instructions.map { v1.accounts[it.programIdIndex] to it.data } shouldBe req.instructions.map { it.programId to it.data }
        v1.confirmationTracking() shouldBe ConfirmationTracking.DurableNonce(nonceAccount, blockhash)
        req.instructions shouldBe listOf(advance, ComputeBudgetProgram.setComputeUnitLimit(1), transfer)
    }

    test("durableNonce sets the hash and preserves nonce tracking through compilation and signing") {
        val nonceAccount = SolanaAddress(ByteArray(32) { 9 })
        val nonce = SolanaBlockhash(ByteArray(32) { 10 })
        val req = request().durableNonce(nonceAccount, bob.publicKey, nonce)
            .computeUnitLimit(200_000).computeUnitPrice(1_000)
        req.blockhash shouldBe nonce
        req.instructions shouldBe listOf(SystemProgram.advanceNonceAccount(nonceAccount, bob.publicKey), transfer)
        for (tx in listOf(req.compile().unwrap(), req.compileLegacy().unwrap(), req.compileV0().unwrap(), req.compileV1().unwrap())) {
            tx.recentBlockhash shouldBe nonce
            tx.signers.toSet() shouldBe setOf(alice.publicKey, bob.publicKey)
            tx.confirmationTracking() shouldBe ConfirmationTracking.DurableNonce(nonceAccount, nonce)
            decodeConfirmationTracking(tx.sign(alice, bob).serialize()) shouldBe tx.confirmationTracking()
        }
    }

    test("durableNonce replaces a leading advance but preserves later nonce operations and other programs") {
        val firstAccount = SolanaAddress(ByteArray(32) { 9 })
        val secondAccount = SolanaAddress(ByteArray(32) { 10 })
        val nonce = SolanaBlockhash(ByteArray(32) { 11 })
        val laterAdvance = SystemProgram.advanceNonceAccount(firstAccount, alice.publicKey)
        val other = Instruction(Programs.TOKEN, emptyList(), byteArrayOf(4, 0, 0, 0))
        val req = request().instructions(listOf(other, transfer, laterAdvance))
        req.durableNonce(firstAccount, alice.publicKey, blockhash)
        req.durableNonce(secondAccount, bob.publicKey, nonce)
        req.durableNonce(secondAccount, bob.publicKey, nonce)
        req.instructions shouldBe listOf(SystemProgram.advanceNonceAccount(secondAccount, bob.publicKey), other, transfer, laterAdvance)
        req.blockhash shouldBe nonce
        val copied = SolanaTransactionRequest(req)
        copied.durableNonce(firstAccount, alice.publicKey, blockhash)
        req.blockhash shouldBe nonce
        req.instructions.first() shouldBe SystemProgram.advanceNonceAccount(secondAccount, bob.publicKey)
    }

    test("durableNonce can initialize an empty DSL request before appending instructions") {
        val nonceAccount = SolanaAddress(ByteArray(32) { 9 })
        val req = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            durableNonce(nonceAccount, alice.publicKey, blockhash)
            instruction(transfer)
        }
        req.instructions shouldBe listOf(SystemProgram.advanceNonceAccount(nonceAccount, alice.publicKey), transfer)
        req.compileLegacy().unwrap().confirmationTracking() shouldBe ConfirmationTracking.DurableNonce(nonceAccount, blockhash)
    }

    test("nonce opcode from another program does not change compute budget placement") {
        val other = Instruction(Programs.TOKEN, emptyList(), byteArrayOf(4, 0, 0, 0))
        val tx = request().instructions(listOf(other, transfer)).computeUnitLimit(200_000).compileLegacy().unwrap()
        tx.accounts[tx.instructions.first().programIdIndex] shouldBe Programs.COMPUTE_BUDGET
        tx.accounts[tx.instructions[1].programIdIndex] shouldBe Programs.TOKEN
    }

    test("a field replaces the matching ComputeBudget instruction but leaves the other alone") {
        val withBoth = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.setComputeUnitLimit(1))
            instruction(ComputeBudgetProgram.setComputeUnitPrice(7))
            instruction(transfer)
            computeUnitLimit(200_000)
        }.compileV0().unwrap()

        val budget = withBoth.instructions
            .filter { withBoth.accounts[it.programIdIndex] == Programs.COMPUTE_BUDGET }
            .map { it.data.toHex() }
        // the limit came from the field, the untouched price instruction survived
        budget shouldBe listOf("02400d0300", "030700000000000000")
    }

    test("compute budget instructions pass through untouched when no field is set") {
        val tx = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.setComputeUnitLimit(1))
            instruction(transfer)
        }.compileV0().unwrap()
        tx.instructions.first().data.toHex() shouldBe "0201000000"
    }

    test("v1 compiles ComputeBudget instructions as instructions and takes its config from the fields") {
        val request = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.setComputeUnitLimit(200_000))
            instruction(ComputeBudgetProgram.setComputeUnitPrice(1_000))
            instruction(transfer)
        }
        val tx = request.compileV1().unwrap()

        // the instructions do not configure v1, so they stay instructions and the config is empty
        tx.config shouldBe SolanaTransactionConfig()
        tx.instructions.size shouldBe 3
        tx.accounts.any { it == Programs.COMPUTE_BUDGET } shouldBe true
        // real mainnet v1 transactions carry both, so dropping them would not reproduce the message
        tx.toRequest().unwrap().instructions shouldBe request.instructions

        // the same request states them inline on v1 when they are set as fields instead
        val inline = SolanaTransactionRequest(request)
            .instructions(listOf(transfer))
            .computeUnitLimit(200_000)
            .computeUnitPrice(1_000)
            .compileV1()
            .unwrap()
        inline.config.computeUnitLimit shouldBe 200_000
        // 200000 units * 1000 micro-lamports, rounded up to whole lamports
        inline.config.priorityFee shouldBe bigIntegerOf(200)
        inline.accounts.none { it == Programs.COMPUTE_BUDGET } shouldBe true
        inline.instructions.size shouldBe 1
    }

    test("v1 config fields cover heap and data size limits without consuming instructions") {
        val tx = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.setComputeUnitLimit(1))
            instruction(transfer)
            computeUnitLimit(300_000)
            loadedAccountsDataSizeLimit(65536)
            heapSize(65536)
        }.compileV1().unwrap()

        tx.config.computeUnitLimit shouldBe 300_000
        tx.config.loadedAccountsDataSizeLimit shouldBe 65536
        tx.config.heapSize shouldBe 65536
        // the field sets the budget; the instruction is still carried, as the runtime receives it
        tx.instructions.size shouldBe 2
    }

    test("heap size and data size limit are settable without writing an instruction") {
        val v1 = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
            heapSize(65536)
            loadedAccountsDataSizeLimit(131072)
        }.compileV1().unwrap()
        v1.config.heapSize shouldBe 65536
        v1.config.loadedAccountsDataSizeLimit shouldBe 131072
        v1.accounts.none { it == Programs.COMPUTE_BUDGET } shouldBe true

        // the same request encodes them as instructions on v0, in discriminant order
        val v0 = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
            heapSize(65536)
            loadedAccountsDataSizeLimit(131072)
        }.compileV0().unwrap()
        v0.instructions.take(2).map { it.data.toHex() } shouldBe listOf("0100000100", "0400000200")
    }

    test("a heap size field replaces an existing heap frame instruction on v0 and v1") {
        fun request() = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.requestHeapFrame(32768))
            instruction(transfer)
            heapSize(65536)
        }
        request().compileV1().unwrap().config.heapSize shouldBe 65536
        val v0 = request().compileV0().unwrap()
        v0.instructions.filter { v0.accounts[it.programIdIndex] == Programs.COMPUTE_BUDGET }
            .map { it.data.toHex() } shouldBe listOf("0100000100")
    }

    test("v1 keeps ComputeBudget instructions it cannot decode") {
        val opaque = Instruction(Programs.COMPUTE_BUDGET, emptyList(), byteArrayOf(9, 9))
        val tx = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(opaque)
            instruction(transfer)
        }.compileV1().unwrap()
        tx.instructions.size shouldBe 2
        tx.config.computeUnitLimit shouldBe null
    }

    test("compile never reaches v0 without being given tables, whatever the request holds") {
        // the documented guarantee: v0 can only appear once the caller has asked for it by passing
        // tables, so an auto-compiled message is safe against a wallet or RPC that takes legacy only
        var compiled = 0
        for (count in listOf(1, 8, 25, 60)) {
            val accounts = List(count) { SolanaAddress(ByteArray(32) { _ -> (it + 10).toByte() }) }
            val request = SolanaTransactionRequest {
                feePayer(alice.publicKey)
                blockhash(blockhash)
                instruction(Instruction(Programs.SYSTEM, accounts.map { AccountMeta.writable(it) }, byteArrayOf(1)))
                computeUnitLimit(200_000)
                computeUnitPrice(bigIntegerOf(5_000))
            }
            // either it compiles as legacy or it does not compile at all; it is never silently v0
            request.compile().unwrapOrNull()?.let {
                it.type shouldBe SolanaTxType.Legacy
                compiled++
            }
        }
        // the largest of these overflows the legacy envelope, so prove the smaller ones really compiled
        (compiled >= 3) shouldBe true
    }

    test("compile picks legacy when no table earns its place, and v0 when one does") {
        val movable = List(2) { SolanaAddress(ByteArray(32) { _ -> (it + 10).toByte() }) }
        fun withAccounts(accounts: List<SolanaAddress>) = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(Instruction(Programs.SYSTEM, accounts.map { AccountMeta.writable(it) }, byteArrayOf(1)))
        }

        // no tables at all, and a table covering only one account, both stay legacy
        withAccounts(movable).compile().unwrap().type shouldBe SolanaTxType.Legacy
        val single = AddressLookupTableAccount(SolanaAddress(ByteArray(32) { 90 }), movable.take(1))
        withAccounts(movable).compile(listOf(single)).unwrap().type shouldBe SolanaTxType.Legacy

        val both = AddressLookupTableAccount(SolanaAddress(ByteArray(32) { 91 }), movable)
        val v0 = withAccounts(movable).compile(listOf(both)).unwrap()
        v0.type shouldBe SolanaTxType.V0
        // and the chosen encoding is the smaller one
        (v0.envelopeSize() < withAccounts(movable).compile().unwrap().envelopeSize()) shouldBe true

        // v1 is never chosen for you
        withAccounts(movable).compile(listOf(both)).unwrap().type shouldBe SolanaTxType.V0
    }

    test("priorityFee takes precedence over computeUnitPrice on every version") {
        fun request() = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
            computeUnitLimit(200_000)
            computeUnitPrice(1)
            priorityFee(500)
        }

        // v1 carries the exact total, ignoring the per-unit price
        request().compileV1().unwrap().config.priorityFee shouldBe bigIntegerOf(500)

        // legacy and v0 can only price per unit, so the total is converted, rounding up
        val v0 = request().compileV0().unwrap()
        val price = v0.instructions.map { it.data.toHex() }.single { it.startsWith("03") }
        // ceil(500 * 1_000_000 / 200_000) = 2500 micro-lamports per unit
        price shouldBe "03c409000000000000"
        // which the runtime charges back as at least the requested total
        v0.estimateFee(bigIntegerOf(0)) shouldBe bigIntegerOf(500)
    }

    test("a priority fee needs a limit on legacy and v0, but not on v1") {
        fun request() = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
            priorityFee(500)
        }
        request().compileV1().unwrap().config.priorityFee shouldBe bigIntegerOf(500)

        val error = request().compileV0().unwrapError()
        error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
        error.reason shouldBe SolanaTransactionError.Reason.CONFIG

        // a limit carried by an instruction is enough
        val withInstructionLimit = request().apply { instruction(ComputeBudgetProgram.setComputeUnitLimit(200_000)) }
        withInstructionLimit.compileV0().isFailure() shouldBe false
    }

    test("every version reports its compute budget through the same accessors") {
        fun request() = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
            computeUnitLimit(200_000)
            computeUnitPrice(1_000)
            heapSize(65536)
            loadedAccountsDataSizeLimit(131072)
        }

        val v0 = request().compileV0().unwrap()
        val v1 = request().compileV1().unwrap()
        val legacy = request().compileLegacy().unwrap()

        for (tx in listOf(legacy, v0, v1)) {
            tx.computeUnitLimit shouldBe 200_000
            tx.heapSize shouldBe 65536
            tx.loadedAccountsDataSizeLimit shouldBe 131072
            // 200000 units at 1000 micro-lamports rounds up to 200 lamports, however it is encoded
            tx.priorityFee shouldBe bigIntegerOf(200)
        }

        // legacy and v0 state a per-unit price; v1 states the total instead
        legacy.computeUnitPrice shouldBe bigIntegerOf(1_000)
        v0.computeUnitPrice shouldBe bigIntegerOf(1_000)
        v1.computeUnitPrice shouldBe null
    }

    test("a message that sets nothing reports nothing, and a v1 message ignores ComputeBudget instructions") {
        val bare = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
        }.compileV0().unwrap()
        bare.computeUnitLimit shouldBe null
        bare.computeUnitPrice shouldBe null
        bare.priorityFee shouldBe null
        bare.heapSize shouldBe null
        bare.loadedAccountsDataSizeLimit shouldBe null
        bare.estimateFee(bigIntegerOf(5000)) shouldBe bigIntegerOf(5000)

        // an instruction the compiler could not translate stays in a v1 message, but does not configure it
        val opaque = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(Instruction(Programs.COMPUTE_BUDGET, emptyList(), byteArrayOf(9, 9)))
            instruction(transfer)
        }.compileV1().unwrap()
        opaque.accounts.contains(Programs.COMPUTE_BUDGET) shouldBe true
        opaque.computeUnitLimit shouldBe null
        opaque.priorityFee shouldBe null
    }

    test("a stated price with no limit has no determinable total") {
        val tx = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.setComputeUnitPrice(1_000))
            instruction(transfer)
        }.compileV0().unwrap()

        tx.computeUnitPrice shouldBe bigIntegerOf(1_000)
        // the runtime would apply a default limit, which this library cannot predict
        tx.priorityFee shouldBe null
        shouldThrow<IllegalArgumentException> { tx.estimateFee(bigIntegerOf(5000)) }

        // a price of zero costs nothing, limit or not
        val free = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.setComputeUnitPrice(0))
            instruction(transfer)
        }.compileV0().unwrap()
        free.priorityFee shouldBe bigIntegerOf(0)
        free.estimateFee(bigIntegerOf(5000)) shouldBe bigIntegerOf(5000)
    }

    test("an RPC response reads as a transaction view, whatever version it holds") {
        val compiled = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
            computeUnitLimit(200_000)
            computeUnitPrice(1_000)
            heapSize(65536)
        }.compileV0().unwrap()

        val rpc = SolanaRPCTransaction(
            slot = bigIntegerOf(1),
            blockTime = null,
            transaction = SolanaRPCTransactionData(
                signatures = emptyList(),
                message = SolanaRPCMessage(
                    header = compiled.header,
                    accountKeys = compiled.accounts,
                    recentBlockhash = compiled.recentBlockhash,
                    instructions = compiled.instructions.map { SolanaRPCInstruction(it.programIdIndex, it.accounts, it.data) },
                ),
            ),
            meta = null,
            type = SolanaTxType.V0,
        )

        // the same reads work whether the transaction was built here or returned by a node
        val views = listOf<SolanaTransaction>(compiled, rpc)
        for (view in views) {
            view.feePayer shouldBe alice.publicKey
            view.signers shouldBe listOf(alice.publicKey)
            view.computeUnitLimit shouldBe 200_000
            view.computeUnitPrice shouldBe bigIntegerOf(1_000)
            view.priorityFee shouldBe bigIntegerOf(200)
            view.heapSize shouldBe 65536
            view.estimateFee(bigIntegerOf(5000)) shouldBe bigIntegerOf(5200)
        }
        rpc.instructions.map { it.programIdIndex } shouldBe compiled.instructions.map { it.programIdIndex }
    }

    test("a version this library cannot construct reports no compute budget rather than guessing") {
        val unsupported = SolanaRPCTransaction(
            slot = bigIntegerOf(1),
            blockTime = null,
            transaction = SolanaRPCTransactionData(
                signatures = emptyList(),
                message = SolanaRPCMessage(
                    header = MessageHeader(1, 0, 1),
                    accountKeys = listOf(alice.publicKey, Programs.COMPUTE_BUDGET),
                    recentBlockhash = blockhash,
                    // would decode as a compute unit limit in a legacy message
                    instructions = listOf(SolanaRPCInstruction(1, emptyList(), ComputeBudgetProgram.setComputeUnitLimit(200_000).data)),
                ),
            ),
            meta = null,
            type = SolanaTxType.Unsupported(3),
        )
        unsupported.computeUnitLimit shouldBe null
        unsupported.priorityFee shouldBe null
        unsupported.heapSize shouldBe null
        unsupported.feePayer shouldBe alice.publicKey
    }

    test("a v1 price with no limit cannot become a priority fee") {
        val error = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
            computeUnitPrice(1_000)
        }.compileV1().unwrapError()
        error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
        error.reason shouldBe SolanaTransactionError.Reason.CONFIG
    }
})
