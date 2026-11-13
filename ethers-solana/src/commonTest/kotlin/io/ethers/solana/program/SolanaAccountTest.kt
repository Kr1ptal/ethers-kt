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
import io.ethers.solana.codec.SolanaAccountArrayField
import io.ethers.solana.codec.SolanaAccountFactory
import io.ethers.solana.codec.SolanaAccountField
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
    var notifications: List<String>? = null
    private val config = RpcClientConfig().subscriptionDescriptor(SolanaSubscriptionDescriptor)

    override suspend fun <T> request(method: String, params: Array<*>, resultDecoder: (JsonElement) -> T): Result<T, RpcError> {
        this.params[method] = params.toList()
        return success(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(responses.getValue(method))))
    }

    override suspend fun <T : Any> subscribe(params: Array<*>, resultDecoder: (JsonElement) -> T): Result<ChannelReceiver<T>, RpcError> {
        val descriptor = config.subscriptionDescriptor.resolve(params)
        this.params[descriptor.subscribeMethod] = descriptor.params.toList()
        val channel = QueueChannel.spscUnbounded<T>()
        for (notification in notifications ?: listOf(responses.getValue(descriptor.subscribeMethod))) {
            channel.offer(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(notification)))
        }
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
            val missing = Vault.ACCOUNT.fetch(provider, FIRST, PROGRAM).send().unwrapError()
            missing shouldBe SolanaAccountError.Missing(FIRST, RpcContext(bigIntegerOf(42)))
        }

        test("an account another program owns, or whose data does not decode, fails the request") {
            client.responses["getAccountInfo"] = contextual(account(Vault.ACCOUNT.encode(VAULT), owner = OTHER_PROGRAM))
            val error = Vault.ACCOUNT.fetch(provider, FIRST, PROGRAM).send().unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
            error.address shouldBe FIRST
            error.context.slot.toString() shouldBe "42"
            error.info.owner shouldBe OTHER_PROGRAM
            error.info.data.asByteArray().toList() shouldBe Vault.ACCOUNT.encode(VAULT).toList()
            error.error shouldBe SolanaDecodeError.UnexpectedOwner(FIRST, OTHER_PROGRAM, PROGRAM)

            client.responses["getAccountInfo"] = contextual(account(Vault.ACCOUNT.encode(VAULT).copyOf(12)))
            Vault.ACCOUNT.fetch(provider, FIRST, PROGRAM).send().unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
                .error.shouldBeInstanceOf<SolanaDecodeError.Malformed>().reason shouldBe SolanaDecodeError.Reason.TRUNCATED
        }

        test("several accounts keep the requested order, each missing one with its address and slot") {
            val data = account(Vault.ACCOUNT.encode(VAULT))
            client.responses["getMultipleAccounts"] = contextual("[$data, null, $data]")
            val accounts = Vault.ACCOUNT.fetch(provider, listOf(FIRST, SECOND, THIRD), PROGRAM).send().unwrap()
            accounts[0].unwrap().address shouldBe FIRST
            accounts[1].unwrapError() shouldBe SolanaAccountError.Missing(SECOND, RpcContext(bigIntegerOf(42)))
            accounts[2].unwrap().address shouldBe THIRD

            client.responses["getMultipleAccounts"] = contextual("[$data, ${account(ByteArray(8))}, $data]")
            val mixed = Vault.ACCOUNT.fetch(provider, listOf(FIRST, SECOND, THIRD), PROGRAM).send().unwrap()
            mixed[0].unwrap().data shouldBe VAULT
            mixed[1].unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>().error.shouldBeInstanceOf<SolanaDecodeError.Unrecognized>()
            mixed[2].unwrap().data shouldBe VAULT
        }

        test("watching emits decode and missing failures then continues delivering valid updates") {
            client.notifications = listOf(
                contextual(account(Vault.ACCOUNT.encode(VAULT))),
                contextual(account(ByteArray(3))),
                contextual(account(Vault.ACCOUNT.encode(VAULT), owner = OTHER_PROGRAM)),
                contextual("null"),
                contextual(account(Vault.ACCOUNT.encode(VAULT))),
            )
            val updates = Vault.ACCOUNT.watch(provider, FIRST, PROGRAM).send().unwrap()
            client.notifications = null
            updates.receive()!!.unwrap().data shouldBe VAULT
            val malformed = updates.receive()!!.unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
            malformed.address shouldBe FIRST
            malformed.context.slot.toString() shouldBe "42"
            malformed.info.data.size shouldBe 3
            updates.receive()!!.unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
                .error.shouldBeInstanceOf<SolanaDecodeError.UnexpectedOwner>()
            updates.receive()!!.unwrapError() shouldBe SolanaAccountError.Missing(FIRST, RpcContext(bigIntegerOf(42)))
            updates.receive()!!.unwrap().data shouldBe VAULT
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

        test("a query retains both decoded accounts and failures with its slot") {
            val good = account(Vault.ACCOUNT.encode(VAULT))
            client.responses["getProgramAccounts"] = contextual(
                """[{"pubkey":"$FIRST","account":$good},{"pubkey":"$SECOND","account":${account(ByteArray(3))}}]""",
            )
            val accounts = SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).query().send().unwrap()
            accounts.size shouldBe 2
            accounts[0].unwrap().address shouldBe FIRST
            accounts[0].unwrap().data shouldBe VAULT
            accounts[0].unwrap().info.owner shouldBe PROGRAM
            val failed = accounts[1].unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
            failed.address shouldBe SECOND
            failed.context shouldBe accounts.context
            failed.info.data.size shouldBe 3
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
            SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).program(OTHER_PROGRAM).query().send().unwrap().single().unwrap().data shouldBe VAULT
            client.params.getValue("getProgramAccounts").first() shouldBe OTHER_PROGRAM.toString()
        }

        test("a program subscription retains failures and continues with the next account") {
            fun notification(address: SolanaAddress, data: ByteArray) = contextual(
                """{"pubkey":"$address","account":${account(data)}}""",
            )
            client.notifications = listOf(notification(FIRST, ByteArray(3)), notification(SECOND, Vault.ACCOUNT.encode(VAULT)))
            val updates = SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).subscribe().send().unwrap()
            client.notifications = null
            val failed = updates.receive()!!.unwrapError().shouldBeInstanceOf<SolanaAccountError.Decode>()
            failed.address shouldBe FIRST
            failed.context.slot.toString() shouldBe "42"
            failed.info.data.size shouldBe 3
            updates.receive()!!.unwrap().address shouldBe SECOND
        }

        test("a subscription decodes each notification with its slot") {
            client.responses["programSubscribe"] = contextual("""{"pubkey":"$SECOND","account":${account(Vault.ACCOUNT.encode(VAULT))}}""")
            val notification = SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).subscribe().send().unwrap().receive()!!.unwrap()
            notification.address shouldBe SECOND
            notification.data.authority shouldBe FIRST
            notification.context.slot.toString() shouldBe "42"
            client.params.getValue("programSubscribe").toString() shouldContain "\"offset\":0"
        }
    }

    test("field filters count their offset from the end of the discriminator") {
        Vault.ACCOUNT.fieldFilter(8) { writeAddress(FIRST) } shouldBe AccountFilter.Memcmp(16, FIRST.toBase58())
    }

    context("nested field descriptors") {
        val amount = SolanaAccountField<Vault, Long>("amount", 0, 8, { it.readI64() }, { writeI64(it) })
        // the authority as its 32 bytes, each an element
        val authority = SolanaAccountArrayField<Vault, SolanaAddress, SolanaAccountField<Vault, Int>>(
            "authority",
            8,
            32,
            1,
            { it.readAddress() },
            { writeAddress(it) },
        ) { name, offset -> SolanaAccountField(name, offset, 1, { it.readU8() }, { writeU8(it) }) }

        test("a filter by descriptor compares the encoded value at its offset from the start of the account data") {
            val filter = SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).field(authority, SECOND).field(authority[31], 7).field(amount, 5)
            filter.accountFilters.drop(1) shouldBe listOf(
                AccountFilter.Memcmp(16, SECOND.toBase58()),
                AccountFilter.Memcmp(47, Base58.encode(byteArrayOf(7))),
                AccountFilter.Memcmp(8, Base58.encode(byteArrayOf(5, 0, 0, 0, 0, 0, 0, 0))),
            )
        }

        test("a filter by descriptor rejects a value that does not encode to the field's size") {
            val short = SolanaAccountField<Vault, Long>("short", 0, 4, { it.readI64() }, { writeI64(it) })
            shouldThrow<IllegalArgumentException> { SolanaAccountFilter(provider, Vault.ACCOUNT, PROGRAM).field(short, 5) }.message shouldContain "takes 4 bytes"
        }

        test("a slice reads fields within a selected field, but not outside every selected field") {
            val selection = SolanaAccountSelection(Vault.ACCOUNT, PROGRAM, SolanaAccountProjectionScope<Vault>().fields(authority))
            client.responses["getAccountInfo"] = contextual(account(Vault.ACCOUNT.encode(Vault(5, SECOND)).copyOfRange(16, 48)))
            val slice = selection.fetch(provider, FIRST).send().unwrap()
            slice[authority] shouldBe SECOND
            slice[authority[0]] shouldBe 2
            slice[authority[31]] shouldBe 2
            shouldThrow<IllegalArgumentException> { slice[amount] }
            // a field that starts within the selection but ends after it is not within it
            shouldThrow<IllegalArgumentException> { slice[SolanaAccountField<Vault, Long>("tail", 28, 16, { it.readI64() }, { writeI64(it) })] }
        }
    }

    test("simulation accounts decode in the order they were requested, as of the simulation's slot") {
        val simulation = Kotlinx.DEFAULT.decodeFromString<ContextValue<TransactionSimulation>>(
            contextual("""{"err":null,"accounts":[null,${account(Vault.ACCOUNT.encode(VAULT))}]}"""),
        )
        val decoded = Vault.ACCOUNT.decodeAccounts(simulation, listOf(FIRST, SECOND), PROGRAM)!!
        decoded[0].unwrapError() shouldBe SolanaAccountError.Missing(FIRST, RpcContext(bigIntegerOf(42)))
        decoded[1].unwrap().data shouldBe VAULT

        shouldThrow<IllegalArgumentException> { Vault.ACCOUNT.decodeAccounts(simulation, listOf(FIRST), PROGRAM) }
        Vault.ACCOUNT.decodeAccounts(ContextValue(RpcContext(bigIntegerOf(1)), TransactionSimulation(err = null)), listOf(FIRST), PROGRAM).shouldBeNull()
    }
})
