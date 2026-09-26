package io.ethers.solana.program

import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.success
import io.ethers.crypto.Base58
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcClientConfig
import io.ethers.providers.RpcError
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.solana.codec.BorshReader
import io.ethers.solana.codec.BorshWriter
import io.ethers.solana.codec.SolanaAccountFactory
import io.ethers.solana.codec.SolanaCodec
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaDiscriminator
import io.ethers.solana.providers.AccountFilter
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.providers.SolanaSubscriptionDescriptor
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.RpcContext
import io.ethers.solana.types.rpc.TransactionSimulation
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonElement
import kotlin.io.encoding.Base64

private val PROGRAM = SolanaAddress(ByteArray(32) { 9 })
private val OTHER_PROGRAM = SolanaAddress(ByteArray(32) { 8 })
private val FIRST = SolanaAddress(ByteArray(32) { 1 })
private val SECOND = SolanaAddress(ByteArray(32) { 2 })
private val THIRD = SolanaAddress(ByteArray(32) { 3 })

private data class Vault(val amount: Long, val authority: SolanaAddress) {
    companion object : SolanaCodec<Vault> {
        val ACCOUNT = SolanaAccountFactory("Vault", SolanaDiscriminator.of(1, 2, 3, 4, 5, 6, 7, 8), this)

        override fun read(reader: BorshReader) = Vault(reader.readI64(), reader.readAddress())

        override fun write(writer: BorshWriter, value: Vault) {
            writer.writeI64(value.amount)
            writer.writeAddress(value.authority)
        }
    }
}

private val VAULT = Vault(5, FIRST)

private fun account(data: ByteArray, owner: SolanaAddress = PROGRAM) = """{"data":["${Base64.encode(data)}","base64"],"executable":false,"lamports":1,"owner":"$owner","rentEpoch":0,"space":${data.size}}"""

private fun contextual(value: String) = """{"context":{"slot":42},"value":$value}"""

/** Answers each request and subscription from [responses] by method, recording the params it was sent. */
private class FakeClient : JsonRpcClient {
    val responses = HashMap<String, String>()
    val params = HashMap<String, List<Any?>>()
    private val config = RpcClientConfig().subscriptionDescriptor(SolanaSubscriptionDescriptor)

    override suspend fun <T> request(method: String, params: Array<*>, resultDecoder: (JsonElement) -> T): Result<T, RpcError> {
        this.params[method] = params.toList()
        return success(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(responses.getValue(method))))
    }

    override suspend fun <T : Any> subscribe(params: Array<*>, resultDecoder: (JsonElement) -> T): Result<ChannelReceiver<T>, RpcError> {
        val descriptor = config.subscriptionDescriptor.resolve(params)
        this.params[descriptor.subscribeMethod] = descriptor.params.toList()
        val channel = QueueChannel.spscUnbounded<T>()
        channel.offer(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(responses.getValue(descriptor.subscribeMethod))))
        return success(channel)
    }

    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = error("Unexpected batch")
    override fun close() = Unit
}

class SolanaAccountTest : FunSpec({
    val client = FakeClient()
    val provider = SolanaProvider(client)

    context("fetch") {
        test("a fetched account is decoded, keeping its address, slot and everything the node returned") {
            client.responses["getAccountInfo"] = contextual(account(Vault.ACCOUNT.encode(VAULT) + ByteArray(8)))
            val fetched = Vault.ACCOUNT.fetch(provider, FIRST, PROGRAM).send().unwrap().shouldBeInstanceOf<SolanaAccount<Vault>>()
            fetched.exists shouldBe true
            fetched.address shouldBe FIRST
            fetched.context.slot.toString() shouldBe "42"
            fetched.data shouldBe VAULT
            // smart-cast: an existing account's info and data are non-null
            val info = fetched.info
            info.owner shouldBe PROGRAM
            info.lamports.toString() shouldBe "1"
            info.space.toString() shouldBe "56"
            // the undecoded data, allocation padding included
            info.data.size shouldBe 56
        }

        test("a missing account keeps its address and slot") {
            client.responses["getAccountInfo"] = contextual("null")
            val missing = Vault.ACCOUNT.fetch(provider, FIRST, PROGRAM).send().unwrap()
            missing shouldBe MissingSolanaAccount(FIRST, RpcContext(bigIntegerOf(42)))
            missing.exists shouldBe false
            missing.info.shouldBeNull()
            missing.data.shouldBeNull()
        }

        test("an account another program owns, or whose data does not decode, fails the request") {
            client.responses["getAccountInfo"] = contextual(account(Vault.ACCOUNT.encode(VAULT), owner = OTHER_PROGRAM))
            Vault.ACCOUNT.fetch(provider, FIRST, PROGRAM).send().unwrapError() shouldBe
                SolanaAccountError.Decode(SolanaDecodeError.UnexpectedOwner(FIRST, OTHER_PROGRAM, PROGRAM))

            client.responses["getAccountInfo"] = contextual(account(Vault.ACCOUNT.encode(VAULT).copyOf(12)))
            Vault.ACCOUNT.fetch(provider, FIRST, PROGRAM).send().unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
                .error.shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe SolanaDecodeError.Reason.TRUNCATED
        }

        test("several accounts keep the requested order, each missing one with its address and slot") {
            val data = account(Vault.ACCOUNT.encode(VAULT))
            client.responses["getMultipleAccounts"] = contextual("[$data, null, $data]")
            val accounts = Vault.ACCOUNT.fetch(provider, listOf(FIRST, SECOND, THIRD), PROGRAM).send().unwrap()
            accounts.map { it.address } shouldBe listOf(FIRST, SECOND, THIRD)
            accounts.map { it.exists } shouldBe listOf(true, false, true)
            accounts[1] shouldBe MissingSolanaAccount(SECOND, RpcContext(bigIntegerOf(42)))

            client.responses["getMultipleAccounts"] = contextual("[$data, ${account(ByteArray(8))}]")
            Vault.ACCOUNT.fetch(provider, listOf(FIRST, SECOND), PROGRAM).send().unwrapError()
                .shouldBeInstanceOf<SolanaAccountError.Decode>().error.shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
        }

        test("watching decodes notifications, reports closure, and skips ones that do not decode") {
            client.responses["accountSubscribe"] = contextual(account(Vault.ACCOUNT.encode(VAULT)))
            Vault.ACCOUNT.watch(provider, FIRST, PROGRAM).send().unwrap().receive()!!.data shouldBe VAULT

            client.responses["accountSubscribe"] = contextual("null")
            Vault.ACCOUNT.watch(provider, FIRST, PROGRAM).send().unwrap().receive()!!.exists shouldBe false

            client.responses["accountSubscribe"] = contextual(account(ByteArray(3)))
            Vault.ACCOUNT.watch(provider, FIRST, PROGRAM).send().unwrap().poll().shouldBeNull()
        }
    }

    context("filter") {
        test("accounts are selected by discriminator, then by each added condition") {
            val filter = SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).memcmp(16, FIRST.asByteArray()).dataSize(48)
            filter.accountFilters shouldBe listOf(
                AccountFilter.Memcmp(0, Base58.encode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))),
                AccountFilter.Memcmp(16, FIRST.toBase58()),
                AccountFilter.DataSize(48),
            )
        }

        test("a query decodes the matching accounts, skips ones that do not decode, and keeps its slot") {
            val good = account(Vault.ACCOUNT.encode(VAULT))
            client.responses["getProgramAccounts"] = contextual(
                """[{"pubkey":"$FIRST","account":$good},{"pubkey":"$SECOND","account":${account(ByteArray(3))}}]""",
            )
            val accounts = SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).query().send().unwrap()
            accounts.map { it.address } shouldBe listOf(FIRST)
            accounts.single().data shouldBe VAULT
            // query results always exist, so info and data are non-null without a check
            accounts.single().data.amount shouldBe 5L
            accounts.single().info.owner shouldBe PROGRAM
            accounts.context.slot.toString() shouldBe "42"
            client.params.getValue("getProgramAccounts").first() shouldBe PROGRAM.toString()
        }

        test("an empty query still reports the slot it was answered at") {
            client.responses["getProgramAccounts"] = contextual("[]")
            val accounts = SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).query().send().unwrap()
            accounts.isEmpty() shouldBe true
            accounts.context.slot.toString() shouldBe "42"
            accounts shouldBe emptyList()
        }

        test("an alternate deployment selects and checks against its own program id") {
            client.responses["getProgramAccounts"] = contextual("""[{"pubkey":"$FIRST","account":${account(Vault.ACCOUNT.encode(VAULT), owner = OTHER_PROGRAM)}}]""")
            SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).program(OTHER_PROGRAM).query().send().unwrap().single().data shouldBe VAULT
            client.params.getValue("getProgramAccounts").first() shouldBe OTHER_PROGRAM.toString()
        }

        test("a subscription decodes each notification with its slot") {
            client.responses["programSubscribe"] = contextual("""{"pubkey":"$SECOND","account":${account(Vault.ACCOUNT.encode(VAULT))}}""")
            val notification = SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).subscribe().send().unwrap().receive()!!
            notification.address shouldBe SECOND
            notification.data.authority shouldBe FIRST
            notification.context.slot.toString() shouldBe "42"
            client.params.getValue("programSubscribe").toString() shouldContain "\"offset\":0"
        }
    }

    test("field filters count their offset from the end of the discriminator") {
        Vault.ACCOUNT.fieldFilter(8) { writeAddress(FIRST) } shouldBe AccountFilter.Memcmp(16, FIRST.toBase58())
    }

    test("simulation accounts decode in the order they were requested, as of the simulation's slot") {
        val simulation = Kotlinx.DEFAULT.decodeFromString<ContextValue<TransactionSimulation>>(
            contextual("""{"err":null,"accounts":[null,${account(Vault.ACCOUNT.encode(VAULT))}]}"""),
        )
        val decoded = Vault.ACCOUNT.decodeAccounts(simulation, listOf(FIRST, SECOND), PROGRAM)!!
        decoded[0].unwrap() shouldBe MissingSolanaAccount(FIRST, RpcContext(bigIntegerOf(42)))
        decoded[1].unwrap().data shouldBe VAULT

        shouldThrow<IllegalArgumentException> { Vault.ACCOUNT.decodeAccounts(simulation, listOf(FIRST), PROGRAM) }
        Vault.ACCOUNT.decodeAccounts(ContextValue(RpcContext(bigIntegerOf(1)), TransactionSimulation(err = null)), listOf(FIRST), PROGRAM).shouldBeNull()
    }
})
