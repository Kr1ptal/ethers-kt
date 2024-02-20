package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.forEachObjectField
import io.ethers.core.readBytes
import io.ethers.core.readHash
import io.ethers.core.readHexBigInteger
import io.ethers.core.readMapOf
import io.ethers.core.types.AccountOverride
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import java.math.BigInteger

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

    override fun encodeConfig(gen: JsonGenerator) {
        gen.writeBooleanField("diffMode", diffMode)
    }

    override fun decodeResult(parser: JsonParser): Result {
        var prestate = emptyMap<Address, Account>()
        var poststate = emptyMap<Address, Account>()
        if (diffMode) {
            parser.forEachObjectField {
                when (it) {
                    "pre" -> prestate = parser.readAccountMap()
                    "post" -> poststate = parser.readAccountMap()
                }
            }
        } else {
            prestate = parser.readAccountMap()
        }

        return Result(diffMode, prestate, poststate)
    }

    private fun JsonParser.readAccountMap(): Map<Address, Account> {
        return readMapOf({ Address(it) }) {
            var nonce: Long = -1L
            var balance: BigInteger? = null
            var code: Bytes? = null
            var storage: Map<Hash, Hash>? = null
            forEachObjectField {
                when (it) {
                    "nonce" -> nonce = longValue
                    "balance" -> balance = readHexBigInteger()
                    "code" -> code = readBytes()
                    "storage" -> storage = readMapOf({ key -> Hash(key) }) { readHash() }
                }
            }

            Account(nonce, balance, code, storage)
        }
    }

    data class Result(
        val diffMode: Boolean,
        val prestate: Map<Address, Account>,
        val poststate: Map<Address, Account>,
    ) {
        /**
         * Return the state diff as override, if [diffMode] is set to true. Otherwise, return empty map.
         * */
        fun toStateOverride(): Map<Address, AccountOverride> {
            // if not in diff mode, return empty map
            if (!diffMode) {
                return emptyMap()
            }

            val ret = HashMap<Address, AccountOverride>(poststate.size)

            // first, add all poststate account changes
            for ((address, account) in poststate) {
                // if account was returned as poststate, but is empty, it was selfdestructed
                if (account.isEmpty) {
                    ret[address] = SELFDESTRUCT_OVERRIDE
                } else {
                    ret[address] = AccountOverride()
                        .nonce(account.nonce)
                        .balance(account.balance)
                        .code(account.code)
                        .stateDiff(account.storage)
                }
            }

            // then, check for selfdestructed accounts and cleared storage slots
            for ((address, account) in prestate) {
                val accountSelfdestructed = !ret.containsKey(address)

                when {
                    // if account is in prestate but not in poststate, it selfdestructed
                    accountSelfdestructed -> ret[address] = SELFDESTRUCT_OVERRIDE

                    // if account is in both prestate and poststate, check if any storage slots were cleared, indicated
                    // by the poststate account's stateDiff not containing the slot
                    !account.storage.isNullOrEmpty() -> {
                        val postAccount = ret[address]!!
                        val postDiff = postAccount.stateDiff ?: emptyMap()
                        var newDiff: MutableMap<Hash, Hash>? = null

                        for ((key, _) in account.storage) {
                            if (!postDiff.containsKey(key)) {
                                if (newDiff == null) {
                                    newDiff = when {
                                        postDiff.isEmpty() -> HashMap(account.storage)
                                        postDiff is MutableMap -> postDiff
                                        else -> postDiff.toMap(HashMap())
                                    }
                                }

                                newDiff[key] = Hash.ZERO
                            }
                        }

                        if (newDiff != null) {
                            ret[address] = postAccount.stateDiff(newDiff)
                        }
                    }
                }
            }

            return ret
        }
    }

    data class Account(
        val nonce: Long = -1L,
        val balance: BigInteger? = null,
        val code: Bytes? = null,
        val storage: Map<Hash, Hash>? = null,
    ) {
        /**
         * Return whether the account has all fields unset.
         * */
        val isEmpty: Boolean
            get() = nonce == -1L && balance == null && code == null && storage == null
    }

    companion object {
        private val SELFDESTRUCT_OVERRIDE = AccountOverride {
            nonce = 0
            code = Bytes.EMPTY
            balance = BigInteger.ZERO
            state = emptyMap()
        }
    }
}
