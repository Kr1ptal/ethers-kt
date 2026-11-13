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
import io.ethers.solana.program.SolanaAccountResolutionError
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.SolanaAddress
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonElement
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

    override suspend fun <T> request(method: String, params: Array<*>, resultDecoder: (JsonElement) -> T): Result<T, RpcError> {
        check(method == "getMultipleAccounts") { "Unexpected $method" }
        error?.let { return failure(it) }
        @Suppress("UNCHECKED_CAST")
        val addresses = (params[0] as List<String>).map(::SolanaAddress)
        requests.add(addresses)
        minContextSlots.add((params[1] as Map<*, *>)["minContextSlot"]?.toString())
        val values = addresses.joinToString(",") { accounts[it] ?: "null" }
        val response = """{"context":{"slot":${slot++}},"value":[$values]}"""
        return success(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(response)))
    }

    override suspend fun <T : Any> subscribe(params: Array<*>, resultDecoder: (JsonElement) -> T): Result<ChannelReceiver<T>, RpcError> = error("Unexpected subscription")
    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = error("Unexpected batch")
    override fun close() = Unit
}

class ResolutionBindingsTest : FunSpec({
    fun client() = AccountsClient().apply {
        accounts[POOL_ADDRESS] = account(Resolution.Pool.ACCOUNT.encode(POOL))
        accounts[CONFIG_ADDRESS] = account(Resolution.Config.ACCOUNT.encode(CONFIG))
        accounts[SETTLEMENT_POOL] = account(Resolution.Pool.ACCOUNT.encode(Resolution.Pool(SETTLEMENT_AUTHORITY, MINT, 1)))
    }

    val partial = Resolution.DepositAccounts.Partial(user = USER, settlement = Resolution.DepositAccounts.Settlement.Partial(pool = SETTLEMENT_POOL))

    context("deposit") {
        test("PDAs, relations, account data seeds and fixed addresses resolve, fetching each round's accounts in one request") {
            val client = client()
            val accounts = partial.resolve(SolanaProvider(client), ARGS).send().unwrap()

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

            // every account whose data is read is fetched once, all in the first round
            client.requests shouldBe listOf(listOf(POOL_ADDRESS, CONFIG_ADDRESS, SETTLEMENT_POOL))
            client.minContextSlots shouldBe listOf(null)

            // the resolved accounts build the same instruction as the pure builder
            Resolution.deposit(accounts, ARGS).keys.size shouldBe 14
        }

        test("provided accounts are used as given, without fetching what only their derivation reads") {
            val client = client()
            val given = address(9)
            val accounts = partial.copy(authority = given, mint = given, feeReceiver = given, position = given)
                .resolve(SolanaProvider(client), ARGS).send().unwrap()
            accounts.authority shouldBe given
            accounts.mint shouldBe given
            accounts.vault shouldBe Resolution.DepositAccounts.findVault(POOL_ADDRESS, given).address
            accounts.metadata shouldBe Resolution.DepositAccounts.findMetadata(given, METADATA_PROGRAM).address
            // neither pool nor config data is needed any more
            client.requests shouldBe listOf(listOf(SETTLEMENT_POOL))

            val none = AccountsClient()
            partial.copy(authority = given, mint = given, feeReceiver = given, position = given, settlement = Resolution.DepositAccounts.Settlement.Partial(given, given))
                .resolve(SolanaProvider(none), ARGS).send().unwrap().pool shouldBe POOL_ADDRESS
            none.requests shouldBe emptyList()
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
            val accounts = partial.resolve(SolanaProvider(client), ARGS, program).send().unwrap()
            accounts.pool shouldBe pool
            accounts.vault shouldBe Resolution.DepositAccounts.findVault(pool, AUTHORITY, program).address
        }

        test("a required account that is neither provided nor derivable is missing") {
            val error = Resolution.DepositAccounts.Partial().resolve(SolanaProvider(client()), ARGS).send().unwrapError()
            error shouldBe SolanaAccountResolutionError.MissingAccount("user", "it was not provided, and the IDL gives no way to derive it")
        }

        test("a relation source or seed account that does not exist on chain fails with its path and slot") {
            val client = client()
            client.accounts.remove(POOL_ADDRESS)
            val error = partial.resolve(SolanaProvider(client), ARGS).send().unwrapError()
                .shouldBeInstanceOf<SolanaAccountResolutionError.AccountNotFound>()
            error.account shouldBe "pool"
            error.address shouldBe POOL_ADDRESS
            error.context.slot.toString() shouldBe "10"
            // the first account in instruction order that needs it
            error.requiredBy shouldBe "authority"
        }

        test("an account another program owns is rejected before its data is read") {
            val client = client()
            client.accounts[POOL_ADDRESS] = account(Resolution.Pool.ACCOUNT.encode(POOL), owner = OTHER_PROGRAM)
            partial.resolve(SolanaProvider(client), ARGS).send().unwrapError() shouldBe
                SolanaAccountResolutionError.OwnerMismatch("pool", POOL_ADDRESS, OTHER_PROGRAM, Resolution.PROGRAM_ID, "authority")
        }

        test("an account whose discriminator is not one the relation or seed expects is a type mismatch") {
            val client = client()
            // a Config where the authority relation reads a Pool
            client.accounts[POOL_ADDRESS] = account(Resolution.Config.ACCOUNT.encode(CONFIG))
            partial.resolve(SolanaProvider(client), ARGS).send().unwrapError() shouldBe
                SolanaAccountResolutionError.AccountTypeMismatch("pool", POOL_ADDRESS, listOf("Pool"), "authority")

            // the mint relation accepts either type, so a Config at pool still names the mint, and then the position seed
            // reads pool as a Pool
            val positioned = client()
            positioned.accounts[POOL_ADDRESS] = account(Resolution.Config.ACCOUNT.encode(CONFIG))
            partial.copy(authority = AUTHORITY).resolve(SolanaProvider(positioned), ARGS).send().unwrapError() shouldBe
                SolanaAccountResolutionError.AccountTypeMismatch("pool", POOL_ADDRESS, listOf("Pool"), "position")
        }

        test("an account with the right discriminator whose data does not decode is undecodable") {
            val client = client()
            client.accounts[POOL_ADDRESS] = account(Resolution.Pool.ACCOUNT.encode(POOL).copyOf(20))
            val error = partial.resolve(SolanaProvider(client), ARGS).send().unwrapError()
                .shouldBeInstanceOf<SolanaAccountResolutionError.Undecodable>()
            error.account shouldBe "pool"
            error.type shouldBe "Pool"
            error.error.shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe SolanaDecodeError.Reason.TRUNCATED
        }

        test("relation sources that name different addresses are ambiguous") {
            val client = client()
            val other = address(7)
            client.accounts[CONFIG_ADDRESS] = account(Resolution.Config.ACCOUNT.encode(CONFIG.copy(mint = other)))
            partial.resolve(SolanaProvider(client), ARGS).send().unwrapError() shouldBe
                SolanaAccountResolutionError.AmbiguousRelation("mint", mapOf("pool" to MINT, "config" to other))
        }

        test("a failed fetch fails resolution") {
            val client = client()
            client.error = RpcError(-32000, "node down")
            partial.resolve(SolanaProvider(client), ARGS).send().unwrapError()
                .shouldBeInstanceOf<SolanaAccountResolutionError.Rpc>().rpcError.message shouldBe "node down"
        }
    }

    context("tangle") {
        test("PDAs that depend on each other are a cycle, reported with its path") {
            Resolution.TangleAccounts.Partial().resolve(SolanaProvider(AccountsClient())).send().unwrapError() shouldBe
                SolanaAccountResolutionError.DependencyCycle(listOf("left", "right", "left"))
        }

        test("providing one account of the cycle breaks it") {
            val left = address(3)
            val accounts = Resolution.TangleAccounts.Partial(left = left).resolve(SolanaProvider(AccountsClient())).send().unwrap()
            accounts.right shouldBe Resolution.TangleAccounts.findRight(left).address
            accounts.anchor shouldBe Resolution.TangleAccounts.findAnchor(left).address
        }
    }

    context("opaque") {
        val user = address(1)
        val args = Resolution.OpaqueArgs(true)

        test("a seed whose encoding the IDL does not determine, and a relation no account type can follow, are not derived") {
            Resolution.OpaqueAccounts.Partial(user = user).resolve(SolanaProvider(AccountsClient())).send().unwrapError() shouldBe
                SolanaAccountResolutionError.MissingAccount("flagged", "it was not provided, and the IDL gives no way to derive it")
            Resolution.OpaqueAccounts.Partial(user = user, flagged = address(2)).resolve(SolanaProvider(AccountsClient())).send().unwrapError() shouldBe
                SolanaAccountResolutionError.MissingAccount("orphan", "it was not provided, and the IDL gives no way to derive it")
        }

        test("a derivation that needs an absent optional account is missing, naming it; providing it derives the account") {
            val partial = Resolution.OpaqueAccounts.Partial(user = user, flagged = address(2), orphan = address(3))
            partial.resolve(SolanaProvider(AccountsClient())).send().unwrapError() shouldBe SolanaAccountResolutionError.MissingAccount(
                "sponsored",
                "it was not provided, and every way to derive it depends on an optional account that was not provided: 'sponsor'",
            )
            val sponsor = address(4)
            val accounts = partial.copy(sponsor = sponsor).resolve(SolanaProvider(AccountsClient())).send().unwrap()
            accounts.sponsor shouldBe sponsor
            accounts.sponsored shouldBe Resolution.OpaqueAccounts.findSponsored(sponsor).address
            // arguments are not taken when no derivation reads them
            Resolution.opaque(accounts, args).data.size shouldBe 9
        }
    }
})
