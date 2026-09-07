package io.ethers.providers

import io.ethers.core.types.Address
import io.ethers.core.types.CallRequest
import io.ethers.core.types.Hash
import io.ethers.core.types.transaction.TxBlob
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient as KtorHttpClient

class ProviderFillTransactionTest : FunSpec({
    context("manuallyFillTransaction") {
        lateinit var server: MockServer
        lateinit var client: HttpClient
        lateinit var provider: Provider

        beforeEach {
            server = mockServerHttp()
            client = HttpClient(server.url, KtorHttpClient())
            provider = Provider(client, 1L)
        }

        afterEach {
            client.close()
            server.stop()
        }

        test("blob fee cap gets 2x headroom over the next base fee per blob gas") {
            // fall back to manual filling, so the fee history below is what fills the blob fee cap
            server.enqueueJson("""{"jsonrpc":"2.0","id":0,"error":{"code":-32601,"message":"Method not found"}}""")
            server.enqueueJson(
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "oldestBlock": "0x1",
                    "baseFeePerGas": ["0x1", "0x1"],
                    "gasUsedRatio": [0.5],
                    "reward": null,
                    "baseFeePerBlobGas": ["0x64", "0x7b"],
                    "blobGasUsedRatio": [0.5]
                  }
                }
                """.trimIndent(),
            )

            // everything except the blob fee cap is set, so fee history is the only extra request
            val call = CallRequest()
                .from(Address("0x0000000000000000000000000000000000000001"))
                .to(Address("0x0000000000000000000000000000000000000002"))
                .gas(21000L)
                .nonce(0L)
                .gasFeeCap(bigIntegerOf(100L))
                .gasTipCap(bigIntegerOf(1L))
                .chainId(1L)
                .blobVersionedHashes(
                    listOf(Hash("0x0100000000000000000000000000000000000000000000000000000000000001")),
                )

            val tx = provider.fillTransaction(call).send().unwrap()

            // next base fee per blob gas is the last fee history entry, 0x7b = 123
            tx.shouldBeInstanceOf<TxBlob>().blobFeeCap shouldBe bigIntegerOf(246L)
        }

        test("explicit blob fee cap is left untouched") {
            server.enqueueJson("""{"jsonrpc":"2.0","id":0,"error":{"code":-32601,"message":"Method not found"}}""")

            val call = CallRequest()
                .from(Address("0x0000000000000000000000000000000000000001"))
                .to(Address("0x0000000000000000000000000000000000000002"))
                .gas(21000L)
                .nonce(0L)
                .gasFeeCap(bigIntegerOf(100L))
                .gasTipCap(bigIntegerOf(1L))
                .chainId(1L)
                .blobFeeCap(bigIntegerOf(7L))
                .blobVersionedHashes(
                    listOf(Hash("0x0100000000000000000000000000000000000000000000000000000000000001")),
                )

            val tx = provider.fillTransaction(call).send().unwrap()

            tx.shouldBeInstanceOf<TxBlob>().blobFeeCap shouldBe bigIntegerOf(7L)
        }
    }
})
