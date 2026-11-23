package io.ethers.aa.bundle

import io.ethers.core.Kotlinx
import io.ethers.core.types.RPCTransaction
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.types.tracers.CallTracer
import io.ethers.core.types.tracers.CallTracer.CallFrame
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * A bundle transaction recorded from Ethereum mainnet: the transaction, its receipt, and its `callTracer` trace
 * with logs.
 */
class BundleFixture(
    val name: String,
    val chainId: Long,
    val transaction: RPCTransaction,
    val receipt: TransactionReceipt,
    val trace: CallFrame,
) {
    override fun toString(): String = name

    companion object {
        val NAMES = listOf(
            "v06_deposited",
            "v06_paymaster",
            "v06_revert",
            "v07_multi",
            "v07_multi_deployed",
            "v07_no_bundle",
            "v07_postop_revert",
            "v07_postop_revert_multi",
            "v07_revert",
            "v08_multi_7702",
            "v08_multi_two_auths",
            "v08_revert",
            "v08_via_contract",
            "v09_deposited",
            "v09_paymaster",
        )

        val ALL: List<BundleFixture> by lazy { NAMES.map(::load) }

        fun load(name: String): BundleFixture {
            val text = BundleFixture::class.java.getResourceAsStream("/io/ethers/aa/bundles/$name.json")!!
                .bufferedReader()
                .use { it.readText() }

            val json = Kotlinx.DEFAULT.parseToJsonElement(text).jsonObject
            val trace = json["trace"]!!
            require(trace !is JsonNull) { "Fixture $name has no trace" }

            return BundleFixture(
                name = name,
                chainId = json["chainId"]!!.jsonPrimitive.long,
                transaction = Kotlinx.DEFAULT.decodeFromJsonElement(RPCTransaction.serializer(), json["transaction"]!!),
                receipt = Kotlinx.DEFAULT.decodeFromJsonElement(TransactionReceipt.serializer(), json["receipt"]!!),
                trace = Kotlinx.DEFAULT.decodeFromJsonElement(CallTracer(false, true).resultSerializer, trace),
            )
        }
    }
}
