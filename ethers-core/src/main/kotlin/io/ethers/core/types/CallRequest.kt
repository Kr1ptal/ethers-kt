package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex
import java.math.BigInteger

@JsonSerialize(using = CallRequestSerializer::class)
class CallRequest() : IntoCallRequest {
    constructor(other: CallRequest) : this() {
        this.from = other.from
        this.to = other.to
        this.gas = other.gas
        this.gasPrice = other.gasPrice
        this.gasFeeCap = other.gasFeeCap
        this.gasTipCap = other.gasTipCap
        this.value = other.value
        this.nonce = other.nonce
        this.data = other.data
        this.accessList = other.accessList
        this.chainId = other.chainId
        this.blobFeeCap = other.blobFeeCap
        this.blobVersionedHashes = other.blobVersionedHashes
    }

    override fun toCallRequest(): CallRequest {
        return this
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
            require(value == null || value >= BigInteger.ZERO) { "GasPrice must be non-negative" }
            field = value
        }

    var gasFeeCap: BigInteger? = null
        @JvmSynthetic set(value) {
            require(value == null || value >= BigInteger.ZERO) { "GasFeeCap must be non-negative" }
            field = value
        }

    var gasTipCap: BigInteger? = null
        @JvmSynthetic set(value) {
            require(value == null || value >= BigInteger.ZERO) { "GasTipCap must be non-negative" }
            field = value
        }

    var value: BigInteger? = null
        @JvmSynthetic set(value) {
            require(value == null || value >= BigInteger.ZERO) { "Value must be non-negative" }
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
            require(value == null || value >= BigInteger.ZERO) { "BlobFeeCap must be non-negative" }
            field = value
        }

    var blobVersionedHashes: List<Hash>? = null
        @JvmSynthetic set

    fun from(from: Address?): CallRequest {
        this.from = from
        return this
    }

    fun to(to: Address?): CallRequest {
        this.to = to
        return this
    }

    fun gas(gas: Long): CallRequest {
        this.gas = gas
        return this
    }

    fun gasPrice(gasPrice: BigInteger?): CallRequest {
        this.gasPrice = gasPrice
        return this
    }

    fun gasFeeCap(gasFeeCap: BigInteger?): CallRequest {
        this.gasFeeCap = gasFeeCap
        return this
    }

    fun gasTipCap(gasTipCap: BigInteger?): CallRequest {
        this.gasTipCap = gasTipCap
        return this
    }

    fun value(value: BigInteger?): CallRequest {
        this.value = value
        return this
    }

    fun nonce(nonce: Long): CallRequest {
        this.nonce = nonce
        return this
    }

    fun data(data: Bytes?): CallRequest {
        this.data = data
        return this
    }

    fun accessList(accessList: List<AccessList.Item>): CallRequest {
        this.accessList = accessList
        return this
    }

    fun chainId(chainId: Long): CallRequest {
        this.chainId = chainId
        return this
    }

    fun blobFeeCap(blobFeeCap: BigInteger?): CallRequest {
        this.blobFeeCap = blobFeeCap
        return this
    }

    fun blobVersionedHashes(blobVersionedHashes: List<Hash>?): CallRequest {
        this.blobVersionedHashes = blobVersionedHashes
        return this
    }

    override fun toString(): String {
        return "CallRequest(from=$from, to=$to, gas=$gas, gasPrice=$gasPrice, gasFeeCap=$gasFeeCap, gasTipCap=$gasTipCap, value=$value, nonce=$nonce, data=$data, accessList=$accessList, chainId=$chainId, blobFeeCap=$blobFeeCap, blobVersionedHashes=$blobVersionedHashes)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

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

    companion object {
        inline operator fun invoke(builder: CallRequest.() -> Unit): CallRequest {
            return CallRequest().apply(builder)
        }
    }
}

private class CallRequestSerializer : JsonSerializer<CallRequest>() {
    override fun serialize(value: CallRequest, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        if (value.from != null) {
            gen.writeStringField("from", value.from.toString())
        }
        if (value.to != null) {
            gen.writeStringField("to", value.to.toString())
        }
        if (value.gas != -1L) {
            gen.writeStringField("gas", FastHex.encodeWithPrefix(value.gas))
        }
        if (value.gasPrice != null) {
            gen.writeStringField("gasPrice", FastHex.encodeWithPrefix(value.gasPrice!!))
        }
        if (value.gasFeeCap != null) {
            gen.writeStringField("maxFeePerGas", FastHex.encodeWithPrefix(value.gasFeeCap!!))
        }
        if (value.gasTipCap != null) {
            gen.writeStringField("maxPriorityFeePerGas", FastHex.encodeWithPrefix(value.gasTipCap!!))
        }
        if (value.value != null) {
            gen.writeStringField("value", FastHex.encodeWithPrefix(value.value!!))
        }
        if (value.nonce != -1L) {
            gen.writeStringField("nonce", FastHex.encodeWithPrefix(value.nonce))
        }
        if (value.data != null) {
            gen.writeStringField("input", value.data.toString())
        }
        if (value.accessList.isNotEmpty()) {
            gen.writeArrayFieldStart("accessList")
            for (i in value.accessList.indices) {
                // delegate to AccessList.Item serializer
                gen.writeObject(value.accessList[i])
            }
            gen.writeEndArray()
        }
        if (value.chainId != -1L) {
            gen.writeStringField("chainId", FastHex.encodeWithPrefix(value.chainId))
        }
        val blobFeeCap = value.blobFeeCap
        if (blobFeeCap != null) {
            gen.writeStringField("maxFeePerBlobGas", FastHex.encodeWithPrefix(blobFeeCap))
        }
        val blobVersionedHashes = value.blobVersionedHashes
        if (blobVersionedHashes != null) {
            gen.writeArrayFieldStart("blobVersionedHashes")
            for (i in blobVersionedHashes.indices) {
                gen.writeString(blobVersionedHashes[i].toString())
            }
            gen.writeEndArray()
        }
        gen.writeEndObject()
    }
}

/**
 * Interface to convert a class into a [CallRequest].
 * */
interface IntoCallRequest {
    /**
     * Get the [CallRequest] representation of `this` class.
     * */
    fun toCallRequest(): CallRequest
}
