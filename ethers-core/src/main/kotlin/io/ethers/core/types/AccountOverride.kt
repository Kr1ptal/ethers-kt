package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex
import java.math.BigInteger

@JsonSerialize(using = AccountOverrideSerializer::class)
class AccountOverride {
    // make property setters unavailable from Java since we provide custom chained functions
    var nonce: Long = -1L
        @JvmSynthetic set
    var code: Bytes? = null
        @JvmSynthetic set
    var balance: BigInteger? = null
        @JvmSynthetic set
    var state: Map<Hash, Hash>? = null
        @JvmSynthetic set
    var stateDiff: Map<Hash, Hash>? = null
        @JvmSynthetic set

    fun nonce(nonce: Long): AccountOverride {
        this.nonce = nonce
        return this
    }

    fun code(code: Bytes?): AccountOverride {
        this.code = code
        return this
    }

    fun balance(balance: BigInteger?): AccountOverride {
        this.balance = balance
        return this
    }

    fun state(state: Map<Hash, Hash>?): AccountOverride {
        this.state = state
        return this
    }

    fun stateDiff(stateDiff: Map<Hash, Hash>?): AccountOverride {
        this.stateDiff = stateDiff
        return this
    }

    override fun toString(): String {
        return "AccountOverride(nonce=$nonce, code=$code, balance=$balance, state=$state, stateDiff=$stateDiff)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccountOverride

        if (nonce != other.nonce) return false
        if (code != other.code) return false
        if (balance != other.balance) return false
        if (state != other.state) return false
        if (stateDiff != other.stateDiff) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nonce.hashCode()
        result = 31 * result + (code?.hashCode() ?: 0)
        result = 31 * result + (balance?.hashCode() ?: 0)
        result = 31 * result + (state?.hashCode() ?: 0)
        result = 31 * result + (stateDiff?.hashCode() ?: 0)
        return result
    }

    companion object {
        inline operator fun invoke(builder: AccountOverride.() -> Unit): AccountOverride {
            return AccountOverride().apply(builder)
        }
    }
}

private class AccountOverrideSerializer : JsonSerializer<AccountOverride>() {
    override fun serialize(value: AccountOverride, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        if (value.nonce != -1L) {
            gen.writeStringField("nonce", FastHex.encodeWithPrefix(value.nonce))
        }
        if (value.code != null) {
            gen.writeStringField("code", value.code!!.toString())
        }
        if (value.balance != null) {
            gen.writeStringField("balance", FastHex.encodeWithPrefix(value.balance!!))
        }
        // empty map clears the state
        if (value.state != null) {
            gen.writeObjectField("state", value.state)
        }
        if (!value.stateDiff.isNullOrEmpty()) {
            gen.writeObjectField("stateDiff", value.stateDiff)
        }
        gen.writeEndObject()
    }
}
