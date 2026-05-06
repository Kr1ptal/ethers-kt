package io.ethers.core.types.tracers

import io.ethers.core.asBytes
import io.ethers.core.asHash
import io.ethers.core.asHexBigInteger
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.StateOverride
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.math.BigInteger
import kotlin.reflect.KClass

/**
 * The prestate tracer has two modes: "prestate" and "diff". The prestate mode returns the accounts necessary to execute
 * a given transaction. diff mode returns the differences between the transaction's pre- and post-state (i.e. what
 * changed because the transaction happened). The prestateTracer defaults to prestate mode.
 *
 * In diff mode the result object will contain a pre and a post object:
 * - Any read-only access is omitted completely from the result. This mode is only concerned with state modifications.
 * - In `pre` you will find the state of an account before the tx started, and in post its state after tx execution finished.
 * - `post` will contain only the modified fields. e.g. if nonce of an account hasn't changed it will be omitted from post.
 * - Deletion (i.e. account selfdestruct, or storage clearing) will be signified by inclusion in pre and omission in post.
 * - Insertion (i.e. account creation or new slots) will be signified by omission in pre and inclusion in post.
 *
 * @param diffMode if `true`, return the differences between the transaction's pre- and post-state
 */
data class PrestateTracer(val diffMode: Boolean) : Tracer<PrestateTracer.Result> {
    override val name: String
        get() = "prestateTracer"

    override val resultType: KClass<Result>
        get() = Result::class

    override val config: Map<String, Any?> = when {
        diffMode -> CONFIG_DIFF
        else -> CONFIG_PRESTATE
    }

    override fun decodeResult(json: Json, element: JsonElement): Result {
        return json.decodeFromJsonElement(ResultSerializer, element)
    }

    @Serializable(with = ResultSerializer::class)
    data class Result(
        val diffMode: Boolean,
        val prestate: Map<Address, Account>,
        val poststate: Map<Address, Account>,
    ) {
        /**
         * Return the state diff as override, if [diffMode] is set to true. Otherwise, return empty map.
         * */
        fun toStateOverride(): StateOverride {
            // if not in diff mode, return empty map
            if (!diffMode) {
                return StateOverride()
            }

            val ret = HashMap<Address, AccountOverride>(poststate.size)

            // first, add all poststate account changes, including empty accounts which indicates that only storage
            // slots were cleared/set to zero.
            for ((address, account) in poststate) {
                ret[address] = AccountOverride()
                    .nonce(account.nonce)
                    .balance(account.balance)
                    .code(account.code)
                    .stateDiff(account.storage)
            }

            // then, check for selfdestructed accounts and cleared storage slots
            for ((address, preAccount) in prestate) {
                when {
                    // if account is in prestate but not in poststate, it selfdestructed
                    poststate[address] == null -> ret[address] = AccountOverride {
                        nonce = 0
                        code = Bytes.EMPTY
                        balance = BigInteger.ZERO
                        state = emptyMap()
                    }

                    // if account is in both prestate and poststate, check if any storage slots were cleared, indicated
                    // by the poststate account's stateDiff not containing the slot
                    !preAccount.storage.isNullOrEmpty() -> {
                        val postAccount = ret[address]
                            ?: throw IllegalStateException("Should not happen: poststate account not found ($address)")

                        val postDiff = postAccount.state ?: postAccount.stateDiff ?: emptyMap()
                        var newDiff: MutableMap<Hash, Hash>? = null

                        for ((key, _) in preAccount.storage) {
                            if (postDiff.containsKey(key)) {
                                continue
                            }

                            // if key present in "pre" but not in "post", it was cleared. Initialize "newDiff" if not
                            // already done
                            if (newDiff == null) {
                                newDiff = when {
                                    postDiff.isEmpty() -> HashMap(preAccount.storage)
                                    postDiff is HashMap -> postDiff
                                    else -> HashMap(postDiff)
                                }
                            }

                            newDiff[key] = Hash.ZERO
                        }

                        if (newDiff != null) {
                            ret[address] = postAccount.stateDiff(newDiff)
                        }
                    }
                }
            }

            // Arbitrum includes ArbOS changes in the diff, but these changes cannot be applied as a state override
            ret.remove(ARB_OS_ADDRESS)

            return StateOverride.wrap(ret)
        }
    }

    data class Account(
        val nonce: Long = -1L,
        val balance: BigInteger? = null,
        val code: Bytes? = null,
        val storage: Map<Hash, Hash>? = null,
    )

    object ResultSerializer : KSerializer<Result> {
        override val descriptor = buildClassSerialDescriptor("PrestateTracer.Result")

        override fun serialize(encoder: Encoder, value: Result) = throw UnsupportedOperationException()

        override fun deserialize(decoder: Decoder): Result {
            val obj = (decoder as JsonDecoder).decodeJsonElement().jsonObject

            val hasDiffKeys = obj.containsKey("pre") || obj.containsKey("post")

            return if (hasDiffKeys) {
                val prestate = obj["pre"]?.jsonObject?.let { readAccountMap(it) } ?: emptyMap()
                val poststate = obj["post"]?.jsonObject?.let { readAccountMap(it) } ?: emptyMap()
                Result(true, prestate, poststate)
            } else {
                val prestate = readAccountMap(obj)
                Result(false, prestate, emptyMap())
            }
        }

        private fun readAccountMap(obj: JsonObject): Map<Address, Account> {
            return obj.entries.associate { (addrStr, element) ->
                Address(addrStr) to readAccount(element.jsonObject)
            }
        }

        private fun readAccount(obj: JsonObject): Account {
            var nonce = -1L
            var balance: BigInteger? = null
            var code: Bytes? = null
            var storage: Map<Hash, Hash>? = null

            for ((key, element) in obj.entries) {
                when (key) {
                    "nonce" -> nonce = element.jsonPrimitive.long
                    "balance" -> balance = element.jsonPrimitive.asHexBigInteger()
                    "code" -> code = element.jsonPrimitive.asBytes()
                    "storage" -> storage = element.jsonObject.entries.associate { (k, v) ->
                        Hash(k) to v.jsonPrimitive.asHash()
                    }
                }
            }

            return Account(nonce, balance, code, storage)
        }
    }

    companion object {
        private val ARB_OS_ADDRESS = Address("0xa4b05fffffffffffffffffffffffffffffffffff")
        private val CONFIG_PRESTATE = mapOf("diffMode" to false)
        private val CONFIG_DIFF = mapOf("diffMode" to true)
    }
}
