package io.ethers.aa.bundle

import io.ethers.aa.EntryPoint
import io.ethers.aa.FakeJsonRpcClient
import io.ethers.aa.contracts.EntryPointV09
import io.ethers.core.Kotlinx
import io.ethers.core.types.BlockId
import io.ethers.core.types.Hash
import io.ethers.providers.Provider
import io.ethers.providers.RpcError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BundleReaderTest : FunSpec({
    val fixture = BundleFixture.load("v07_postop_revert_multi")
    val raw = Kotlinx.DEFAULT.parseToJsonElement(
        BundleFixture::class.java.getResourceAsStream("/io/ethers/aa/bundles/v07_postop_revert_multi.json")!!
            .bufferedReader().use { it.readText() },
    ).jsonObject
    val txHash = fixture.receipt.transactionHash

    fun node() = FakeJsonRpcClient()
        .on("eth_getTransactionReceipt") { if (it[0].jsonPrimitive.content == txHash.toString()) raw["receipt"].toString() else "null" }
        .on("eth_getTransactionByHash") { if (it[0].jsonPrimitive.content == txHash.toString()) raw["transaction"].toString() else "null" }
        .on("debug_traceTransaction") { raw["trace"].toString() }
        // eth_getLogs answers with the receipt logs matching the filter's topics
        .on("eth_getLogs") { params ->
            val filter = params[0].jsonObject
            val topics = filter["topics"]!!.jsonArray
            val matching = fixture.receipt.logs.indices.filter { i ->
                val log = fixture.receipt.logs[i]
                topics.withIndex().all { (t, wanted) ->
                    val options = (wanted as? JsonArray)?.map { it.jsonPrimitive.content } ?: listOfNotNull(wanted.jsonPrimitive.contentOrNull)
                    options.isEmpty() || log.topics.getOrNull(t)?.toString() in options
                }
            }
            JsonArray(matching.map { raw["receipt"]!!.jsonObject["logs"]!!.jsonArray[it] }).toString()
        }

    test("reads the bundles of a transaction, with traces") {
        val rpc = node()
        val bundles = BundleReader(Provider(rpc, 1), useTrace = true).getBundles(txHash).send().unwrap()
        bundles shouldBe BundleDecoder.decode(fixture.receipt, fixture.transaction, fixture.trace).unwrap()
        rpc.requests("debug_traceTransaction") shouldHaveSize 1
    }

    test("reads without traces by default") {
        val rpc = node()
        val bundles = BundleReader(Provider(rpc, 1)).getBundles(txHash).send().unwrap()
        bundles shouldBe BundleDecoder.decode(fixture.receipt, fixture.transaction).unwrap()
        rpc.requests("debug_traceTransaction").shouldBeEmpty()
    }

    test("finds an executed operation by its hash") {
        val expected = BundleDecoder.decode(fixture.receipt, fixture.transaction).unwrap().single().userOperations.last()
        val reader = BundleReader(Provider(node(), 1))

        val found = reader.getExecutedUserOperation(expected.userOpHash, BlockId.Number(0), BlockId.LATEST).send().unwrap()
        found shouldBe expected

        reader.getExecutedUserOperation(Hash.ZERO, BlockId.Number(0), BlockId.LATEST).send().unwrap().shouldBeNull()
    }

    test("finds the operations of a sender") {
        val expected = BundleDecoder.decode(fixture.receipt, fixture.transaction).unwrap().single().userOperations.first()
        val ops = BundleReader(Provider(node(), 1))
            .getUserOperationsBySender(expected.sender, BlockId.Number(0), BlockId.LATEST)
            .send().unwrap()
        ops.forEach { it.sender shouldBe expected.sender }
        ops.first() shouldBe expected
    }

    test("reads the bundles of a block") {
        val bundles = BundleReader(Provider(node(), 1)).getBundlesInBlock(BlockId.Number(fixture.receipt.blockNumber)).send().unwrap()
        bundles shouldHaveSize 1
        bundles.single().entryPoint shouldBe EntryPoint.V0_7
    }

    test("block hash as the end of a range is a failure, not an exception") {
        BundleReader(Provider(node(), 1)).getExecutedUserOperation(Hash.ZERO, BlockId.Number(0), BlockId.Hash(Hash.ZERO))
            .send().unwrapError().shouldBeInstanceOf<BundleReader.Error.Rpc>()
    }

    test("unknown transaction and RPC errors are reported") {
        BundleReader(Provider(node(), 1)).getBundles(Hash.ZERO).send().unwrapError()
            .shouldBeInstanceOf<BundleReader.Error.TransactionNotFound>().transactionHash shouldBe Hash.ZERO

        val failing = FakeJsonRpcClient().fail("eth_getTransactionReceipt", RpcError(-32000, "boom"))
            .respond("eth_getTransactionByHash", "null")
        BundleReader(Provider(failing, 1)).getBundles(txHash).send().unwrapError()
            .shouldBeInstanceOf<BundleReader.Error.Rpc>().error.message shouldBe "boom"
    }

    test("filters for operation events of the configured EntryPoints") {
        val rpc = node()
        BundleReader(Provider(rpc, 1), entryPoints = listOf(EntryPoint.V0_7))
            .getExecutedUserOperation(Hash.ZERO, BlockId.Number(0), BlockId.LATEST).send().unwrap()
        val filter = rpc.requests("eth_getLogs").single().params[0].jsonObject
        val addresses = filter["address"].shouldNotBeNull().toString().lowercase()
        addresses shouldContain EntryPoint.V0_7.address.toString().lowercase()
        addresses shouldNotContain EntryPoint.V0_6.address.toString().lowercase()
        filter["topics"]!!.jsonArray[0].jsonPrimitive.content shouldBe EntryPointV09.UserOperationEvent.abi.topicId.toString()
    }
})
