package io.ethers.providers.types

import io.ethers.core.Jackson
import io.ethers.core.isFailure
import io.ethers.core.types.Address
import io.ethers.core.types.Bloom
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.types.transaction.TxType
import io.ethers.providers.HttpClient
import io.ethers.providers.Provider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.intellij.lang.annotations.Language
import java.math.BigInteger
import java.time.Duration

class PendingTransactionTest : FunSpec({
    context("awaitInclusion()") {
        val mockWebServer = MockWebServer()
        val provider = run<Provider> {
            mockWebServer.start()

            // Immediately enqueue a response for `eth_chainId` call which is executed on Provider creation
            mockWebServer.enqueue(generateMockResponse(body = CHAIN_ID_RESPONSE))

            val httpClient = HttpClient(mockWebServer.url("").toString(), OkHttpClient())
            Provider(httpClient)
        }
        val minedBlockNumber = 18341180L
        val txHash = Hash("0xce15f8ce74845b0d254fcbfda722ba89976ca6e09936d6761a648a6492b82e9b")
        val pendingTransaction = PendingTransaction(txHash, provider)

        test("transaction included, no confirmations") {
            val retries = 5
            enqueueEmptyResponses(mockWebServer, retries - 1)
            mockWebServer.enqueue(generateMockResponse(body = TX_RECEIPT_RESPONSE))

            val response = pendingTransaction.awaitInclusion(retries, Duration.ofMillis(50), 0).unwrap()
            response shouldBe TX_RECEIPT
        }

        test("transaction included, confirmation required") {
            val retries = 5
            enqueueEmptyResponses(mockWebServer, retries - 1)
            mockWebServer.enqueue(generateMockResponse(body = TX_RECEIPT_RESPONSE))

            // Generate mock responses for subsequent confirmations until we reach final response
            val confirmations = 3
            for (i in 1..<3) {
                mockWebServer.enqueue(generateMockResponse(body = MINED_BLOCK_RESPONSE_FACTORY(minedBlockNumber + i)))
            }

            val response = pendingTransaction.awaitInclusion(retries, Duration.ofMillis(50), confirmations).unwrap()
            response shouldBe TX_RECEIPT
        }

        test("transaction not included") {
            // Generate mock responses for subsequent retries until we reach final response
            val retries = 5
            enqueueEmptyResponses(mockWebServer, retries)

            val response = pendingTransaction.awaitInclusion(retries, Duration.ofMillis(50), 0)
            response.isFailure() shouldBe true
            response.unwrapError().shouldBeInstanceOf<PendingInclusion.Error>()
        }

        test("inclusion response returns error") {
            // Failed transaction status response
            val errorMessage = "Failed to query transaction status"
            mockWebServer.enqueue(generateMockResponse(body = ERROR_RESPONSE_FACTORY(errorMessage)))

            val response = pendingTransaction.awaitInclusion(1, Duration.ofMillis(50), 0)
            response.isFailure() shouldBe true

            val error = response.unwrapError()
            error.shouldBeInstanceOf<PendingInclusion.Error.RpcError>()
            error.error.message shouldBe errorMessage
        }

        test("confirmation response returns error") {
            // Successful transaction status response
            mockWebServer.enqueue(generateMockResponse(body = TX_RECEIPT_RESPONSE))

            // Failed block status response
            val errorMessage = "Failed to query mined block status"
            mockWebServer.enqueue(generateMockResponse(body = ERROR_RESPONSE_FACTORY(errorMessage)))

            val response = pendingTransaction.awaitInclusion(1, Duration.ofMillis(50), 10)
            response.isFailure() shouldBe true

            val error = response.unwrapError()
            error.shouldBeInstanceOf<PendingInclusion.Error.RpcError>()
            error.error.message shouldBe errorMessage
        }
    }
})

@Language("JSON")
private val EMPTY_RESPONSE = """
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": null
        }
""".trimIndent()

@Language("JSON")
private val CHAIN_ID_RESPONSE = """
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": "0xf423f"
        }
""".trimIndent()

@Language("JSON")
private val ERROR_RESPONSE_FACTORY: (String) -> String = { error ->
    @Language("JSON")
    val response = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "error": {
                    "code": 1,
                    "message": "$error",
                    "data": "no_data"
                }
            }
    """.trimIndent()
    response
}

private val MINED_BLOCK_RESPONSE_FACTORY: (Long) -> String = { blockNumber ->
    @Language("JSON")
    val response = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "result": "0x${blockNumber.toString(16)}"
            }
    """.trimIndent()
    response
}

@Language("JSON")
private val TX_RECEIPT_RESPONSE = """
        {
          "jsonrpc": "2.0",
          "id": 1,
          "result": {
            "blockHash": "0x1d1bf1f362575491216d32c4bfe0fd7f4cb74281803d72f8b17260e365247329",
            "blockNumber": "0x117dd3c",
            "contractAddress": null,
            "cumulativeGasUsed": "0x7a063",
            "effectiveGasPrice": "0x166a124ba",
            "from": "0x0eeeeb7ae10c10f82f34f796b9ccf6066cce1421",
            "gasUsed": "0x65331",
            "logs": [
              {
                "address": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                "topics": [
                  "0x7fcf532c15f0a6db0bd6d0e038bea71d30d808c7d98cb3bf7268a95bf5081b65",
                  "0x000000000000000000000000b0bababe78a9be0810fadf99dd2ed31ed12568be"
                ],
                "data": "0x0000000000000000000000000000000000000000000000000110c4f5fd7772ba",
                "blockNumber": "0x117c277",
                "transactionHash": "0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8",
                "transactionIndex": "0xa0",
                "blockHash": "0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560",
                "logIndex": "0x10",
                "removed": true
              }
            ],
            "logsBloom": "0x002000000100008010000000800000000000000000000000000000000400000000000100000000000000180000000000020080000800380001400002000090000000000000000028080200080000002400000000000010002000040080000000000400000000400000000000000000410000000000000000000000100008000000000210200000000000000000000000002000010100000840a00040080000000000000000002080000040400000020000000000000002000020040000080000000000020000000000000100000000000000000000008010000000000000000000202020000000000000108000004000000000a0000000400000000000001000",
            "status": "0x1",
            "to": "0x881d40237659c251811cec9c364ef91dc08d300c",
            "transactionHash": "0xce15f8ce74845b0d254fcbfda722ba89976ca6e09936d6761a648a6492b82e9b",
            "transactionIndex": "0x1",
            "type": "0x2",
            "root": "0x5f5755290000000000000000000000000000000000000000000000000000000000000080",
            "test_tx": {
              "k1_tx": "v1_tx",
              "k2_tx": "v2_tx"
            }
          }
        }
""".trimIndent()

private val TX_RECEIPT = TransactionReceipt(
    blockHash = Hash("0x1d1bf1f362575491216d32c4bfe0fd7f4cb74281803d72f8b17260e365247329"),
    blockNumber = 18341180L,
    contractAddress = null,
    cumulativeGasUsed = 499811L,
    effectiveGasPrice = BigInteger("6016804026"),
    from = Address("0x0eeeeb7ae10c10f82f34f796b9ccf6066cce1421"),
    gasUsed = 414513L,
    logs = listOf(
        Log(
            address = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"),
            topics = listOf(
                Hash("0x7fcf532c15f0a6db0bd6d0e038bea71d30d808c7d98cb3bf7268a95bf5081b65"),
                Hash("0x000000000000000000000000b0bababe78a9be0810fadf99dd2ed31ed12568be"),
            ),
            data = Bytes("0x0000000000000000000000000000000000000000000000000110c4f5fd7772ba"),
            blockNumber = 18334327L,
            transactionHash = Hash("0xc74b35721eec9b338589ea735f8d322b3e27f3259d9e924ef354a4336fb715a8"),
            transactionIndex = 160,
            blockHash = Hash("0xf58bc0d9ad6de2ca7169880cf7d6ffe970c85e48880c00745f21f7a0a5330560"),
            logIndex = 16,
            removed = true,
        ),
    ),
    logsBloom = Bloom("0x002000000100008010000000800000000000000000000000000000000400000000000100000000000000180000000000020080000800380001400002000090000000000000000028080200080000002400000000000010002000040080000000000400000000400000000000000000410000000000000000000000100008000000000210200000000000000000000000002000010100000840a00040080000000000000000002080000040400000020000000000000002000020040000080000000000020000000000000100000000000000000000008010000000000000000000202020000000000000108000004000000000a0000000400000000000001000"),
    status = 1,
    to = Address("0x881d40237659c251811cec9c364ef91dc08d300c"),
    transactionHash = Hash("0xce15f8ce74845b0d254fcbfda722ba89976ca6e09936d6761a648a6492b82e9b"),
    transactionIndex = 1,
    type = TxType.DynamicFee,
    root = Bytes("0x5f5755290000000000000000000000000000000000000000000000000000000000000080"),
    otherFields = mapOf(
        "test_tx" to Jackson.MAPPER.readTree("""{"k1_tx":"v1_tx","k2_tx":"v2_tx"}"""),
    ),
)

private fun generateMockResponse(body: String): MockResponse {
    return MockResponse().setResponseCode(200).setBody(body)
}

private fun enqueueEmptyResponses(mockWebServer: MockWebServer, count: Int) {
    // Generate mock responses for subsequent retries until we reach final response
    for (i in 0..<count) {
        mockWebServer.enqueue(generateMockResponse(body = EMPTY_RESPONSE))
    }
}
