package io.ethers.aa

import io.ethers.aa.internal.BIG_ZERO_JSON
import io.ethers.aa.internal.asAddressValue
import io.ethers.aa.internal.asAuthorization
import io.ethers.aa.internal.asHexBytes
import io.ethers.aa.internal.asQuantity
import io.ethers.aa.internal.field
import io.ethers.aa.internal.fieldOrNull
import io.ethers.aa.internal.hex
import io.ethers.aa.internal.put
import io.ethers.aa.internal.quantity
import io.ethers.aa.internal.toRpcJson
import io.ethers.core.types.Bytes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Conversion between [UserOperation] and its ERC-7769 JSON-RPC representation.
 *
 * EntryPoint v0.6 operations use the `initCode` and `paymasterAndData` fields, while v0.7 and later use `factory`,
 * `factoryData`, `paymaster`, `paymasterVerificationGasLimit`, `paymasterPostOpGasLimit` and `paymasterData`.
 */
object UserOperationJson {
    /**
     * Encode [op] into the JSON-RPC object for EntryPoint [version].
     */
    fun encode(op: UserOperation, version: EntryPointVersion): JsonObject = buildJsonObject {
        put("sender", hex(op.sender))
        put("nonce", quantity(op.nonce))

        if (version.usesPackedUserOperation) {
            if (op.factory != null) {
                put("factory", hex(op.factory))
                put("factoryData", hex(op.factoryData ?: Bytes.EMPTY))
            }
        } else {
            put("initCode", hex(op.initCode))
        }

        put("callData", hex(op.callData))
        put("callGasLimit", quantity(op.callGasLimit))
        put("verificationGasLimit", quantity(op.verificationGasLimit))
        put("preVerificationGas", quantity(op.preVerificationGas))
        put("maxFeePerGas", quantity(op.maxFeePerGas))
        put("maxPriorityFeePerGas", quantity(op.maxPriorityFeePerGas))

        if (version.usesPackedUserOperation) {
            if (op.paymaster != null) {
                put("paymaster", hex(op.paymaster))
                put("paymasterVerificationGasLimit", op.paymasterVerificationGasLimit?.let(::quantity) ?: BIG_ZERO_JSON)
                put("paymasterPostOpGasLimit", op.paymasterPostOpGasLimit?.let(::quantity) ?: BIG_ZERO_JSON)
                put("paymasterData", hex(op.paymasterData ?: Bytes.EMPTY))
                if (op.paymasterSignature != null && !op.paymasterSignature.isEmpty) {
                    require(version.supportsPaymasterSignature) {
                        "EntryPoint $version does not support paymaster signatures"
                    }
                    put("paymasterSignature", hex(op.paymasterSignature))
                }
            }
        } else {
            put("paymasterAndData", hex(op.paymasterAndData(version)))
        }

        put("signature", hex(op.signature))

        if (op.eip7702Auth != null) {
            require(version.supportsEip7702) { "EntryPoint $version does not support EIP-7702 accounts" }
            put("eip7702Auth", op.eip7702Auth.toRpcJson())
        }
    }

    /**
     * Decode a JSON-RPC user operation. The layout is detected from the fields present: an `initCode` or
     * `paymasterAndData` field means an EntryPoint v0.6 operation.
     *
     * @throws IllegalArgumentException if a required field is missing or malformed.
     */
    fun decode(json: JsonElement): UserOperation {
        val obj = json.jsonObject
        val isV06 = obj.containsKey("initCode") || obj.containsKey("paymasterAndData")
        return if (isV06) decodeV06(obj) else decodePacked(obj)
    }

    private fun decodeV06(obj: JsonObject): UserOperation {
        return UserOperation.fromV06(
            sender = obj.field("sender").asAddressValue(),
            nonce = obj.field("nonce").asQuantity(),
            initCode = obj.fieldOrNull("initCode")?.asHexBytes() ?: Bytes.EMPTY,
            callData = obj.field("callData").asHexBytes(),
            callGasLimit = obj.field("callGasLimit").asQuantity(),
            verificationGasLimit = obj.field("verificationGasLimit").asQuantity(),
            preVerificationGas = obj.field("preVerificationGas").asQuantity(),
            maxFeePerGas = obj.field("maxFeePerGas").asQuantity(),
            maxPriorityFeePerGas = obj.field("maxPriorityFeePerGas").asQuantity(),
            paymasterAndData = obj.fieldOrNull("paymasterAndData")?.asHexBytes() ?: Bytes.EMPTY,
            signature = obj.fieldOrNull("signature")?.asHexBytes() ?: Bytes.EMPTY,
        )
    }

    private fun decodePacked(obj: JsonObject): UserOperation {
        val paymaster = obj.fieldOrNull("paymaster")?.asAddressValue()
        return UserOperation(
            sender = obj.field("sender").asAddressValue(),
            nonce = obj.field("nonce").asQuantity(),
            callData = obj.field("callData").asHexBytes(),
            callGasLimit = obj.field("callGasLimit").asQuantity(),
            verificationGasLimit = obj.field("verificationGasLimit").asQuantity(),
            preVerificationGas = obj.field("preVerificationGas").asQuantity(),
            maxFeePerGas = obj.field("maxFeePerGas").asQuantity(),
            maxPriorityFeePerGas = obj.field("maxPriorityFeePerGas").asQuantity(),
            signature = obj.fieldOrNull("signature")?.asHexBytes() ?: Bytes.EMPTY,
            factory = obj.fieldOrNull("factory")?.asAddressValue(),
            factoryData = obj.fieldOrNull("factoryData")?.asHexBytes(),
            paymaster = paymaster,
            paymasterVerificationGasLimit = paymaster?.let { obj.fieldOrNull("paymasterVerificationGasLimit")?.asQuantity() },
            paymasterPostOpGasLimit = paymaster?.let { obj.fieldOrNull("paymasterPostOpGasLimit")?.asQuantity() },
            paymasterData = paymaster?.let { obj.fieldOrNull("paymasterData")?.asHexBytes() },
            paymasterSignature = paymaster?.let { obj.fieldOrNull("paymasterSignature")?.asHexBytes() },
            eip7702Auth = obj.fieldOrNull("eip7702Auth")?.asAuthorization(),
        )
    }
}
