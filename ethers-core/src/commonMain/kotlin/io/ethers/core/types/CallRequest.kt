package io.ethers.core.types

import io.ethers.core.FastHex
import io.ethers.core.types.transaction.ChainId
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.core.types.transaction.TxAccessList
import io.ethers.core.types.transaction.TxBlob
import io.ethers.core.types.transaction.TxDynamicFee
import io.ethers.core.types.transaction.TxLegacy
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlin.jvm.JvmSynthetic

/**
 * An immutable request to call, estimate, or fill a transaction. Every field is optional, so a provider can fill
 * in whatever is missing (see `Middleware.fillTransaction`).
 *
 * Build one with the DSL, or with a [Builder] when the fields are set across several steps:
 * ```kotlin
 * val request = CallRequest {
 *     to = contract
 *     data = calldata
 * }
 *
 * val request = CallRequest.Builder().to(contract).data(calldata).build()
 * ```
 *
 * Derive a changed copy with [copy] (or [toBuilder] from Java); the original is never modified:
 * ```kotlin
 * val withGas = request.copy { gas = 100_000 }
 * ```
 * */
@Serializable(with = CallRequestSerializer::class)
class CallRequest private constructor(builder: Builder) : IntoCallRequest {
    val from: Address? = builder.from
    val to: Address? = builder.to
    val gas: Long = builder.gas
    val gasPrice: BigInteger? = builder.gasPrice
    val gasFeeCap: BigInteger? = builder.gasFeeCap
    val gasTipCap: BigInteger? = builder.gasTipCap
    val value: BigInteger? = builder.value
    val nonce: Long = builder.nonce
    val data: Bytes? = builder.data
    val accessList: List<AccessList.Item> = builder.accessList
    val chainId: Long = builder.chainId
    val blobFeeCap: BigInteger? = builder.blobFeeCap
    val blobVersionedHashes: List<Hash>? = builder.blobVersionedHashes

    override fun toCallRequest() = this

    /**
     * Create a [Builder] initialized with the fields of **this** request.
     * */
    fun toBuilder(): Builder = Builder(this)

    /**
     * Return a copy of **this** request with the changes applied by [builder]. **This** request is not modified.
     * */
    @JvmSynthetic
    inline fun copy(builder: Builder.() -> Unit): CallRequest = Builder(this).apply(builder).build()

    /**
     * Try to convert [CallRequest] to [TransactionUnsigned], if all required fields are set. Returns null if
     * conversion is not possible.
     * */
    // IMPORTANT: Checks in this function must be kept in sync with `Provider#manuallyFillTransaction`.
    fun toUnsignedTransactionOrNull(): TransactionUnsigned? {
        if (nonce < 0) {
            return null
        }

        if (gas < 21000L) {
            return null
        }

        when {
            gasFeeCap != null && gasTipCap != null && blobVersionedHashes != null -> {
                if (to == null || blobFeeCap == null || !ChainId.isValid(chainId)) {
                    return null
                }

                return TxBlob(
                    to = to,
                    value = value ?: bigIntegerOf(0),
                    nonce = nonce,
                    gas = gas,
                    gasFeeCap = gasFeeCap,
                    gasTipCap = gasTipCap,
                    data = data,
                    chainId = chainId,
                    accessList = accessList,
                    blobFeeCap = blobFeeCap,
                    blobVersionedHashes = blobVersionedHashes,
                )
            }

            gasFeeCap != null && gasTipCap != null && blobVersionedHashes == null -> {
                if (!ChainId.isValid(chainId)) {
                    return null
                }

                return TxDynamicFee(
                    to = to,
                    value = value ?: bigIntegerOf(0),
                    nonce = nonce,
                    gas = gas,
                    gasFeeCap = gasFeeCap,
                    gasTipCap = gasTipCap,
                    data = data,
                    chainId = chainId,
                    accessList = accessList,
                )
            }

            gasPrice != null && accessList.isNotEmpty() -> {
                if (!ChainId.isValid(chainId)) {
                    return null
                }

                return TxAccessList(
                    to = to,
                    value = value ?: bigIntegerOf(0),
                    nonce = nonce,
                    gas = gas,
                    gasPrice = gasPrice,
                    data = data,
                    chainId = chainId,
                    accessList = accessList,
                )
            }

            gasPrice != null && accessList.isEmpty() -> {
                return TxLegacy(
                    to = to,
                    value = value ?: bigIntegerOf(0),
                    nonce = nonce,
                    gas = gas,
                    gasPrice = gasPrice,
                    data = data,
                    chainId = chainId,
                )
            }
        }

        return null
    }

    override fun toString(): String {
        return "CallRequest(from=$from, to=$to, gas=$gas, gasPrice=$gasPrice, gasFeeCap=$gasFeeCap, gasTipCap=$gasTipCap, value=$value, nonce=$nonce, data=$data, accessList=$accessList, chainId=$chainId, blobFeeCap=$blobFeeCap, blobVersionedHashes=$blobVersionedHashes)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CallRequest

        if (from != other.from) return false
        if (to != other.to) return false
        if (gas != other.gas) return false
        if (gasPrice != other.gasPrice) return false
        if (gasFeeCap != other.gasFeeCap) return false
        if (gasTipCap != other.gasTipCap) return false
        if (value != other.value) return false
        if (nonce != other.nonce) return false
        if (data != other.data) return false
        if (accessList != other.accessList) return false
        if (chainId != other.chainId) return false
        if (blobFeeCap != other.blobFeeCap) return false
        if (blobVersionedHashes != other.blobVersionedHashes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from?.hashCode() ?: 0
        result = 31 * result + (to?.hashCode() ?: 0)
        result = 31 * result + gas.hashCode()
        result = 31 * result + (gasPrice?.hashCode() ?: 0)
        result = 31 * result + (gasFeeCap?.hashCode() ?: 0)
        result = 31 * result + (gasTipCap?.hashCode() ?: 0)
        result = 31 * result + (value?.hashCode() ?: 0)
        result = 31 * result + nonce.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        result = 31 * result + accessList.hashCode()
        result = 31 * result + chainId.hashCode()
        result = 31 * result + (blobFeeCap?.hashCode() ?: 0)
        result = 31 * result + (blobVersionedHashes?.hashCode() ?: 0)
        return result
    }

    /**
     * Mutable builder of [CallRequest]. Each [build] creates a new [CallRequest] and leaves the builder untouched,
     * so it can keep being changed and built again. Not thread-safe.
     * */
    class Builder() {
        /**
         * Create a builder initialized with the fields of [request].
         * */
        constructor(request: CallRequest) : this() {
            this.from = request.from
            this.to = request.to
            this.gas = request.gas
            this.gasPrice = request.gasPrice
            this.gasFeeCap = request.gasFeeCap
            this.gasTipCap = request.gasTipCap
            this.value = request.value
            this.nonce = request.nonce
            this.data = request.data
            this.accessList = request.accessList
            this.chainId = request.chainId
            this.blobFeeCap = request.blobFeeCap
            this.blobVersionedHashes = request.blobVersionedHashes
        }

        // make property setters unavailable from Java since we provide custom chained functions
        var from: Address? = null
            @JvmSynthetic set

        var to: Address? = null
            @JvmSynthetic set

        var gas: Long = -1L
            @JvmSynthetic set

        var gasPrice: BigInteger? = null
            @JvmSynthetic set(value) {
                require(value == null || value >= bigIntegerOf(0)) { "GasPrice must be non-negative" }
                field = value
            }

        var gasFeeCap: BigInteger? = null
            @JvmSynthetic set(value) {
                require(value == null || value >= bigIntegerOf(0)) { "GasFeeCap must be non-negative" }
                field = value
            }

        var gasTipCap: BigInteger? = null
            @JvmSynthetic set(value) {
                require(value == null || value >= bigIntegerOf(0)) { "GasTipCap must be non-negative" }
                field = value
            }

        var value: BigInteger? = null
            @JvmSynthetic set(value) {
                require(value == null || value >= bigIntegerOf(0)) { "Value must be non-negative" }
                field = value
            }

        var nonce: Long = -1L
            @JvmSynthetic set

        var data: Bytes? = null
            @JvmSynthetic set

        var accessList: List<AccessList.Item> = emptyList()
            @JvmSynthetic set

        var chainId: Long = -1L
            @JvmSynthetic set

        var blobFeeCap: BigInteger? = null
            @JvmSynthetic set(value) {
                require(value == null || value >= bigIntegerOf(0)) { "BlobFeeCap must be non-negative" }
                field = value
            }

        var blobVersionedHashes: List<Hash>? = null
            @JvmSynthetic set

        fun from(from: Address?) = apply { this.from = from }
        fun to(to: Address?) = apply { this.to = to }
        fun gas(gas: Long) = apply { this.gas = gas }
        fun gasPrice(gasPrice: BigInteger?) = apply { this.gasPrice = gasPrice }
        fun gasFeeCap(gasFeeCap: BigInteger?) = apply { this.gasFeeCap = gasFeeCap }
        fun gasTipCap(gasTipCap: BigInteger?) = apply { this.gasTipCap = gasTipCap }
        fun value(value: BigInteger?) = apply { this.value = value }
        fun nonce(nonce: Long) = apply { this.nonce = nonce }
        fun data(data: Bytes?) = apply { this.data = data }
        fun accessList(accessList: List<AccessList.Item>) = apply { this.accessList = accessList }
        fun chainId(chainId: Long) = apply { this.chainId = chainId }
        fun blobFeeCap(blobFeeCap: BigInteger?) = apply { this.blobFeeCap = blobFeeCap }
        fun blobVersionedHashes(blobVersionedHashes: List<Hash>?) = apply { this.blobVersionedHashes = blobVersionedHashes }

        /**
         * Create a new [CallRequest] from the current fields of this builder.
         * */
        fun build(): CallRequest = CallRequest(this)
    }

    companion object {
        /**
         * Build a [CallRequest], configuring its fields with [builder].
         * */
        @JvmSynthetic
        inline operator fun invoke(builder: Builder.() -> Unit): CallRequest {
            return Builder().apply(builder).build()
        }
    }
}

object CallRequestSerializer : KSerializer<CallRequest> {
    private val accessListSerializer = ListSerializer(AccessList.Item.serializer())
    private val hashListSerializer = ListSerializer(HashSerializer)

    private const val FROM = 0
    private const val TO = 1
    private const val GAS = 2
    private const val GAS_PRICE = 3
    private const val GAS_FEE_CAP = 4
    private const val GAS_TIP_CAP = 5
    private const val VALUE = 6
    private const val NONCE = 7
    private const val DATA = 8
    private const val ACCESS_LIST = 9
    private const val CHAIN_ID = 10
    private const val BLOB_FEE_CAP = 11
    private const val BLOB_VERSIONED_HASHES = 12

    // Every field is optional: only the ones that are set are written, in this order.
    override val descriptor = buildClassSerialDescriptor("CallRequest") {
        element<String>("from", isOptional = true)
        element<String>("to", isOptional = true)
        element<String>("gas", isOptional = true)
        element<String>("gasPrice", isOptional = true)
        element<String>("maxFeePerGas", isOptional = true)
        element<String>("maxPriorityFeePerGas", isOptional = true)
        element<String>("value", isOptional = true)
        element<String>("nonce", isOptional = true)
        element<String>("data", isOptional = true)
        element("accessList", accessListSerializer.descriptor, isOptional = true)
        element<String>("chainId", isOptional = true)
        element<String>("maxFeePerBlobGas", isOptional = true)
        element("blobVersionedHashes", hashListSerializer.descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: CallRequest) {
        val descriptor = descriptor
        encoder.encodeStructure(descriptor) {
            val from = value.from
            if (from != null) encodeStringElement(descriptor, FROM, from.toString())
            val to = value.to
            if (to != null) encodeStringElement(descriptor, TO, to.toString())
            val gas = value.gas
            if (gas != -1L) encodeStringElement(descriptor, GAS, FastHex.encodeWithPrefix(gas))
            val gasPrice = value.gasPrice
            if (gasPrice != null) encodeStringElement(descriptor, GAS_PRICE, FastHex.encodeWithPrefix(gasPrice))
            val gasFeeCap = value.gasFeeCap
            if (gasFeeCap != null) encodeStringElement(descriptor, GAS_FEE_CAP, FastHex.encodeWithPrefix(gasFeeCap))
            val gasTipCap = value.gasTipCap
            if (gasTipCap != null) encodeStringElement(descriptor, GAS_TIP_CAP, FastHex.encodeWithPrefix(gasTipCap))
            val txValue = value.value
            if (txValue != null) encodeStringElement(descriptor, VALUE, FastHex.encodeWithPrefix(txValue))
            val nonce = value.nonce
            if (nonce != -1L) encodeStringElement(descriptor, NONCE, FastHex.encodeWithPrefix(nonce))
            val data = value.data
            if (data != null) encodeStringElement(descriptor, DATA, data.toString())
            val accessList = value.accessList
            if (accessList.isNotEmpty()) {
                encodeSerializableElement(descriptor, ACCESS_LIST, accessListSerializer, accessList)
            }
            val chainId = value.chainId
            if (chainId != -1L) encodeStringElement(descriptor, CHAIN_ID, FastHex.encodeWithPrefix(chainId))
            val blobFeeCap = value.blobFeeCap
            if (blobFeeCap != null) encodeStringElement(descriptor, BLOB_FEE_CAP, FastHex.encodeWithPrefix(blobFeeCap))
            val blobVersionedHashes = value.blobVersionedHashes
            if (blobVersionedHashes != null) {
                encodeSerializableElement(descriptor, BLOB_VERSIONED_HASHES, hashListSerializer, blobVersionedHashes)
            }
        }
    }

    override fun deserialize(decoder: Decoder): CallRequest = throw UnsupportedOperationException()
}
