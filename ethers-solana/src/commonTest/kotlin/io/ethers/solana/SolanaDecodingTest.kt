package io.ethers.solana

import io.channels.core.ChannelReceiver
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcParams
import io.ethers.solana.providers.PendingSolanaTransaction
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.U64Serializer
import io.ethers.solana.types.rpc.AccountInfo
import io.ethers.solana.types.rpc.BlockNotification
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.InflationReward
import io.ethers.solana.types.rpc.LargestAccount
import io.ethers.solana.types.rpc.LargestTokenAccount
import io.ethers.solana.types.rpc.LogsNotification
import io.ethers.solana.types.rpc.ProgramAccount
import io.ethers.solana.types.rpc.RpcContext
import io.ethers.solana.types.rpc.SignatureNotification
import io.ethers.solana.types.rpc.SignatureStatus
import io.ethers.solana.types.rpc.SlotNotification
import io.ethers.solana.types.rpc.SlotUpdateNotification
import io.ethers.solana.types.rpc.SolanaBlock
import io.ethers.solana.types.rpc.SolanaNodeHealth
import io.ethers.solana.types.rpc.SolanaRPCTransaction
import io.ethers.solana.types.rpc.VoteNotification
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Every Solana call and subscription that used to decode its result with a function of the JSON tree is checked
 * here against that function, kept verbatim as the oracle. For each input, the call's deserializer must give the
 * same value, or fail where the function failed, both when decoding the text (as HTTP does) and the tree (as
 * batches and WebSocket do).
 */
class SolanaDecodingTest : FunSpec({
    val client = CapturingClient()
    val provider = SolanaProvider(client)

    for (case in cases()) {
        test(case.name) {
            client.captured.clear()
            case.call(provider)
            val deserializer = client.captured.single()
            for (input in case.inputs) {
                withClue("${case.name}: $input") {
                    val expected = outcome { case.project(case.oracle(Kotlinx.DEFAULT.parseToJsonElement(input))) }
                    outcome { case.project(Kotlinx.DEFAULT.decodeFromString(deserializer, input)) } shouldBe expected
                    outcome { case.project(Kotlinx.DEFAULT.decodeFromJsonElement(deserializer, Kotlinx.DEFAULT.parseToJsonElement(input))) } shouldBe expected
                }
            }
        }
    }
})

private class Case(
    val name: String,
    val call: suspend SolanaProvider.() -> Unit,
    val oracle: (JsonElement) -> Any?,
    val inputs: List<String>,
    val project: (Any?) -> Any? = { it },
)

// the tree decoders, as they were
private fun decodeU64(value: JsonElement) = Kotlinx.DEFAULT.decodeFromJsonElement(U64Serializer, value)
private inline fun <reified T> decode(value: JsonElement): T = Kotlinx.DEFAULT.decodeFromJsonElement(value)
private fun <T> decodeContext(value: JsonElement, decoder: (JsonElement) -> T): ContextValue<T> {
    val obj = value.jsonObject
    return ContextValue(decode<RpcContext>(obj.getValue("context")), decoder(obj.getValue("value")))
}

private const val ADDRESS = "11111111111111111111111111111111"
private const val ADDRESS_2 = "SysvarC1ock11111111111111111111111111111111"
private const val SIGNATURE = "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQUW"

private val U64S = listOf("5", "0", "18446744073709551615", "18446744073709551616", "-1", "\"5\"", "1.5", "1e3", "null", "{}", "[]")
private val U64_LISTS = listOf("[1,2,3]", "[]", "[\"1\"]", "[-1]", "[null]", "null", "{}")

private fun context(vararg values: String) = values.map { """{"context":{"slot":7,"apiVersion":"2.0"},"value":$it}""" } +
    listOf("""{"value":${values.first()}}""", """{"context":{"slot":7}}""", """{"value":${values.first()},"context":{"slot":1},"extra":[1]}""", "null", "{}", "[]")

private val BASE58 = { valid: String -> listOf("\"$valid\"", "\"0OIl\"", "\"\"", "null", "5", "{}") }

private fun cases(): List<Case> {
    val address = SolanaAddress(ADDRESS)
    val signature = SolanaSignature(SIGNATURE)
    val slot = bigIntegerOf(5)
    val account = """{"data":["AQID","base64"],"executable":false,"lamports":5,"owner":"$ADDRESS","rentEpoch":0,"space":3}"""
    val u64: (JsonElement) -> Any? = ::decodeU64

    return listOf(
        Case("getBalance", { getBalance(address).send() }, { decodeContext(it, ::decodeU64) }, context("5", "\"5\"", "-1", "null")),
        Case("getStakeMinimumDelegation", { getStakeMinimumDelegation().send() }, { decodeContext(it, ::decodeU64) }, context("1000000000")),
        Case("isBlockhashValid", { isBlockhashValid(SolanaBlockhash(ADDRESS)).send() }, { decodeContext(it) { v -> v.jsonPrimitive.boolean } }, context("true", "false", "\"true\"", "null", "1")),
        Case("getHealth", { getHealth().send() }, { if (it.jsonPrimitive.content == "ok") SolanaNodeHealth.OK else SolanaNodeHealth.ERROR }, listOf("\"ok\"", "\"behind\"", "\"OK\"", "null", "5", "{}", "[]")),
        Case("getTransactionCount", { getTransactionCount().send() }, u64, U64S),
        Case("getMinimumBalanceForRentExemption", { getMinimumBalanceForRentExemption(10L).send() }, u64, U64S),
        Case("getSlot", { getSlot().send() }, u64, U64S),
        Case("getBlockHeight", { getBlockHeight().send() }, u64, U64S),
        Case("getFirstAvailableBlock", { getFirstAvailableBlock().send() }, u64, U64S),
        Case("minimumLedgerSlot", { minimumLedgerSlot().send() }, u64, U64S),
        Case("getMaxShredInsertSlot", { getMaxShredInsertSlot().send() }, u64, U64S),
        Case("getMaxRetransmitSlot", { getMaxRetransmitSlot().send() }, u64, U64S),
        Case("getTransaction", { getTransaction(signature).send() }, { if (it == JsonNull) null else decode<SolanaRPCTransaction>(it) }, listOf("null", "{}", "[]", "5")),
        Case("getBlock", { getBlock(slot).send() }, { if (it == JsonNull) null else decode<SolanaBlock>(it) }, listOf("null", "{}", "[]", "5")),
        Case("requestAirdrop", { requestAirdrop(address, 5L).send() }, { SolanaSignature(it.jsonPrimitive.content) }, BASE58(SIGNATURE)),
        Case(
            "sendTransaction",
            { sendTransaction(byteArrayOf(1, 2, 3)).send() },
            { SolanaSignature(it.jsonPrimitive.content) },
            BASE58(SIGNATURE),
            project = { (it as? PendingSolanaTransaction)?.signature ?: it },
        ),
        Case("getFeeForMessage", { getFeeForMessage(byteArrayOf(1)).send() }, { decodeContext(it) { v -> if (v == JsonNull) null else decodeU64(v) } }, context("5000", "null", "\"5\"")),
        Case(
            "getSignatureStatuses",
            { getSignatureStatuses(signature).send() },
            { element -> decodeContext(element) { value -> value.jsonArray.map { if (it == JsonNull) null else decode<SignatureStatus>(it) } } },
            context("""[null,{"slot":9,"confirmations":null,"err":null,"confirmationStatus":"finalized"}]""", "[]", "null", "[{}]"),
        ),
        Case("getBlocks", { getBlocks(slot, slot).send() }, { element -> element.jsonArray.map(::decodeU64) }, U64_LISTS),
        Case("getBlocksWithLimit", { getBlocksWithLimit(slot, 3).send() }, { element -> element.jsonArray.map(::decodeU64) }, U64_LISTS),
        Case(
            "getTokenLargestAccounts",
            { getTokenLargestAccounts(address).send() },
            { element -> decodeContext(element) { it.jsonArray.map { account -> decode<LargestTokenAccount>(account) } } },
            context("""[{"address":"$ADDRESS","amount":"58840833237","decimals":9,"uiAmount":58.840833237,"uiAmountString":"58.840833237"}]""", "[]", "[null]"),
        ),
        Case(
            "getLargestAccounts",
            { getLargestAccounts().send() },
            { element -> decodeContext(element) { it.jsonArray.map { account -> decode<LargestAccount>(account) } } },
            context("""[{"address":"$ADDRESS","lamports":9}]""", "[]", "[null]"),
        ),
        Case("getBlockTime", { getBlockTime(slot).send() }, { if (it == JsonNull) null else it.jsonPrimitive.long }, listOf("1700000000", "null", "\"5\"", "-1", "1.5", "{}")),
        Case(
            "getLeaderSchedule",
            { getLeaderSchedule().send() },
            { element -> if (element == JsonNull) null else element.jsonObject.mapValues { (_, slots) -> slots.jsonArray.map(::decodeU64) } },
            listOf("""{"$ADDRESS":[0,1,2],"$ADDRESS_2":[]}""", "null", "{}", """{"a":["1"]}""", """{"a":null}""", "[]"),
        ),
        Case("getSlotLeader", { getSlotLeader().send() }, { SolanaAddress(it.jsonPrimitive.content) }, BASE58(ADDRESS)),
        Case("getSlotLeaders", { getSlotLeaders(slot, 2).send() }, { element -> element.jsonArray.map { SolanaAddress(it.jsonPrimitive.content) } }, listOf("[\"$ADDRESS\",\"$ADDRESS_2\"]", "[]", "[null]", "null")),
        Case("getGenesisHash", { getGenesisHash().send() }, { SolanaBlockhash(it.jsonPrimitive.content) }, BASE58(ADDRESS)),
        Case(
            "getInflationReward",
            { getInflationReward(listOf(address)).send() },
            { element -> element.jsonArray.map { if (it == JsonNull) null else decode<InflationReward>(it) } },
            listOf("""[null,{"epoch":100,"effectiveSlot":9,"amount":5,"postBalance":10,"commission":5}]""", "[]", "[{}]", "null"),
        ),
        Case(
            "subscribeAccount",
            { subscribeAccount(address).send() },
            { element -> decodeContext(element) { if (it == JsonNull) null else decode<AccountInfo>(it) } },
            context(account, "null", "{}"),
        ),
        Case(
            "subscribeProgram",
            { subscribeProgram(address).send() },
            { element -> decodeContext(element) { decode<ProgramAccount>(it) } },
            context("""{"pubkey":"$ADDRESS","account":$account}""", "null", "{}"),
        ),
        Case("subscribeLogs", { subscribeLogs().send() }, { decode<ContextValue<LogsNotification>>(it) }, context("""{"signature":"$SIGNATURE","err":null,"logs":["a"]}""", "{}")),
        Case("subscribeSignature", { subscribeSignature(signature).send() }, { decode<ContextValue<SignatureNotification>>(it) }, context("""{"err":null}""", "\"receivedSignature\"", "{}")),
        Case("subscribeBlock", { subscribeBlock().send() }, { element -> decodeContext(element) { decode<BlockNotification>(it) } }, context("""{"slot":5,"block":null,"err":null}""", "{}")),
        Case("subscribeSlotsUpdates", { subscribeSlotsUpdates().send() }, { decode<SlotUpdateNotification>(it) }, listOf("""{"type":"firstShredReceived","slot":5,"timestamp":1}""", "{}", "null")),
        Case("subscribeVote", { subscribeVote().send() }, { decode<VoteNotification>(it) }, listOf("{}", "null")),
        Case("subscribeSlot", { subscribeSlot().send() }, { decode<SlotNotification>(it) }, listOf("""{"parent":4,"root":3,"slot":5}""", "{}", "null")),
        Case("subscribeRoot", { subscribeRoot().send() }, u64, U64S),
    )
}

private object Failed {
    override fun toString() = "Failed"
}

private data class Decoded(val value: Any?)

private inline fun outcome(block: () -> Any?): Any = try {
    Decoded(block())
} catch (_: Exception) {
    Failed
}

/** Records the deserializer of each request and subscription, and fails it. */
private class CapturingClient : JsonRpcClient {
    val captured = ArrayList<DeserializationStrategy<*>>()

    override suspend fun <T> request(method: String, params: RpcParams, resultDeserializer: DeserializationStrategy<T>): Result<T, RpcError> {
        captured += resultDeserializer
        return failure(RpcError(RpcError.CODE_INTERNAL_ERROR, "captured"))
    }

    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = false

    override suspend fun <T : Any> subscribe(params: RpcParams, resultDeserializer: DeserializationStrategy<T>): Result<ChannelReceiver<T>, RpcError> {
        captured += resultDeserializer
        return failure(RpcError(RpcError.CODE_INTERNAL_ERROR, "captured"))
    }

    override fun close() = Unit
}
