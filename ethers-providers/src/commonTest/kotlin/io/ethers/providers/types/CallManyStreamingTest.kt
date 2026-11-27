package io.ethers.providers.types

import io.ethers.core.Kotlinx
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.BlockIdSerializer
import io.ethers.core.types.BlockOverride
import io.ethers.core.types.BlockOverrideSerializer
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.core.types.CallRequestSerializer
import io.ethers.core.types.Hash
import io.github.artificialpb.bignum.BigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [CallManyBundleSerializer] and [CallManyContextSerializer] write exactly the text their earlier versions wrote by
 * building a JSON tree, kept below, for every combination of their fields. The legacy versions are those on master. The text of each nested call and block
 * override is checked against their own earlier serializers in ethers-core.
 */
class CallManyStreamingTest : FunSpec({
    val calls = listOf(
        CallRequest {},
        CallRequest {
            from = Address.ZERO
            to = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")
            gas = Long.MAX_VALUE
            value = BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935")
            data = Bytes("0x70a082310000000000000000000000000d4a11d5eeaac28ec3f61d100daf4d40471f1852")
        },
        CallRequest {
            to = Address("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")
            nonce = 0
            blobVersionedHashes = emptyList()
        },
    )
    val blockOverrides = listOf(
        null,
        BlockOverride {},
        BlockOverride {
            number = 1234
            time = 5678
        },
    )

    test("CallManyBundle") {
        val transactionLists = listOf(emptyList(), calls.take(1), calls)
        for (transactions in transactionLists) {
            for (blockOverride in blockOverrides) {
                encodesAsLegacy(
                    CallManyBundleSerializer,
                    LegacyCallManyBundleSerializer,
                    CallManyBundle(transactions, blockOverride),
                )
            }
        }
    }

    test("CallManyContext") {
        val blocks = listOf(
            BlockId.LATEST,
            BlockId.Number(0),
            BlockId.Number(Long.MAX_VALUE),
            BlockId.Hash(Hash("0x1b4dcbdc1bc1f8a01b1f1ab5e13b3bfe25fbd29e4f99b4a5e6e1c8d5c5a39f27")),
        )
        for (block in blocks) {
            for (transactionIndex in intArrayOf(-1, 0, 1, Int.MAX_VALUE, Int.MIN_VALUE)) {
                encodesAsLegacy(
                    CallManyContextSerializer,
                    LegacyCallManyContextSerializer,
                    CallManyContext(block, transactionIndex),
                )
            }
        }
    }
})

private fun <T> encodesAsLegacy(serializer: KSerializer<T>, legacy: KSerializer<T>, value: T) {
    val expected = Kotlinx.DEFAULT.encodeToString(legacy, value)
    Kotlinx.DEFAULT.encodeToString(serializer, value) shouldBe expected
    Kotlinx.DEFAULT.encodeToString(ListSerializer(serializer), listOf(value, value)) shouldBe "[$expected,$expected]"
    Kotlinx.DEFAULT.encodeToJsonElement(serializer, value) shouldBe Kotlinx.DEFAULT.encodeToJsonElement(legacy, value)
}

// The serializers as they were before they were made to write their fields directly, unchanged but for their names.

private object LegacyCallManyBundleSerializer : KSerializer<CallManyBundle> {
    override val descriptor = buildClassSerialDescriptor("CallManyBundle")

    override fun serialize(encoder: Encoder, value: CallManyBundle) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put(
                    "transactions",
                    buildJsonArray {
                        for (tx in value.transactions) {
                            add(jsonEncoder.json.encodeToJsonElement(CallRequestSerializer, tx))
                        }
                    },
                )
                if (value.blockOverride != null) {
                    put("blockOverride", jsonEncoder.json.encodeToJsonElement(BlockOverrideSerializer, value.blockOverride))
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): CallManyBundle = throw UnsupportedOperationException()
}

private object LegacyCallManyContextSerializer : KSerializer<CallManyContext> {
    override val descriptor = buildClassSerialDescriptor("CallManyContext")

    override fun serialize(encoder: Encoder, value: CallManyContext) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("blockNumber", jsonEncoder.json.encodeToJsonElement(BlockIdSerializer, value.blockNumber))
                put("transactionIndex", value.transactionIndex)
            },
        )
    }

    override fun deserialize(decoder: Decoder): CallManyContext = throw UnsupportedOperationException()
}
