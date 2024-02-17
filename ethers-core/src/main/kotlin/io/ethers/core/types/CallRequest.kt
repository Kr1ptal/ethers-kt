package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex
import java.math.BigInteger

@JsonSerialize(using = CallRequestSerializer::class)
class CallRequest {
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

    override fun toString(): String {
        return "CallRequest(from=$from, to=$to, gas=$gas, gasPrice=$gasPrice, gasFeeCap=$gasFeeCap, gasTipCap=$gasTipCap, value=$value, nonce=$nonce, data=$data, accessList=$accessList, chainId=$chainId)"
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
        gen.writeEndObject()
    }
}
