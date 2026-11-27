package io.ethers.aa.bundler

import io.ethers.aa.EntryPoint
import io.ethers.aa.UserOperation
import io.ethers.aa.internal.asQuantity
import io.ethers.aa.internal.required
import io.ethers.core.asAddress
import io.ethers.core.asHash
import io.ethers.core.asHexLong
import io.ethers.core.types.Address
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.TransactionReceipt
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Gas limits estimated by `eth_estimateUserOperationGas`.
 *
 * The paymaster limits are only returned for EntryPoint v0.7 and later, and only when the operation has a
 * paymaster.
 */
@Serializable(with = UserOperationGasEstimateSerializer::class)
data class UserOperationGasEstimate(
    val preVerificationGas: BigInteger,
    val verificationGasLimit: BigInteger,
    val callGasLimit: BigInteger,
    val paymasterVerificationGasLimit: BigInteger? = null,
    val paymasterPostOpGasLimit: BigInteger? = null,
)

/**
 * A user operation as returned by `eth_getUserOperationByHash`.
 *
 * [blockHash], [blockNumber] and [transactionHash] are null while the operation is still pending in the bundler's
 * mempool.
 */
@Serializable(with = UserOperationByHashSerializer::class)
data class UserOperationByHash(
    val userOperation: UserOperation,
    val entryPoint: Address,
    val blockNumber: Long?,
    val blockHash: Hash?,
    val transactionHash: Hash?,
)

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
@Serializable(with = UserOperationReceiptSerializer::class)
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
)

// Bundlers are not consistent about quantities: most return hex strings, some JSON numbers or decimal strings. The
// quantities are therefore read with the lenient `asQuantity`.

object UserOperationGasEstimateSerializer : KSerializer<UserOperationGasEstimate> {
    override val descriptor = buildClassSerialDescriptor("UserOperationGasEstimate")

    override fun serialize(encoder: Encoder, value: UserOperationGasEstimate) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): UserOperationGasEstimate {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

        var preVerificationGas: BigInteger? = null
        var verificationGasLimit: BigInteger? = null
        var callGasLimit: BigInteger? = null
        var paymasterVerificationGasLimit: BigInteger? = null
        var paymasterPostOpGasLimit: BigInteger? = null

        for ((key, element) in obj.entries) {
            if (element is JsonNull) continue
            when (key) {
                "preVerificationGas" -> preVerificationGas = element.asQuantity()
                "verificationGasLimit" -> verificationGasLimit = element.asQuantity()
                "callGasLimit" -> callGasLimit = element.asQuantity()
                "paymasterVerificationGasLimit" -> paymasterVerificationGasLimit = element.asQuantity()
                "paymasterPostOpGasLimit" -> paymasterPostOpGasLimit = element.asQuantity()
            }
        }

        return UserOperationGasEstimate(
            preVerificationGas = required(preVerificationGas, "preVerificationGas"),
            verificationGasLimit = required(verificationGasLimit, "verificationGasLimit"),
            callGasLimit = required(callGasLimit, "callGasLimit"),
            paymasterVerificationGasLimit = paymasterVerificationGasLimit,
            paymasterPostOpGasLimit = paymasterPostOpGasLimit,
        )
    }
}

object UserOperationByHashSerializer : KSerializer<UserOperationByHash> {
    override val descriptor = buildClassSerialDescriptor("UserOperationByHash")

    override fun serialize(encoder: Encoder, value: UserOperationByHash) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): UserOperationByHash {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

        var userOperation: JsonElement? = null
        var entryPoint: Address? = null
        var blockNumber: Long? = null
        var blockHash: Hash? = null
        var transactionHash: Hash? = null

        for ((key, element) in obj.entries) {
            if (element is JsonNull) continue
            when (key) {
                "userOperation" -> userOperation = element
                "entryPoint" -> entryPoint = element.jsonPrimitive.asAddress()
                "blockNumber" -> blockNumber = element.jsonPrimitive.asHexLong()
                "blockHash" -> blockHash = element.jsonPrimitive.asHash()
                "transactionHash" -> transactionHash = element.jsonPrimitive.asHash()
            }
        }

        val entryPointAddress = required(entryPoint, "entryPoint")
        // the operation's layout depends on its EntryPoint, which may come after it in the object
        val layout = EntryPoint.canonical(entryPointAddress)?.version
        return UserOperationByHash(
            userOperation = UserOperation.fromJson(required(userOperation, "userOperation"), layout),
            entryPoint = entryPointAddress,
            blockNumber = blockNumber,
            blockHash = blockHash,
            transactionHash = transactionHash,
        )
    }
}

object UserOperationReceiptSerializer : KSerializer<UserOperationReceipt> {
    override val descriptor = buildClassSerialDescriptor("UserOperationReceipt")

    override fun serialize(encoder: Encoder, value: UserOperationReceipt) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): UserOperationReceipt {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        var userOpHash: Hash? = null
        var entryPoint: Address? = null
        var sender: Address? = null
        var nonce: BigInteger? = null
        var paymaster: Address? = null
        var actualGasCost: BigInteger? = null
        var actualGasUsed: BigInteger? = null
        var success: Boolean? = null
        var reason: String? = null
        var logs = emptyList<Log>()
        var receipt: TransactionReceipt? = null

        for ((key, element) in obj.entries) {
            if (element is JsonNull) continue
            when (key) {
                "userOpHash" -> userOpHash = element.jsonPrimitive.asHash()
                "entryPoint" -> entryPoint = element.jsonPrimitive.asAddress()
                "sender" -> sender = element.jsonPrimitive.asAddress()
                "nonce" -> nonce = element.asQuantity()
                // bundlers report a self-paying account with the zero address
                "paymaster" -> paymaster = element.jsonPrimitive.asAddress().takeIf { it != Address.ZERO }
                "actualGasCost" -> actualGasCost = element.asQuantity()
                "actualGasUsed" -> actualGasUsed = element.asQuantity()
                "success" -> success = element.jsonPrimitive.content.let { it == "true" || it == "0x1" }
                "reason" -> reason = element.jsonPrimitive.content.takeIf { it.isNotEmpty() }
                "logs" -> logs = element.jsonArray.map { jsonDecoder.json.decodeFromJsonElement(Log.serializer(), it) }
                "receipt" -> receipt = jsonDecoder.json.decodeFromJsonElement(TransactionReceipt.serializer(), element)
            }
        }

        return UserOperationReceipt(
            userOpHash = required(userOpHash, "userOpHash"),
            entryPoint = required(entryPoint, "entryPoint"),
            sender = required(sender, "sender"),
            nonce = required(nonce, "nonce"),
            paymaster = paymaster,
            actualGasCost = required(actualGasCost, "actualGasCost"),
            actualGasUsed = required(actualGasUsed, "actualGasUsed"),
            success = required(success, "success"),
            reason = reason,
            logs = logs,
            receipt = required(receipt, "receipt"),
        )
    }
}
