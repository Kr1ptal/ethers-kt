package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import io.ethers.core.forEachObjectField
import io.ethers.core.readBytes
import io.ethers.core.readHash
import io.ethers.core.readHexBigInteger
import io.ethers.core.readMapOf
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import java.math.BigInteger

/**
 * Trace every part of state that is touched during its execution.
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

        return Result(prestate, poststate)
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
        val prestate: Map<Address, Account>,
        val poststate: Map<Address, Account>,
    )

    data class Account(
        val nonce: Long = -1L,
        val balance: BigInteger? = null,
        val code: Bytes? = null,
        val storage: Map<Hash, Hash>? = null,
    )
}
