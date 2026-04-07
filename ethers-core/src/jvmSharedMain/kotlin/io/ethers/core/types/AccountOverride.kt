package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex
import java.math.BigInteger

/**
 * An account override, used to override the nonce, balance, code, and storage of an account.
 * */
@JsonSerialize(using = AccountOverrideSerializer::class)
class AccountOverride() {
    constructor(other: AccountOverride) : this() {
        this.applyChanges(other)
    }

    // make property setters unavailable from Java since we provide custom chained functions
    var nonce: Long = -1L
        @JvmSynthetic set

    var code: Bytes? = null
        @JvmSynthetic set

    var balance: BigInteger? = null
        @JvmSynthetic set

    var state: Map<Hash, Hash>? = null
        @JvmSynthetic set(value) {
            // either setting to null or stateDiff must not be set
            require(value == null || stateDiff == null) { "state and stateDiff cannot be set at the same time" }
            field = value
        }

    var stateDiff: Map<Hash, Hash>? = null
        @JvmSynthetic set(value) {
            // either setting to null or state must not be set
            require(value == null || state == null) { "state and stateDiff cannot be set at the same time" }
            field = value
        }

    /**
     * Set the nonce of the account.
     * */
    fun nonce(nonce: Long): AccountOverride {
        this.nonce = nonce
        return this
    }

    /**
     * Set the code of the account.
     * */
    fun code(code: Bytes?): AccountOverride {
        this.code = code
        return this
    }

    /**
     * Set the balance of the account.
     * */
    fun balance(balance: BigInteger?): AccountOverride {
        this.balance = balance
        return this
    }

    /**
     * Set the state of the account. Existing state will be replaced with the provided state.
     * */
    fun state(state: Map<Hash, Hash>?): AccountOverride {
        this.state = state
        return this
    }

    /**
     * Apply the state diff to the account. Existing state will be updated with the provided state diff.
     * */
    fun stateDiff(stateDiff: Map<Hash, Hash>?): AccountOverride {
        this.stateDiff = stateDiff
        return this
    }

    /**
     * Return a new instance of [AccountOverride] with the merged changes from **this** and [other].
     * */
    fun mergeChanges(other: AccountOverride): AccountOverride {
        val ret = AccountOverride()
        ret.applyChanges(this)
        ret.applyChanges(other)
        return ret
    }

    /**
     * Apply changes from [other] to **this** instance.
     * */
    fun applyChanges(other: AccountOverride) {
        if (other.nonce != -1L) {
            nonce = other.nonce
        }
        if (other.code != null) {
            code = other.code
        }
        if (other.balance != null) {
            balance = other.balance
        }
        if (other.state != null) {
            // first clear the diff if set, then set the new state
            stateDiff = null
            state = other.state
        }

        if (other.stateDiff != null) {
            val applyingOnFreshState = state != null

            // if we already have a "state" override, apply diff to it. This means that the state was cleared before,
            // and we're applying a diff to a fresh state.
            var curr = if (applyingOnFreshState) state else stateDiff
            if (curr == null) {
                curr = HashMap(other.stateDiff!!.size)
            }

            when (curr) {
                is HashMap -> curr.putAll(other.stateDiff!!)
                else -> curr = HashMap(curr).apply { putAll(other.stateDiff!!) }
            }

            if (applyingOnFreshState) state = curr else stateDiff = curr
        }
    }

    override fun toString(): String {
        return "AccountOverride(nonce=$nonce, code=$code, balance=$balance, state=$state, stateDiff=$stateDiff)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

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
