package io.ethers.aa.paymaster

import io.ethers.aa.EntryPoint
import io.ethers.aa.EntryPointVersion
import io.ethers.aa.UserOperation
import io.ethers.aa.UserOperationJson
import io.ethers.aa.internal.asAddressValue
import io.ethers.aa.internal.asHexBytes
import io.ethers.aa.internal.asQuantity
import io.ethers.aa.internal.bytesToUint
import io.ethers.aa.internal.fieldOrNull
import io.ethers.aa.internal.quantity
import io.ethers.core.toJsonElement
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.map
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Who sponsors a user operation, for wallets to show to the user.
 */
data class PaymasterSponsor(val name: String, val icon: String? = null)

/**
 * Stub paymaster fields for gas estimation, returned by `pm_getPaymasterStubData`.
 *
 * The stub [paymasterData] must make the paymaster's validation cost as much gas as the final data will.
 *
 * @property isFinal if true, these are already the final paymaster fields and `pm_getPaymasterData` must not be
 * called.
 * @property paymasterVerificationGasLimit the paymaster's own estimate of its validation gas, for EntryPoint v0.7 and
 * later. If null, it is left to the bundler to estimate.
 */
data class PaymasterStubData(
    val paymaster: Address,
    val paymasterData: Bytes,
    val paymasterVerificationGasLimit: BigInteger? = null,
    val paymasterPostOpGasLimit: BigInteger? = null,
    val sponsor: PaymasterSponsor? = null,
    val isFinal: Boolean = false,
)

/**
 * Final paymaster fields, returned by `pm_getPaymasterData`, to be put into the user operation before it is signed.
 */
data class PaymasterData(
    val paymaster: Address,
    val paymasterData: Bytes,
)

/**
 * Supplies the paymaster fields of a user operation. [PaymasterClient] implements it over the ERC-7677 RPC API,
 * but any custom paymaster integration can implement it too.
 */
interface PaymasterApi {
    /**
     * Get stub paymaster fields to estimate the gas of [userOperation] with.
     */
    fun getPaymasterStubData(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        chainId: Long,
    ): RpcRequest<PaymasterStubData, RpcError> = getPaymasterStubData(userOperation, entryPoint, chainId, null)

    /**
     * Like [getPaymasterStubData], passing paymaster-specific [context], e.g. a sponsorship policy ID.
     */
    fun getPaymasterStubData(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        chainId: Long,
        context: Map<String, Any?>?,
    ): RpcRequest<PaymasterStubData, RpcError>

    /**
     * Get the final paymaster fields of [userOperation], whose gas limits are already estimated.
     */
    fun getPaymasterData(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        chainId: Long,
    ): RpcRequest<PaymasterData, RpcError> = getPaymasterData(userOperation, entryPoint, chainId, null)

    /**
     * Like [getPaymasterData], passing paymaster-specific [context], e.g. a sponsorship policy ID.
     */
    fun getPaymasterData(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        chainId: Long,
        context: Map<String, Any?>?,
    ): RpcRequest<PaymasterData, RpcError>
}

/**
 * [PaymasterApi] over a [JsonRpcClient] connected to an ERC-7677 paymaster service.
 */
class PaymasterClient(val client: JsonRpcClient) : PaymasterApi {
    override fun getPaymasterStubData(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        chainId: Long,
        context: Map<String, Any?>?,
    ): RpcRequest<PaymasterStubData, RpcError> {
        val params = params(userOperation, entryPoint, chainId, context)
        val deserializer = JsonElement.serializer().map { parseStubData(it.jsonObject, entryPoint.version) }
        return RpcCall(client, "pm_getPaymasterStubData", params, deserializer)
    }

    override fun getPaymasterData(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        chainId: Long,
        context: Map<String, Any?>?,
    ): RpcRequest<PaymasterData, RpcError> {
        val params = params(userOperation, entryPoint, chainId, context)
        val deserializer = JsonElement.serializer().map {
            val (paymaster, data) = parsePaymasterAndData(it.jsonObject, entryPoint.version)
            PaymasterData(paymaster, data)
        }
        return RpcCall(client, "pm_getPaymasterData", params, deserializer)
    }

    private fun params(op: UserOperation, entryPoint: EntryPoint, chainId: Long, context: Map<String, Any?>?): Array<Any> {
        return arrayOf(
            UserOperationJson.encode(op, entryPoint.version),
            entryPoint.address,
            quantity(chainId),
            context?.toJsonElement() ?: JsonObject(emptyMap()),
        )
    }

    internal companion object {
        fun parseStubData(obj: JsonObject, version: EntryPointVersion): PaymasterStubData {
            val (paymaster, data) = parsePaymasterAndData(obj, version)
            val packedLimits = packedPaymasterGasLimits(obj, version)
            val sponsor = obj.fieldOrNull("sponsor")?.jsonObject?.let { sponsor ->
                PaymasterSponsor(
                    name = sponsor.fieldOrNull("name")?.jsonPrimitive?.content ?: "",
                    icon = sponsor.fieldOrNull("icon")?.jsonPrimitive?.content,
                )
            }

            return PaymasterStubData(
                paymaster = paymaster,
                paymasterData = data,
                paymasterVerificationGasLimit = obj.fieldOrNull("paymasterVerificationGasLimit")?.asQuantity()
                    ?: packedLimits?.first,
                paymasterPostOpGasLimit = obj.fieldOrNull("paymasterPostOpGasLimit")?.asQuantity()
                    ?: packedLimits?.second,
                sponsor = sponsor,
                isFinal = obj.fieldOrNull("isFinal")?.jsonPrimitive?.content == "true",
            )
        }

        /**
         * Return the gas limits packed into a v0.7+ `paymasterAndData` field, if the response uses that form.
         */
        private fun packedPaymasterGasLimits(obj: JsonObject, version: EntryPointVersion): Pair<BigInteger, BigInteger>? {
            if (!version.usesPackedUserOperation || obj.fieldOrNull("paymaster") != null) return null
            val bytes = obj.fieldOrNull("paymasterAndData")?.asHexBytes()?.asByteArray() ?: return null
            if (bytes.size < 52) return null
            return bytesToUint(bytes, 20, 36) to bytesToUint(bytes, 36, 52)
        }

        /**
         * ERC-7677 returns `paymasterAndData` for EntryPoint v0.6, and `paymaster` with `paymasterData` for later
         * versions. Some services return the v0.6 form for every version, so both are accepted.
         */
        fun parsePaymasterAndData(obj: JsonObject, version: EntryPointVersion): Pair<Address, Bytes> {
            val paymaster = obj.fieldOrNull("paymaster")
            if (paymaster != null && paymaster !is JsonNull) {
                return paymaster.asAddressValue() to (obj.fieldOrNull("paymasterData")?.asHexBytes() ?: Bytes.EMPTY)
            }

            val paymasterAndData = requireNotNull(obj.fieldOrNull("paymasterAndData")) {
                "Paymaster response has neither 'paymaster' nor 'paymasterAndData'"
            }.asHexBytes().asByteArray()
            require(paymasterAndData.size >= 20) { "paymasterAndData is shorter than an address" }
            require(version == EntryPointVersion.V0_6 || paymasterAndData.size >= 52 || paymasterAndData.size == 20) {
                "paymasterAndData of EntryPoint $version must include the paymaster gas limits"
            }

            val dataStart = if (version == EntryPointVersion.V0_6 || paymasterAndData.size == 20) 20 else 52
            return Address(paymasterAndData.copyOfRange(0, 20)) to Bytes(paymasterAndData.copyOfRange(dataStart, paymasterAndData.size))
        }
    }
}
