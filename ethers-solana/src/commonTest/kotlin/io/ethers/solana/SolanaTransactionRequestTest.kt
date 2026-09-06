package io.ethers.solana

import io.ethers.core.isFailure
import io.ethers.solana.instruction.ComputeBudgetProgram
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.bigIntegerOf
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
        request().compileLegacy().serializeMessage() shouldBe
            SolanaTxLegacy.compile(alice.publicKey, blockhash, transfer).serializeMessage()
        request().compileV0().serializeMessage() shouldBe
            SolanaTxV0.compile(alice.publicKey, blockhash, transfer).serializeMessage()
        request().compileV1().serializeMessage() shouldBe
            SolanaTxV1.compile(alice.publicKey, blockhash, transfer, io.ethers.solana.types.transaction.SolanaTransactionConfig()).serializeMessage()
    }

    test("the DSL, chained setters and copy constructor agree") {
        val chained = SolanaTransactionRequest()
            .feePayer(alice.publicKey)
            .blockhash(blockhash)
            .instruction(transfer)
        chained.compileV0().serializeMessage() shouldBe request().compileV0().serializeMessage()
        SolanaTransactionRequest(chained).compileV0().serializeMessage() shouldBe chained.compileV0().serializeMessage()

        val many = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instructions(listOf(transfer))
            instruction(transfer)
        }
        many.instructions.size shouldBe 2
    }

    test("a request without a fee payer or blockhash names the missing field") {
        SolanaTransactionRequest { blockhash(blockhash) }.tryCompileV0().unwrapError() shouldBe
            SolanaTransactionError.MissingFeePayer
        SolanaTransactionRequest { feePayer(alice.publicKey) }.tryCompileV1().unwrapError() shouldBe
            SolanaTransactionError.MissingBlockhash
        SolanaTransactionRequest { }.tryCompileLegacy().isFailure() shouldBe true
    }

    test("legacy and v0 encode compute budget as prepended ComputeBudget instructions") {
        val tx = request().apply {
            computeUnitLimit(200_000)
            computeUnitPrice(1_000)
        }.compileV0()

        val budget = tx.instructions.filter { tx.accounts[it.programIdIndex] == Programs.COMPUTE_BUDGET }
        budget.map { it.data.toHex() } shouldBe listOf("02400d0300", "03e803000000000000")
        // compute budget is prepended, ahead of the caller's own instructions
        tx.instructions.take(2) shouldBe budget
    }

    test("a field replaces the matching ComputeBudget instruction but leaves the other alone") {
        val withBoth = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.setComputeUnitLimit(1))
            instruction(ComputeBudgetProgram.setComputeUnitPrice(7))
            instruction(transfer)
            computeUnitLimit(200_000)
        }.compileV0()

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
        }.compileV0()
        tx.instructions.first().data.toHex() shouldBe "0201000000"
    }

    test("v1 translates ComputeBudget instructions into inline config and drops them") {
        val tx = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.setComputeUnitLimit(200_000))
            instruction(ComputeBudgetProgram.setComputeUnitPrice(1_000))
            instruction(transfer)
        }.compileV1()

        tx.config.computeUnitLimit shouldBe 200_000
        // 200000 units * 1000 micro-lamports, rounded up to whole lamports
        tx.config.priorityFee shouldBe bigIntegerOf(200)
        tx.accounts.none { it == Programs.COMPUTE_BUDGET } shouldBe true
        tx.instructions.size shouldBe 1
    }

    test("v1 fields win over instructions and cover heap and data size limits") {
        val tx = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(ComputeBudgetProgram.setComputeUnitLimit(1))
            instruction(ComputeBudgetProgram.setLoadedAccountsDataSizeLimit(65536))
            instruction(ComputeBudgetProgram.requestHeapFrame(65536))
            instruction(transfer)
            computeUnitLimit(300_000)
        }.compileV1()

        tx.config.computeUnitLimit shouldBe 300_000
        tx.config.loadedAccountsDataSizeLimit shouldBe 65536
        tx.config.heapSize shouldBe 65536
        tx.instructions.size shouldBe 1
    }

    test("heap size and data size limit are settable without writing an instruction") {
        val v1 = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
            heapSize(65536)
            loadedAccountsDataSizeLimit(131072)
        }.compileV1()
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
        }.compileV0()
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
        request().compileV1().config.heapSize shouldBe 65536
        val v0 = request().compileV0()
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
        }.compileV1()
        tx.instructions.size shouldBe 2
        tx.config.computeUnitLimit shouldBe null
    }

    test("tryCompile picks legacy when no table earns its place, and v0 when one does") {
        val movable = List(2) { SolanaAddress(ByteArray(32) { _ -> (it + 10).toByte() }) }
        fun withAccounts(accounts: List<SolanaAddress>) = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(Instruction(Programs.SYSTEM, accounts.map { AccountMeta.writable(it) }, byteArrayOf(1)))
        }

        // no tables at all, and a table covering only one account, both stay legacy
        withAccounts(movable).compile().type shouldBe SolanaTxType.Legacy
        val single = AddressLookupTableAccount(SolanaAddress(ByteArray(32) { 90 }), movable.take(1))
        withAccounts(movable).compile(listOf(single)).type shouldBe SolanaTxType.Legacy

        val both = AddressLookupTableAccount(SolanaAddress(ByteArray(32) { 91 }), movable)
        val v0 = withAccounts(movable).compile(listOf(both))
        v0.type shouldBe SolanaTxType.V0
        // and the chosen encoding is the smaller one
        (v0.envelopeSize() < withAccounts(movable).compile().envelopeSize()) shouldBe true

        // v1 is never chosen for you
        withAccounts(movable).compile(listOf(both)).type shouldBe SolanaTxType.V0
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
        request().compileV1().config.priorityFee shouldBe bigIntegerOf(500)

        // legacy and v0 can only price per unit, so the total is converted, rounding up
        val v0 = request().compileV0()
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
        request().compileV1().config.priorityFee shouldBe bigIntegerOf(500)

        val error = request().tryCompileV0().unwrapError()
        error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
        error.reason shouldBe SolanaTransactionError.Reason.CONFIG

        // a limit carried by an instruction is enough
        val withInstructionLimit = request().apply { instruction(ComputeBudgetProgram.setComputeUnitLimit(200_000)) }
        withInstructionLimit.tryCompileV0().isFailure() shouldBe false
    }

    test("a v1 price with no limit cannot become a priority fee") {
        val error = SolanaTransactionRequest {
            feePayer(alice.publicKey)
            blockhash(blockhash)
            instruction(transfer)
            computeUnitPrice(1_000)
        }.tryCompileV1().unwrapError()
        error.shouldBeInstanceOf<SolanaTransactionError.InvalidMessage>()
        error.reason shouldBe SolanaTransactionError.Reason.CONFIG
    }
})
