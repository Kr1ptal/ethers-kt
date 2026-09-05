package io.ethers.solana

import io.channels.core.ChannelReceiver
import io.channels.core.QueueChannel
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.success
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcClientConfig
import io.ethers.providers.RpcError
import io.ethers.providers.SubscriptionDescriptor
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.solana.providers.AccountFilter
import io.ethers.solana.providers.LogsFilter
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.providers.SolanaSubscriptionDescriptor
import io.ethers.solana.types.Commitment
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SignatureNotification
import io.ethers.solana.types.SolanaSignature
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class SubscriptionsTest : FunSpec({
    val key = Programs.SYSTEM
    val signature = SolanaSignature(ByteArray(64))
    val account = """{"data":["AQID","base64"],"executable":false,"lamports":123,"owner":"$key","rentEpoch":42}"""
    fun contextual(value: String) = """{"context":{"slot":42},"value":$value}"""
    val client = SubscriptionClient()
    val provider = SolanaProvider(client)

    test("descriptor resolves all six methods without changing caller params") {
        for (name in listOf("account", "program", "logs", "signature", "slot", "root")) {
            val params = arrayOf(name, "argument")
            val resolved = SolanaSubscriptionDescriptor.resolve(params)
            resolved.subscribeMethod shouldBe "${name}Subscribe"
            resolved.unsubscribeMethod shouldBe "${name}Unsubscribe"
            resolved.notificationMethod shouldBe "${name}Notification"
            resolved.params.toList() shouldBe listOf("argument")
            params[1] = "changed"
            resolved.params.toList() shouldBe listOf("argument")
            resolved.isTerminal(Kotlinx.DEFAULT.parseToJsonElement(contextual("{\"err\":null}"))) shouldBe (name == "signature")
        }
        shouldThrow<IllegalArgumentException> { SolanaSubscriptionDescriptor.resolve(emptyArray<Any>()) }
        shouldThrow<IllegalArgumentException> { SolanaSubscriptionDescriptor.resolve(arrayOf("unknown")) }
    }

    test("account and program subscriptions decode contextual data and filters") {
        client.event = contextual(account)
        provider.subscribeAccount(key).send().unwrap().take()!!.value!!.data shouldBe byteArrayOf(1, 2, 3)
        client.descriptor.subscribeMethod shouldBe "accountSubscribe"
        client.descriptor.unsubscribeMethod shouldBe "accountUnsubscribe"
        client.params[0] shouldBe key.toString()
        client.params[1] shouldBe Kotlinx.DEFAULT.parseToJsonElement("""{"commitment":"finalized","encoding":"base64"}""")
        client.event = contextual("""{"pubkey":"$key","account":$account}""")
        provider.subscribeProgram(key, listOf(AccountFilter.DataSize(165), AccountFilter.Memcmp(0, key.toString()))).send().unwrap().take()!!.value.pubkey shouldBe key
        client.descriptor.subscribeMethod shouldBe "programSubscribe"
        client.params[1] shouldBe Kotlinx.DEFAULT.parseToJsonElement("""{"commitment":"finalized","encoding":"base64","filters":[{"dataSize":165},{"memcmp":{"offset":0,"bytes":"$key","encoding":"base58"}}]}""")
    }

    test("subscription commitment overrides leave the provider default unchanged") {
        fun assertCommitment(expected: Commitment) {
            (client.params[1] as JsonObject)["commitment"] shouldBe JsonPrimitive(expected.toString())
            provider.defaultCommitment shouldBe Commitment.FINALIZED
        }
        client.event = contextual(account)
        provider.subscribeAccount(key, commitment = Commitment.CONFIRMED).send().unwrap()
        assertCommitment(Commitment.CONFIRMED)
        provider.subscribeAccount(key).send().unwrap()
        assertCommitment(Commitment.FINALIZED)

        client.event = contextual("""{"pubkey":"$key","account":$account}""")
        provider.subscribeProgram(key, commitment = Commitment.CONFIRMED).send().unwrap()
        assertCommitment(Commitment.CONFIRMED)
        provider.subscribeProgram(key).send().unwrap()
        assertCommitment(Commitment.FINALIZED)

        client.event = contextual("""{"signature":"$signature","err":null,"logs":[]}""")
        provider.subscribeLogs(commitment = Commitment.CONFIRMED).send().unwrap()
        assertCommitment(Commitment.CONFIRMED)
        provider.subscribeLogs().send().unwrap()
        assertCommitment(Commitment.FINALIZED)

        client.event = contextual("""{"err":null}""")
        provider.subscribeSignature(signature, commitment = Commitment.CONFIRMED).send().unwrap()
        assertCommitment(Commitment.CONFIRMED)
        provider.subscribeSignature(signature).send().unwrap()
        assertCommitment(Commitment.FINALIZED)
    }

    test("logs support all, allWithVotes and single-account mentions") {
        client.event = contextual("""{"signature":"$signature","err":null,"logs":["hello"]}""")
        provider.subscribeLogs().send().unwrap().take()!!.value.logs shouldBe listOf("hello")
        client.params[0] shouldBe JsonPrimitive("all")
        provider.subscribeLogs(LogsFilter.AllWithVotes).send().unwrap()
        client.params[0] shouldBe JsonPrimitive("allWithVotes")
        provider.subscribeLogs(LogsFilter.Mentions(key)).send().unwrap()
        client.params[0] shouldBe Kotlinx.DEFAULT.parseToJsonElement("""{"mentions":["$key"]}""")
        client.descriptor.notificationMethod shouldBe "logsNotification"
    }

    test("signature received events are non-terminal and statuses are terminal") {
        client.event = contextual("\"receivedSignature\"")
        provider.subscribeSignature(signature, enableReceivedNotification = true).send().unwrap().take()!!::class shouldBe SignatureNotification.Received::class
        client.descriptor.isTerminal(Kotlinx.DEFAULT.parseToJsonElement(client.event)) shouldBe false
        client.event = contextual("""{"err":{"InstructionError":[0,"InvalidArgument"]}}""")
        val status = provider.subscribeSignature(signature).send().unwrap().take() as SignatureNotification.Status
        status.err shouldBe io.ethers.solana.types.TransactionError.InstructionFailure(0, io.ethers.solana.types.InstructionError.Simple.INVALID_ARGUMENT)
        client.descriptor.isTerminal(Kotlinx.DEFAULT.parseToJsonElement(client.event)) shouldBe true
        client.descriptor.unsubscribeMethod shouldBe "signatureUnsubscribe"
    }

    test("slot and root subscriptions have empty params and precise numeric results") {
        client.event = """{"parent":40,"root":39,"slot":42}"""
        provider.subscribeSlot().send().unwrap().take()!!.slot shouldBe bigIntegerOf(42)
        client.params.isEmpty() shouldBe true
        client.descriptor.subscribeMethod shouldBe "slotSubscribe"
        client.event = "42"
        provider.subscribeRoot().send().unwrap().take() shouldBe bigIntegerOf(42)
        client.params.isEmpty() shouldBe true
        client.descriptor.subscribeMethod shouldBe "rootSubscribe"
    }
})

private class SubscriptionClient : JsonRpcClient {
    var event = "null"
    private val config = RpcClientConfig().subscriptionDescriptor(SolanaSubscriptionDescriptor)
    lateinit var descriptor: SubscriptionDescriptor.Resolved
    lateinit var params: Array<*>
    override suspend fun <T : Any> subscribe(params: Array<*>, resultDecoder: (JsonElement) -> T): Result<ChannelReceiver<T>, RpcError> {
        this.descriptor = config.subscriptionDescriptor.resolve(params)
        this.params = descriptor.params
        val channel = QueueChannel.spscUnbounded<T>()
        channel.offer(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement(event)))
        return success(channel)
    }
    override suspend fun <T> request(method: String, params: Array<*>, resultDecoder: (JsonElement) -> T): Result<T, RpcError> = error("Unexpected request")
    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = error("Unexpected batch")
    override fun close() = Unit
}
