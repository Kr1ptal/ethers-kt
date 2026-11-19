package io.ethers.solana.idl.test

import io.channels.core.ChannelReceiver
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.program.SolanaAccountResolutionError
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.DeserializationStrategy
import kotlin.io.encoding.Base64

private fun address(byte: Int) = SolanaAddress(ByteArray(32) { byte.toByte() })

private val USER = address(1)
private val AUTHORITY = address(2)
private val MINT = address(3)
private val ADMIN = address(4)
private val SETTLEMENT_POOL = address(5)
private val SETTLEMENT_AUTHORITY = address(6)
private val OTHER_PROGRAM = address(8)
private val METADATA_PROGRAM = SolanaAddress("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s")
private val SYSTEM_PROGRAM = SolanaAddress("11111111111111111111111111111111")

private val POOL_ID = bigIntegerOf(42)
private val ARGS = Resolution.DepositArgs(POOL_ID, bigIntegerOf(1_000))
private val POOL_ADDRESS = Resolution.DepositAccounts.findPool(POOL_ID).address
private val CONFIG_ADDRESS = Resolution.DepositAccounts.findConfig().address

private val POOL = Resolution.Pool(AUTHORITY, MINT, 254)
private val CONFIG = Resolution.Config(MINT, 30, ADMIN)

private fun account(data: ByteArray, owner: SolanaAddress = Resolution.PROGRAM_ID) = """{"data":["${Base64.encode(data)}","base64"],"executable":false,"lamports":1,"owner":"$owner","rentEpoch":0,"space":${data.size}}"""

/** Answers `getMultipleAccounts` from [accounts] by address, recording the addresses and slot bound of each request. */
private class AccountsClient : JsonRpcClient {
    val accounts = HashMap<SolanaAddress, String>()
    val requests = ArrayList<List<SolanaAddress>>()
    val minContextSlots = ArrayList<String?>()
    var slot = 10
    var error: RpcError? = null

    override suspend fun <T> request(method: String, params: Array<*>, resultDeserializer: DeserializationStrategy<T>): Result<T, RpcError> {
        check(method == "getMultipleAccounts") { "Unexpected $method" }
        error?.let { return failure(it) }
        @Suppress("UNCHECKED_CAST")
        val addresses = (params[0] as List<String>).map(::SolanaAddress)
        requests.add(addresses)
        minContextSlots.add((params[1] as Map<*, *>)["minContextSlot"]?.toString())
        val values = addresses.joinToString(",") { accounts[it] ?: "null" }
        val response = """{"context":{"slot":${slot++}},"value":[$values]}"""
        return success(Kotlinx.DEFAULT.decodeFromString(resultDeserializer, response))
    }

    override suspend fun <T : Any> subscribe(params: Array<*>, resultDeserializer: DeserializationStrategy<T>): Result<ChannelReceiver<T>, RpcError> = error("Unexpected subscription")
    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = error("Unexpected batch")
    override fun close() = Unit
}

private val PAYER = address(9)
private val INIT_ARGS = Resolution.InitArgs(POOL_ID)

private const val NAME = "main"
private val CHAIN_ARGS = Resolution.ChainArgs(POOL_ID, NAME)
private val LEDGER = address(10)

class ResolutionBindingsTest : FunSpec({
    fun client() = AccountsClient().apply {
        accounts[POOL_ADDRESS] = account(Resolution.Pool.ACCOUNT.encode(POOL))
        accounts[CONFIG_ADDRESS] = account(Resolution.Config.ACCOUNT.encode(CONFIG))
        accounts[SETTLEMENT_POOL] = account(Resolution.Pool.ACCOUNT.encode(Resolution.Pool(SETTLEMENT_AUTHORITY, MINT, 1)))
    }

    /** Resolve deposit with everything that can be left out left out. */
    suspend fun deposit(client: AccountsClient, programId: SolanaAddress = Resolution.PROGRAM_ID) = Resolution.resolveDeposit(SolanaProvider(client), user = USER, settlementPool = SETTLEMENT_POOL, args = ARGS, programId = programId).send()

    context("deriving builder, without on-chain data") {
        val config = Resolution.InitAccounts.findConfig().address
        val pool = Resolution.InitAccounts.findPool(POOL_ID).address
        val accounts = Resolution.InitAccounts(PAYER, config, pool, Resolution.InitAccounts.findVault(pool, PAYER).address)

        test("constant, argument and account address seeds and fixed addresses are derived when left out, and visible in its accounts") {
            accounts.systemProgram shouldBe SYSTEM_PROGRAM
            val built = Resolution.init(payer = PAYER, args = INIT_ARGS)
            built shouldBe Resolution.init(accounts, INIT_ARGS)
            built.accounts shouldBe accounts
            built.args shouldBe INIT_ARGS
            built.programId shouldBe Resolution.PROGRAM_ID
        }

        test("the builder taking the accounts class encodes exactly as before: the descriptor's data and the accounts' metas") {
            val remaining = listOf(AccountMeta(address(11), false, true))
            val built = Resolution.init(accounts, INIT_ARGS, remaining)
            val plain = Instruction(
                Resolution.PROGRAM_ID,
                accounts.toAccountMetas(Resolution.PROGRAM_ID) + remaining,
                Resolution.INIT.encodeData(INIT_ARGS),
            )
            // equal by wire content, either way round, with the same hash
            built shouldBe plain
            plain shouldBe built
            built.hashCode() shouldBe plain.hashCode()
            built.keys shouldBe plain.keys
            built.data shouldBe plain.data
            built.remainingAccounts shouldBe remaining
            built.toString() shouldBe "Init(accounts=$accounts, args=$INIT_ARGS, remainingAccounts=$remaining, programId=${Resolution.PROGRAM_ID})"
        }

        test("decoding an instruction that was built gives back the same typed instruction, keeping its wire fields") {
            val built = Resolution.init(payer = PAYER, args = INIT_ARGS, remainingAccounts = listOf(AccountMeta(address(11))))
            Resolution.Instruction.Init.decode(built).unwrap() shouldBe built
            Resolution.decodeInstruction(built).unwrap() shouldBe built
            // and building from what was decoded encodes the same instruction
            val decoded = Resolution.Instruction.Init.decode(built).unwrap()
            Resolution.init(decoded.accounts, decoded.args, decoded.remainingAccounts, decoded.programId) shouldBe built

            // a plain instruction decodes to a typed one with its exact keys and data
            val plain = Instruction(built.programId, built.keys, built.data)
            val typed = Resolution.decodeInstruction(plain).unwrap().shouldBeInstanceOf<Resolution.Instruction.Init>()
            typed.keys shouldBe plain.keys
            typed.data shouldBe plain.data
            typed.accounts shouldBe built.accounts
        }

        test("a typed instruction compiles into every transaction kind exactly as the plain instruction does") {
            val blockhash = SolanaBlockhash(ByteArray(32) { 1 })
            val typed = Resolution.init(payer = PAYER, args = INIT_ARGS)
            val plain = Instruction(typed.programId, typed.keys, typed.data)
            val transfer = SystemProgram.transfer(PAYER, address(15), 1_000L)

            fun legacy(vararg instructions: Instruction) = SolanaTxLegacy.compile(PAYER, blockhash, instructions.toList()).unwrap().serializeMessage().toList()
            fun v0(vararg instructions: Instruction) = SolanaTxV0.compile(PAYER, blockhash, instructions.toList()).unwrap().serializeMessage().toList()
            fun v1(vararg instructions: Instruction) = SolanaTxV1.compile(PAYER, blockhash, instructions.toList(), SolanaTransactionConfig()).unwrap().serializeMessage().toList()
            fun request(vararg instructions: Instruction) = SolanaTransactionRequest().feePayer(PAYER).blockhash(blockhash).instructions(instructions.toList())

            legacy(typed, transfer) shouldBe legacy(plain, transfer)
            v0(typed, transfer) shouldBe v0(plain, transfer)
            v1(typed, transfer) shouldBe v1(plain, transfer)
            SolanaTxV0.compile(PAYER, blockhash, typed).unwrap().serializeMessage().toList() shouldBe v0(plain)
            request(typed, transfer).compileV0().unwrap().serializeMessage().toList() shouldBe v0(plain, transfer)
            SolanaTransactionRequest().feePayer(PAYER).blockhash(blockhash).instruction(typed).compileLegacy().unwrap().serializeMessage().toList() shouldBe legacy(plain)
        }

        test("every builder returns the same typed instruction, which a transaction takes as it is") {
            val offline: Resolution.Instruction.Deposit = Resolution.deposit(
                user = USER,
                authority = AUTHORITY,
                mint = MINT,
                position = address(12),
                feeReceiver = address(13),
                settlementPool = SETTLEMENT_POOL,
                settlementAuthority = SETTLEMENT_AUTHORITY,
                args = ARGS,
            )
            val explicit: Resolution.Instruction.Deposit = Resolution.deposit(offline.accounts, ARGS)
            val resolved: Resolution.Instruction.Deposit = Resolution.resolveDeposit(
                SolanaProvider(AccountsClient()),
                user = USER,
                authority = AUTHORITY,
                mint = MINT,
                position = address(12),
                feeReceiver = address(13),
                settlementPool = SETTLEMENT_POOL,
                settlementAuthority = SETTLEMENT_AUTHORITY,
                args = ARGS,
            ).send().unwrap()
            offline shouldBe explicit
            resolved shouldBe explicit

            val blockhash = SolanaBlockhash(ByteArray(32) { 1 })
            val message = SolanaTxV0.compile(USER, blockhash, listOf(resolved, Resolution.init(payer = USER, args = INIT_ARGS))).unwrap()
            message.instructions.size shouldBe 2
            SolanaTxV0.compile(USER, blockhash, resolved).unwrap().instructions.single().data shouldBe resolved.data
        }

        test("an account given is used as given, and accounts derived from it follow it") {
            val other = address(7)
            val instruction = Resolution.init(payer = PAYER, pool = other, systemProgram = other, args = INIT_ARGS)
            instruction shouldBe Resolution.init(accounts.copy(pool = other, vault = Resolution.InitAccounts.findVault(other, PAYER).address, systemProgram = other), INIT_ARGS)
        }

        test("giving every account builds the same instruction, byte for byte, as the builder taking the accounts class") {
            val instruction = Resolution.init(
                payer = PAYER,
                config = accounts.config,
                pool = accounts.pool,
                vault = accounts.vault,
                systemProgram = SYSTEM_PROGRAM,
                args = INIT_ARGS,
                remainingAccounts = listOf(AccountMeta(address(11), false, true)),
            )
            val expected = Resolution.init(accounts, INIT_ARGS, listOf(AccountMeta(address(11), false, true)))
            instruction shouldBe expected
            instruction.data.asByteArray().toList() shouldBe expected.data.asByteArray().toList()
            instruction.keys shouldBe expected.keys
        }

        test("an alternate program id derives under it") {
            val program = OTHER_PROGRAM
            val otherPool = Resolution.InitAccounts.findPool(POOL_ID, program).address
            Resolution.init(payer = PAYER, args = INIT_ARGS, programId = program) shouldBe Resolution.init(
                Resolution.InitAccounts(PAYER, Resolution.InitAccounts.findConfig(program).address, otherPool, Resolution.InitAccounts.findVault(otherPool, PAYER, program).address),
                INIT_ARGS,
                programId = program,
            )
        }

        test("accounts derived from on-chain data, and PDAs in a cycle, are required; the rest are still derived") {
            val instruction = Resolution.deposit(
                user = USER,
                authority = AUTHORITY,
                mint = MINT,
                position = address(12),
                feeReceiver = address(13),
                settlementPool = SETTLEMENT_POOL,
                settlementAuthority = SETTLEMENT_AUTHORITY,
                args = ARGS,
            )
            val decoded = instruction.accounts
            decoded.pool shouldBe POOL_ADDRESS
            decoded.vault shouldBe Resolution.DepositAccounts.findVault(POOL_ADDRESS, AUTHORITY).address
            decoded.metadata shouldBe Resolution.DepositAccounts.findMetadata(MINT, METADATA_PROGRAM).address
            decoded.referrer.shouldBeNull()

            val left = address(3)
            val right = address(4)
            val tangle = Resolution.tangle(left = left, right = right, mint = MINT).accounts
            tangle.anchor shouldBe Resolution.TangleAccounts.findAnchor(left).address
            tangle.config shouldBe Resolution.TangleAccounts.findConfig().address
        }
    }

    context("resolving builder, with an api") {
        test("one round: PDAs, relations, account data seeds and fixed addresses resolve, fetching what they read in one request") {
            val client = client()
            val built = deposit(client).unwrap()
            val accounts = built.accounts

            accounts.user shouldBe USER
            accounts.pool shouldBe POOL_ADDRESS
            accounts.config shouldBe CONFIG_ADDRESS
            // relations: pool.authority, and pool.mint and config.mint, which agree
            accounts.authority shouldBe AUTHORITY
            accounts.mint shouldBe MINT
            // PDAs over resolved accounts and over fields of fetched account data
            accounts.vault shouldBe Resolution.DepositAccounts.findVault(POOL_ADDRESS, AUTHORITY).address
            accounts.position shouldBe Resolution.DepositAccounts.findPosition(MINT, USER).address
            accounts.feeReceiver shouldBe Resolution.DepositAccounts.findFeeReceiver(30).address
            // derived under the program another account names
            accounts.metadata shouldBe Resolution.DepositAccounts.findMetadata(MINT, METADATA_PROGRAM).address
            accounts.metadataProgram shouldBe METADATA_PROGRAM
            accounts.systemProgram shouldBe SYSTEM_PROGRAM
            // an optional account is never derived
            accounts.referrer.shouldBeNull()
            // a group's relation follows the account in its own group
            accounts.settlement shouldBe Resolution.DepositAccounts.Settlement(SETTLEMENT_POOL, SETTLEMENT_AUTHORITY)

            client.requests shouldBe listOf(listOf(POOL_ADDRESS, CONFIG_ADDRESS, SETTLEMENT_POOL))
            client.minContextSlots shouldBe listOf(null)
            built shouldBe Resolution.deposit(accounts, ARGS)
        }

        test("the resolved accounts build further instructions without fetching again") {
            val client = client()
            val built = deposit(client).unwrap()
            val requests = client.requests.size

            val next = Resolution.DepositArgs(POOL_ID, bigIntegerOf(5))
            val reused = Resolution.deposit(built.accounts, next)
            reused.accounts shouldBe built.accounts
            reused.keys shouldBe built.keys
            reused.data shouldBe Resolution.DEPOSIT.encodeData(next).let { SolanaBytes.fromBytes(it) }
            // the accounts of an offline-built instruction are reused just the same
            Resolution.deposit(Resolution.deposit(reused.accounts, ARGS).accounts, next) shouldBe reused
            client.requests.size shouldBe requests
        }

        test("no round: accounts given are used as given, and nothing is fetched when nothing left out reads data") {
            val none = AccountsClient()
            val given = address(9)
            val built = Resolution.resolveDeposit(
                SolanaProvider(none),
                user = USER,
                authority = given,
                mint = given,
                position = given,
                feeReceiver = given,
                settlementPool = given,
                settlementAuthority = given,
                args = ARGS,
            ).send().unwrap()
            built.accounts.vault shouldBe Resolution.DepositAccounts.findVault(POOL_ADDRESS, given).address
            built.accounts.metadata shouldBe Resolution.DepositAccounts.findMetadata(given, METADATA_PROGRAM).address
            none.requests shouldBe emptyList()
        }

        test("two rounds: an account related to a resolved account is fetched in the next round, reading no older state") {
            val client = AccountsClient()
            client.accounts[POOL_ADDRESS] = account(Resolution.Pool.ACCOUNT.encode(POOL))
            client.accounts[AUTHORITY] = account(Resolution.Profile.ACCOUNT.encode(Resolution.Profile(LEDGER)))
            val sponsor = address(14)
            val built = Resolution.resolveChain(SolanaProvider(client), sponsor = sponsor, args = CHAIN_ARGS).send().unwrap()
            built.accounts shouldBe Resolution.ChainAccounts(
                pool = POOL_ADDRESS,
                authority = AUTHORITY,
                ledger = LEDGER,
                named = Resolution.ChainAccounts.findNamed(NAME).address,
                sponsor = sponsor,
                sponsored = Resolution.ChainAccounts.findSponsored(sponsor).address,
            )
            client.requests shouldBe listOf(listOf(POOL_ADDRESS), listOf(AUTHORITY))
            client.minContextSlots shouldBe listOf(null, "10")
        }

        test("an alternate program id derives PDAs under it and expects it to own the accounts it reads") {
            val program = OTHER_PROGRAM
            val pool = Resolution.DepositAccounts.findPool(POOL_ID, program).address
            val config = Resolution.DepositAccounts.findConfig(program).address
            val client = AccountsClient().apply {
                accounts[pool] = account(Resolution.Pool.ACCOUNT.encode(POOL), owner = program)
                accounts[config] = account(Resolution.Config.ACCOUNT.encode(CONFIG), owner = program)
                accounts[SETTLEMENT_POOL] = account(Resolution.Pool.ACCOUNT.encode(POOL), owner = program)
            }
            val built = deposit(client, program).unwrap()
            built.accounts.pool shouldBe pool
            built.accounts.vault shouldBe Resolution.DepositAccounts.findVault(pool, AUTHORITY, program).address
            built.programId shouldBe program
        }
    }

    context("resolving builder errors") {
        test("an account whose only derivation needs an absent optional account is missing, naming it") {
            val client = AccountsClient()
            client.accounts[POOL_ADDRESS] = account(Resolution.Pool.ACCOUNT.encode(POOL))
            Resolution.resolveChain(SolanaProvider(client), args = CHAIN_ARGS).send().unwrapError() shouldBe SolanaAccountResolutionError.MissingAccount(
                "sponsored",
                "it was not provided, and every way to derive it depends on an optional account that was not provided: 'sponsor'",
            )
        }

        test("a seed that cannot derive an address is invalid") {
            val long = Resolution.ChainArgs(POOL_ID, "x".repeat(40))
            val error = Resolution.resolveChain(SolanaProvider(AccountsClient()), sponsor = address(14), args = long).send().unwrapError()
                .shouldBeInstanceOf<SolanaAccountResolutionError.InvalidSeeds>()
            error.account shouldBe "named"
        }

        test("a relation source or seed account that does not exist on chain fails with its path and slot") {
            val client = client()
            client.accounts.remove(POOL_ADDRESS)
            val error = deposit(client).unwrapError().shouldBeInstanceOf<SolanaAccountResolutionError.AccountNotFound>()
            error.account shouldBe "pool"
            error.address shouldBe POOL_ADDRESS
            error.context.slot.toString() shouldBe "10"
            // the first account in instruction order that needs it
            error.requiredBy shouldBe "authority"
        }

        test("an account another program owns is rejected before its data is read") {
            val client = client()
            client.accounts[POOL_ADDRESS] = account(Resolution.Pool.ACCOUNT.encode(POOL), owner = OTHER_PROGRAM)
            deposit(client).unwrapError() shouldBe
                SolanaAccountResolutionError.OwnerMismatch("pool", POOL_ADDRESS, OTHER_PROGRAM, Resolution.PROGRAM_ID, "authority")
        }

        test("an account whose discriminator is not one the relation or seed expects is a type mismatch") {
            val client = client()
            // a Config where the authority relation reads a Pool
            client.accounts[POOL_ADDRESS] = account(Resolution.Config.ACCOUNT.encode(CONFIG))
            deposit(client).unwrapError() shouldBe SolanaAccountResolutionError.AccountTypeMismatch("pool", POOL_ADDRESS, listOf("Pool"), "authority")
        }

        test("an account with the right discriminator whose data does not decode is undecodable") {
            val client = client()
            client.accounts[POOL_ADDRESS] = account(Resolution.Pool.ACCOUNT.encode(POOL).copyOf(20))
            val error = deposit(client).unwrapError().shouldBeInstanceOf<SolanaAccountResolutionError.Undecodable>()
            error.account shouldBe "pool"
            error.type shouldBe "Pool"
            error.error.shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe SolanaDecodeError.Reason.TRUNCATED
        }

        test("relation sources that name different addresses are ambiguous") {
            val client = client()
            val other = address(7)
            client.accounts[CONFIG_ADDRESS] = account(Resolution.Config.ACCOUNT.encode(CONFIG.copy(mint = other)))
            deposit(client).unwrapError() shouldBe SolanaAccountResolutionError.AmbiguousRelation("mint", mapOf("pool" to MINT, "config" to other))
        }

        test("PDAs that depend on each other are a cycle, reported with its path; giving one breaks it") {
            val client = AccountsClient()
            val config = Resolution.TangleAccounts.findConfig().address
            client.accounts[config] = account(Resolution.Config.ACCOUNT.encode(CONFIG))
            Resolution.resolveTangle(SolanaProvider(client)).send().unwrapError() shouldBe
                SolanaAccountResolutionError.DependencyCycle(listOf("left", "right", "left"))

            val left = address(3)
            val built = Resolution.resolveTangle(SolanaProvider(client), left = left).send().unwrap()
            built.accounts.right shouldBe Resolution.TangleAccounts.findRight(left).address
            built.accounts.anchor shouldBe Resolution.TangleAccounts.findAnchor(left).address
            built.accounts.mint shouldBe MINT
        }

        test("a failed fetch fails resolution") {
            val client = client()
            client.error = RpcError(-32000, "node down")
            deposit(client).unwrapError().shouldBeInstanceOf<SolanaAccountResolutionError.Rpc>().rpcError.message shouldBe "node down"
        }
    }

    context("opaque") {
        test("a seed whose encoding the IDL does not determine, and a relation no account type can follow, stay required") {
            val user = address(1)
            val flagged = address(2)
            val orphan = address(3)
            val sponsored = address(4)
            val instruction = Resolution.opaque(user = user, flagged = flagged, orphan = orphan, sponsored = sponsored, args = Resolution.OpaqueArgs(true))
            instruction shouldBe Resolution.opaque(Resolution.OpaqueAccounts(user, flagged, orphan, null, sponsored), Resolution.OpaqueArgs(true))
        }
    }
})
