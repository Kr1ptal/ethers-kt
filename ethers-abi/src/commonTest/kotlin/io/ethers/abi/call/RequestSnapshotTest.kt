package io.ethers.abi.call

import io.channels.core.ChannelReceiver
import io.ethers.abi.AbiEvent
import io.ethers.abi.AbiType
import io.ethers.abi.ContractEvent
import io.ethers.abi.EventFactory
import io.ethers.abi.EventFilter
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Log
import io.ethers.core.types.LogFilter
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.Provider
import io.ethers.providers.RpcError
import io.ethers.providers.middleware.Middleware
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.providers.types.RpcParams
import io.ethers.signers.PrivateKeySigner
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.DeserializationStrategy

/**
 * Contract calls and event filters are mutable builders, but every request created from them must carry a snapshot:
 * changing the builder afterwards must not change what an already created request sends.
 * */
class RequestSnapshotTest : FunSpec({
    val contract = Address("0x0000000000000000000000000000000000000001")
    val sender = Address("0x0000000000000000000000000000000000000002")

    test("changing a contract call after creating an eth_call request does not change the request") {
        val client = RecordingClient()
        val call = ReadFunctionCall(Provider(client, 1L), contract, Bytes("0x01")) { it }

        val request = call.call(BlockId.LATEST)
        call.from(sender).gas(50_000L)
        request.send()

        val sent = client.params.single()[0].shouldBeInstanceOf<CallRequest>()
        sent.from shouldBe null
        sent.gas shouldBe -1L
        sent.to shouldBe contract
        sent.data shouldBe Bytes("0x01")
    }

    test("changing a contract call after creating an eth_createAccessList request does not change the request") {
        val client = RecordingClient()
        val call = FunctionCall(Provider(client, 1L), contract, Bytes("0x01")) { it }

        val request = call.createAccessList(BlockId.LATEST)
        call.nonce(7L)
        request.send()

        client.params.single()[0].shouldBeInstanceOf<CallRequest>().nonce shouldBe -1L
    }

    test("toCallRequest returns a snapshot of the contract call") {
        val call = FunctionCall(Provider(RecordingClient(), 1L), contract, Bytes("0x01")) { it }

        val snapshot = call.toCallRequest()
        call.gas(50_000L).gasPrice(bigIntegerOf(10))

        snapshot.gas shouldBe -1L
        snapshot.gasPrice shouldBe null
        snapshot.chainId shouldBe 1L
        call.toCallRequest().gas shouldBe 50_000L
    }

    test("IncompleteCall holds a snapshot, not the contract call's own state") {
        val call = FunctionCall(Provider(RecordingClient(), 1L), contract, Bytes("0x01")) { it }
        val signer = PrivateKeySigner(ByteArray(32) { 1 })

        val error = call.trySign(signer).unwrapError().shouldBeInstanceOf<CallSigningError.IncompleteCall>()
        call.gas(50_000L)

        error.call.gas shouldBe -1L
    }

    test("changing an event filter after creating an eth_getLogs request does not change the request") {
        val client = RecordingClient()
        val filter = Transfer.filter(Provider(client, 1L))

        val request = filter.query()
        filter.address(contract).blockRange(1L, 2L)
        request.send()

        val sent = client.params.single()[0].shouldBeInstanceOf<LogFilter>()
        sent.addresses shouldBe null
        sent.blocks shouldBe LogFilter {}.blocks
        sent.topic0 shouldBe listOf(Transfer.abi.topicId)
    }
})

private class Transfer(override val log: Log) : ContractEvent {
    companion object : EventFactory<Transfer> {
        override val abi = AbiEvent("Transfer", listOf(AbiEvent.Token(AbiType.Address, true)), false)

        override fun filter(provider: Middleware) = EventFilter(provider, this)

        override fun decode(log: Log, data: List<Any>) = Transfer(log)
    }
}

/**
 * Records the params of every request when it is sent, answering `eth_call` with empty bytes and everything else
 * with an empty list.
 * */
private class RecordingClient : JsonRpcClient {
    val params = ArrayList<List<Any?>>()

    override suspend fun <T> request(
        method: String,
        params: RpcParams,
        resultDeserializer: DeserializationStrategy<T>,
    ): Result<T, RpcError> {
        this.params.add(List(params.size) { params[it] })
        val result = when (method) {
            "eth_call" -> "\"0x\""
            "eth_createAccessList" -> """{"accessList":[],"gasUsed":"0x0"}"""
            else -> "[]"
        }
        return success(Kotlinx.DEFAULT.decodeFromString(resultDeserializer, result))
    }

    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = throw UnsupportedOperationException()

    override suspend fun <T : Any> subscribe(
        params: RpcParams,
        resultDeserializer: DeserializationStrategy<T>,
    ): Result<ChannelReceiver<T>, RpcError> = failure(RpcError(RpcError.CODE_METHOD_NOT_FOUND, "unsupported"))

    override fun close() = Unit
}
