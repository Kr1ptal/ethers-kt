package io.ethers.aa.paymaster

import io.ethers.aa.EntryPoint
import io.ethers.aa.EntryPointVersion
import io.ethers.aa.UserOperation
import io.ethers.aa.internal.asQuantity
import io.ethers.core.FastHex
import io.ethers.core.asAddress
import io.ethers.core.asBytes
import io.ethers.core.asHexByteArray
import io.ethers.core.toJsonElement
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcCall
import io.ethers.providers.types.RpcRequest
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
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
        return RpcCall(client, "pm_getPaymasterStubData", params, PaymasterStubDataSerializer(entryPoint.version))
    }

    override fun getPaymasterData(
        userOperation: UserOperation,
        entryPoint: EntryPoint,
        chainId: Long,
        context: Map<String, Any?>?,
    ): RpcRequest<PaymasterData, RpcError> {
        val params = params(userOperation, entryPoint, chainId, context)
        return RpcCall(client, "pm_getPaymasterData", params, PaymasterDataSerializer(entryPoint.version))
    }

    private fun params(op: UserOperation, entryPoint: EntryPoint, chainId: Long, context: Map<String, Any?>?): Array<Any> {
        return arrayOf(
            op.toJson(entryPoint.version),
            entryPoint.address,
            FastHex.encodeWithPrefix(chainId),
            context?.toJsonElement() ?: JsonObject(emptyMap()),
        )
    }
}

/**
 * Decodes `pm_getPaymasterStubData` responses for EntryPoint [version], whose layout of `paymasterAndData` it needs.
 *
 * ERC-7677 returns `paymasterAndData` for EntryPoint v0.6, and `paymaster` with `paymasterData` for later versions.
 * Some services return the v0.6 form for every version, so both are accepted, and gas limits packed into a v0.7+
 * `paymasterAndData` are unpacked.
 */
class PaymasterStubDataSerializer(private val version: EntryPointVersion) : KSerializer<PaymasterStubData> {
    override val descriptor = buildClassSerialDescriptor("PaymasterStubData")

    override fun serialize(encoder: Encoder, value: PaymasterStubData) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): PaymasterStubData {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

        val fields = PaymasterFields()
        var sponsor: PaymasterSponsor? = null
        var isFinal = false

        for ((key, element) in obj.entries) {
            if (element is JsonNull) continue
            when (key) {
                "sponsor" -> sponsor = parseSponsor(element.jsonObject)
                "isFinal" -> isFinal = element.jsonPrimitive.content == "true"
                else -> fields.read(key, element)
            }
        }

        val (paymaster, data) = fields.paymasterAndData(version)
        return PaymasterStubData(
            paymaster = paymaster,
            paymasterData = data,
            paymasterVerificationGasLimit = fields.paymasterVerificationGasLimit,
            paymasterPostOpGasLimit = fields.paymasterPostOpGasLimit,
            sponsor = sponsor,
            isFinal = isFinal,
        )
    }

    private fun parseSponsor(obj: JsonObject): PaymasterSponsor {
        var name = ""
        var icon: String? = null
        for ((key, element) in obj.entries) {
            if (element is JsonNull) continue
            when (key) {
                "name" -> name = element.jsonPrimitive.content
                "icon" -> icon = element.jsonPrimitive.content
            }
        }
        return PaymasterSponsor(name, icon)
    }
}

/**
 * Decodes `pm_getPaymasterData` responses for EntryPoint [version], see [PaymasterStubDataSerializer].
 */
class PaymasterDataSerializer(private val version: EntryPointVersion) : KSerializer<PaymasterData> {
    override val descriptor = buildClassSerialDescriptor("PaymasterData")

    override fun serialize(encoder: Encoder, value: PaymasterData) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): PaymasterData {
        val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

        val fields = PaymasterFields()
        for ((key, element) in obj.entries) {
            if (element !is JsonNull) fields.read(key, element)
        }

        val (paymaster, data) = fields.paymasterAndData(version)
        return PaymasterData(paymaster, data)
    }
}

/**
 * The paymaster fields of a response, in either the split or the `paymasterAndData` form.
 */
private class PaymasterFields {
    private var paymaster: Address? = null
    private var paymasterData: Bytes? = null
    private var packed: ByteArray? = null
    var paymasterVerificationGasLimit: BigInteger? = null
        private set
    var paymasterPostOpGasLimit: BigInteger? = null
        private set

    fun read(key: String, element: JsonElement) {
        when (key) {
            "paymaster" -> paymaster = element.jsonPrimitive.asAddress()
            "paymasterData" -> paymasterData = element.jsonPrimitive.asBytes()
            "paymasterAndData" -> packed = element.jsonPrimitive.asHexByteArray()
            "paymasterVerificationGasLimit" -> paymasterVerificationGasLimit = element.asQuantity()
            "paymasterPostOpGasLimit" -> paymasterPostOpGasLimit = element.asQuantity()
        }
    }

    fun paymasterAndData(version: EntryPointVersion): Pair<Address, Bytes> {
        paymaster?.let { return it to (paymasterData ?: Bytes.EMPTY) }

        val bytes = packed ?: throw SerializationException("Paymaster response has neither 'paymaster' nor 'paymasterAndData'")
        if (bytes.size < 20) throw SerializationException("paymasterAndData is shorter than an address")

        // only v0.7 and later pack the paymaster gas limits, a bare address has no data either way
        val packsGasLimits = version.usesPackedUserOperation && bytes.size > 20
        if (packsGasLimits) {
            if (bytes.size < UserOperation.PAYMASTER_DATA_OFFSET) {
                throw SerializationException("paymasterAndData of EntryPoint $version must include the paymaster gas limits")
            }
            if (paymasterVerificationGasLimit == null) paymasterVerificationGasLimit = UserOperation.uint(bytes, 20, 36)
            if (paymasterPostOpGasLimit == null) paymasterPostOpGasLimit = UserOperation.uint(bytes, 36, 52)
        }

        val dataStart = if (packsGasLimits) UserOperation.PAYMASTER_DATA_OFFSET else 20
        return Address(bytes.copyOfRange(0, 20)) to Bytes(bytes.copyOfRange(dataStart, bytes.size))
    }
}
