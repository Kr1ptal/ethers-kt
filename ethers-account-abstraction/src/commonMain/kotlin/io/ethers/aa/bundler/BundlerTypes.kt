package io.ethers.aa.bundler

import io.ethers.aa.EntryPoint
import io.ethers.aa.UserOperation
import io.ethers.aa.UserOperationJson
import io.ethers.aa.internal.asAddressValue
import io.ethers.aa.internal.asHashValue
import io.ethers.aa.internal.asQuantity
import io.ethers.aa.internal.asQuantityLong
import io.ethers.aa.internal.field
import io.ethers.aa.internal.fieldOrNull
import io.ethers.core.Kotlinx
import io.ethers.core.types.Address
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.TransactionReceipt
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Gas limits estimated by `eth_estimateUserOperationGas`.
 *
 * The paymaster limits are only returned for EntryPoint v0.7 and later, and only when the operation has a
 * paymaster.
 */
data class UserOperationGasEstimate(
    val preVerificationGas: BigInteger,
    val verificationGasLimit: BigInteger,
    val callGasLimit: BigInteger,
    val paymasterVerificationGasLimit: BigInteger? = null,
    val paymasterPostOpGasLimit: BigInteger? = null,
) {
    internal companion object {
        fun fromJson(json: JsonElement): UserOperationGasEstimate {
            val obj = json.jsonObject
            return UserOperationGasEstimate(
                preVerificationGas = obj.field("preVerificationGas").asQuantity(),
                verificationGasLimit = obj.field("verificationGasLimit").asQuantity(),
                callGasLimit = obj.field("callGasLimit").asQuantity(),
                paymasterVerificationGasLimit = obj.fieldOrNull("paymasterVerificationGasLimit")?.asQuantity(),
                paymasterPostOpGasLimit = obj.fieldOrNull("paymasterPostOpGasLimit")?.asQuantity(),
            )
        }
    }
}

/**
 * A user operation as returned by `eth_getUserOperationByHash`.
 *
 * [blockHash], [blockNumber] and [transactionHash] are null while the operation is still pending in the bundler's
 * mempool.
 */
data class UserOperationByHash(
    val userOperation: UserOperation,
    val entryPoint: Address,
    val blockNumber: Long?,
    val blockHash: Hash?,
    val transactionHash: Hash?,
) {
    internal companion object {
        fun fromJson(json: JsonElement): UserOperationByHash {
            val obj = json.jsonObject
            val entryPoint = obj.field("entryPoint").asAddressValue()
            return UserOperationByHash(
                userOperation = UserOperationJson.decode(obj.field("userOperation"), EntryPoint.canonical(entryPoint)?.version),
                entryPoint = entryPoint,
                blockNumber = obj.fieldOrNull("blockNumber")?.asQuantityLong(),
                blockHash = obj.fieldOrNull("blockHash")?.asHashValue(),
                transactionHash = obj.fieldOrNull("transactionHash")?.asHashValue(),
            )
        }
    }
}

/**
 * Receipt of an included user operation, as returned by `eth_getUserOperationReceipt`.
 *
 * @property paymaster the paymaster that paid for the operation, or null if the account paid itself.
 * @property success whether the operation's execution succeeded. A failed operation is still included, and still
 * pays for its gas.
 * @property reason the revert reason of a failed execution, if the bundler reports one.
 * @property logs the logs emitted by this operation only, as opposed to [receipt] which covers the whole bundle.
 * @property receipt receipt of the bundle transaction that included the operation.
 */
data class UserOperationReceipt(
    val userOpHash: Hash,
    val entryPoint: Address,
    val sender: Address,
    val nonce: BigInteger,
    val paymaster: Address?,
    val actualGasCost: BigInteger,
    val actualGasUsed: BigInteger,
    val success: Boolean,
    val reason: String?,
    val logs: List<Log>,
    val receipt: TransactionReceipt,
) {
    internal companion object {
        private val LOGS = ListSerializer(Log.serializer())

        fun fromJson(json: JsonElement): UserOperationReceipt {
            val obj = json.jsonObject
            val paymaster = obj.fieldOrNull("paymaster")?.asAddressValue()
            val success = obj.field("success").jsonPrimitive.let {
                if (it.isString) it.content.equals("true", ignoreCase = true) || it.content == "0x1" else it.content == "true"
            }

            return UserOperationReceipt(
                userOpHash = obj.field("userOpHash").asHashValue(),
                entryPoint = obj.field("entryPoint").asAddressValue(),
                sender = obj.field("sender").asAddressValue(),
                nonce = obj.field("nonce").asQuantity(),
                paymaster = if (paymaster == Address.ZERO) null else paymaster,
                actualGasCost = obj.field("actualGasCost").asQuantity(),
                actualGasUsed = obj.field("actualGasUsed").asQuantity(),
                success = success,
                reason = obj.fieldOrNull("reason")?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
                logs = obj.fieldOrNull("logs")?.let { Kotlinx.DEFAULT.decodeFromJsonElement(LOGS, it) } ?: emptyList(),
                receipt = Kotlinx.DEFAULT.decodeFromJsonElement(TransactionReceipt.serializer(), obj.field("receipt")),
            )
        }
    }
}

internal fun <T : Any> JsonElement.ifNotNull(parser: (JsonElement) -> T): T? = if (this is JsonNull) null else parser(this)
