package io.ethers.aa

import io.ethers.aa.internal.asQuantity
import io.ethers.aa.internal.required
import io.ethers.core.FastHex
import io.ethers.core.asAddress
import io.ethers.core.asBytes
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Serializes [UserOperation] in its ERC-7769 JSON-RPC form for EntryPoint [version].
 *
 * EntryPoint v0.6 operations use the `initCode` and `paymasterAndData` fields, while v0.7 and later use `factory`,
 * `factoryData`, `paymaster`, `paymasterVerificationGasLimit`, `paymasterPostOpGasLimit` and `paymasterData`, so
 * encoding needs a [version]. Without one, decoding detects the layout from the fields present: an `initCode` or
 * `paymasterAndData` field means a v0.6 operation.
 */
class UserOperationSerializer private constructor(private val version: EntryPointVersion?) : KSerializer<UserOperation> {
    override val descriptor = buildClassSerialDescriptor("UserOperation")

    override fun serialize(encoder: Encoder, value: UserOperation) {
        val version = this.version ?: throw SerializationException("Encoding a user operation needs an EntryPoint version")
        (encoder as JsonEncoder).encodeJsonElement(encode(value, version, encoder))
    }

    override fun deserialize(decoder: Decoder): UserOperation {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val isV06 = version?.let { !it.usesPackedUserOperation }
            ?: (obj.containsKey("initCode") || obj.containsKey("paymasterAndData"))

        var sender: Address? = null
        var nonce: BigInteger? = null
        var callData: Bytes? = null
        var callGasLimit: BigInteger? = null
        var verificationGasLimit: BigInteger? = null
        var preVerificationGas: BigInteger? = null
        var maxFeePerGas: BigInteger? = null
        var maxPriorityFeePerGas: BigInteger? = null
        var signature = Bytes.EMPTY
        var initCode = Bytes.EMPTY
        var paymasterAndData = Bytes.EMPTY
        var factory: Address? = null
        var factoryData: Bytes? = null
        var paymaster: Address? = null
        var paymasterVerificationGasLimit: BigInteger? = null
        var paymasterPostOpGasLimit: BigInteger? = null
        var paymasterData: Bytes? = null
        var paymasterSignature: Bytes? = null
        var eip7702Auth: Authorization? = null

        for ((key, element) in obj.entries) {
            if (element is JsonNull) continue
            when (key) {
                "sender" -> sender = element.jsonPrimitive.asAddress()
                "nonce" -> nonce = element.asQuantity()
                "callData" -> callData = element.jsonPrimitive.asBytes()
                "callGasLimit" -> callGasLimit = element.asQuantity()
                "verificationGasLimit" -> verificationGasLimit = element.asQuantity()
                "preVerificationGas" -> preVerificationGas = element.asQuantity()
                "maxFeePerGas" -> maxFeePerGas = element.asQuantity()
                "maxPriorityFeePerGas" -> maxPriorityFeePerGas = element.asQuantity()
                "signature" -> signature = element.jsonPrimitive.asBytes()
                "initCode" -> initCode = element.jsonPrimitive.asBytes()
                "paymasterAndData" -> paymasterAndData = element.jsonPrimitive.asBytes()
                "factory" -> factory = element.jsonPrimitive.asAddress()
                "factoryData" -> factoryData = element.jsonPrimitive.asBytes()
                "paymaster" -> paymaster = element.jsonPrimitive.asAddress()
                "paymasterVerificationGasLimit" -> paymasterVerificationGasLimit = element.asQuantity()
                "paymasterPostOpGasLimit" -> paymasterPostOpGasLimit = element.asQuantity()
                "paymasterData" -> paymasterData = element.jsonPrimitive.asBytes()
                "paymasterSignature" -> paymasterSignature = element.jsonPrimitive.asBytes()
                "eip7702Auth" -> eip7702Auth = jsonDecoder.json.decodeFromJsonElement(Authorization.serializer(), element)
            }
        }

        if (isV06) {
            return UserOperation.fromV06(
                sender = required(sender, "sender"),
                nonce = required(nonce, "nonce"),
                initCode = initCode,
                callData = required(callData, "callData"),
                callGasLimit = required(callGasLimit, "callGasLimit"),
                verificationGasLimit = required(verificationGasLimit, "verificationGasLimit"),
                preVerificationGas = required(preVerificationGas, "preVerificationGas"),
                maxFeePerGas = required(maxFeePerGas, "maxFeePerGas"),
                maxPriorityFeePerGas = required(maxPriorityFeePerGas, "maxPriorityFeePerGas"),
                paymasterAndData = paymasterAndData,
                signature = signature,
            )
        }

        val hasPaymaster = paymaster != null
        return UserOperation(
            sender = required(sender, "sender"),
            nonce = required(nonce, "nonce"),
            callData = required(callData, "callData"),
            callGasLimit = required(callGasLimit, "callGasLimit"),
            verificationGasLimit = required(verificationGasLimit, "verificationGasLimit"),
            preVerificationGas = required(preVerificationGas, "preVerificationGas"),
            maxFeePerGas = required(maxFeePerGas, "maxFeePerGas"),
            maxPriorityFeePerGas = required(maxPriorityFeePerGas, "maxPriorityFeePerGas"),
            signature = signature,
            factory = factory,
            factoryData = factoryData,
            paymaster = paymaster,
            paymasterVerificationGasLimit = paymasterVerificationGasLimit.takeIf { hasPaymaster },
            paymasterPostOpGasLimit = paymasterPostOpGasLimit.takeIf { hasPaymaster },
            paymasterData = paymasterData.takeIf { hasPaymaster },
            paymasterSignature = paymasterSignature.takeIf { hasPaymaster },
            eip7702Auth = eip7702Auth,
        )
    }

    private fun encode(op: UserOperation, version: EntryPointVersion, encoder: JsonEncoder): JsonObject = buildJsonObject {
        put("sender", JsonPrimitive(op.sender.toString()))
        put("nonce", quantity(op.nonce))

        if (version.usesPackedUserOperation) {
            if (op.factory != null) {
                put("factory", JsonPrimitive(op.factory.toString()))
                put("factoryData", JsonPrimitive((op.factoryData ?: Bytes.EMPTY).toString()))
            }
        } else {
            put("initCode", JsonPrimitive(op.initCode.toString()))
        }

        put("callData", JsonPrimitive(op.callData.toString()))
        put("callGasLimit", quantity(op.callGasLimit))
        put("verificationGasLimit", quantity(op.verificationGasLimit))
        put("preVerificationGas", quantity(op.preVerificationGas))
        put("maxFeePerGas", quantity(op.maxFeePerGas))
        put("maxPriorityFeePerGas", quantity(op.maxPriorityFeePerGas))

        if (version.usesPackedUserOperation) {
            if (op.paymaster != null) {
                put("paymaster", JsonPrimitive(op.paymaster.toString()))
                put("paymasterVerificationGasLimit", quantity(op.paymasterVerificationGasLimit ?: ZERO))
                put("paymasterPostOpGasLimit", quantity(op.paymasterPostOpGasLimit ?: ZERO))
                put("paymasterData", JsonPrimitive((op.paymasterData ?: Bytes.EMPTY).toString()))
                if (op.paymasterSignature != null && !op.paymasterSignature.isEmpty) {
                    require(version.supportsPaymasterSignature) { "EntryPoint $version does not support paymaster signatures" }
                    put("paymasterSignature", JsonPrimitive(op.paymasterSignature.toString()))
                }
            }
        } else {
            put("paymasterAndData", JsonPrimitive(op.paymasterAndData(version).toString()))
        }

        put("signature", JsonPrimitive(op.signature.toString()))

        if (op.eip7702Auth != null) {
            require(version.supportsEip7702) { "EntryPoint $version does not support EIP-7702 accounts" }
            put("eip7702Auth", encoder.json.encodeToJsonElement(Authorization.serializer(), op.eip7702Auth))
        }
    }

    private fun quantity(value: BigInteger): JsonPrimitive = JsonPrimitive(FastHex.encodeWithPrefix(value))

    companion object {
        private val ZERO = bigIntegerOf(0)

        /**
         * Decodes operations of any EntryPoint version, detecting the layout from the fields present. Cannot encode.
         */
        @JvmField
        val DETECT = UserOperationSerializer(null)

        private val BY_VERSION = EntryPointVersion.entries.associateWith { UserOperationSerializer(it) }

        /**
         * Return the serializer of operations of EntryPoint [version], or [DETECT] if the version is unknown.
         */
        @JvmStatic
        fun of(version: EntryPointVersion?): UserOperationSerializer = version?.let(BY_VERSION::getValue) ?: DETECT
    }
}
