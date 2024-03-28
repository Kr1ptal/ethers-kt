package io.ethers.core.types

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ethers.core.isField
import io.ethers.core.isNextTokenObjectEnd
import io.ethers.core.readHexLong
import io.ethers.core.readMapOf

/**
 * Content of the transaction pool, including both pending and queued transactions, grouped by sender address
 * and nonce values.
 */
@JsonDeserialize(using = TxpoolContentDeserializer::class)
data class TxpoolContent(
    val pending: Map<Address, Map<Long, RPCTransaction>>,
    val queued: Map<Address, Map<Long, RPCTransaction>>,
)

/**
 * Status of the transaction pool, including number of pending and queued transactions.
 */
@JsonDeserialize(using = TxpoolStatusDeserializer::class)
data class TxpoolStatus(
    val pending: Long,
    val queued: Long,
)

/**
 * Content of the transaction pool for a given address, including both pending and queued transactions,
 * grouped by nonce values.
 */
@JsonDeserialize(using = TxpoolContentFromAddressDeserializer::class)
data class TxpoolContentFromAddress(
    val pending: Map<Long, RPCTransaction>,
    val queued: Map<Long, RPCTransaction>,
)

/**
 * Flattened transaction pool content for easy inspection, grouped by sender address and nonce values.
 *
 * Transaction is represented as formatted string:
 *   - normal TX:            "addressTo: value + gasLimit + gasPrice"
 *   - contract creation:    "contract creation: value + gasLimit + gasPrice"
 */
@JsonDeserialize(using = TxpoolInspectResultDeserializer::class)
data class TxpoolInspectResult(
    val pending: Map<Address, Map<Long, String>>,
    val queued: Map<Address, Map<Long, String>>,
)

private class TxpoolContentDeserializer : JsonDeserializer<TxpoolContent>() {
    override fun deserialize(p: JsonParser, context: DeserializationContext): TxpoolContent {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalStateException("Expected start object, got: ${p.currentToken}")
        }

        var pending: Map<Address, Map<Long, RPCTransaction>> = emptyMap()
        var queued: Map<Address, Map<Long, RPCTransaction>> = emptyMap()

        while (!p.isNextTokenObjectEnd()) {
            when {
                p.isField("pending") -> {
                    pending = p.readMapOf(
                        { Address(it) },
                        { p.readMapOf({ it.toLong() }, RPCTransaction::class.java) },
                    )
                }

                p.isField("queued") -> {
                    queued = p.readMapOf(
                        { Address(it) },
                        { p.readMapOf({ it.toLong() }, RPCTransaction::class.java) },
                    )
                }

                else -> throw IllegalStateException("Unexpected field name: ${p.currentName()}")
            }
        }

        return TxpoolContent(pending, queued)
    }
}

private class TxpoolStatusDeserializer : JsonDeserializer<TxpoolStatus>() {
    override fun deserialize(p: JsonParser, context: DeserializationContext): TxpoolStatus {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalStateException("Expected start object, got: ${p.currentToken}")
        }

        var pending: Long? = null
        var queued: Long? = null

        while (!p.isNextTokenObjectEnd()) {
            when {
                p.isField("pending") -> pending = p.readHexLong()
                p.isField("queued") -> queued = p.readHexLong()
                else -> throw IllegalStateException("Unexpected field name: ${p.currentName()}")
            }
        }

        return TxpoolStatus(pending!!, queued!!)
    }
}

private class TxpoolContentFromAddressDeserializer : JsonDeserializer<TxpoolContentFromAddress>() {
    override fun deserialize(p: JsonParser, context: DeserializationContext): TxpoolContentFromAddress {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalStateException("Expected start object, got: ${p.currentToken}")
        }

        var pending: Map<Long, RPCTransaction> = emptyMap()
        var queued: Map<Long, RPCTransaction> = emptyMap()

        while (!p.isNextTokenObjectEnd()) {
            when {
                p.isField("pending") -> pending = p.readMapOf({ it.toLong() }, RPCTransaction::class.java)
                p.isField("queued") -> queued = p.readMapOf({ it.toLong() }, RPCTransaction::class.java)
                else -> throw IllegalStateException("Unexpected field name: ${p.currentName()}")
            }
        }

        return TxpoolContentFromAddress(pending, queued)
    }
}

private class TxpoolInspectResultDeserializer : JsonDeserializer<TxpoolInspectResult>() {
    override fun deserialize(p: JsonParser, context: DeserializationContext): TxpoolInspectResult {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalStateException("Expected start object, got: ${p.currentToken}")
        }

        var pending: Map<Address, Map<Long, String>> = emptyMap()
        var queued: Map<Address, Map<Long, String>> = emptyMap()

        while (!p.isNextTokenObjectEnd()) {
            when {
                p.isField("pending") -> {
                    pending = p.readMapOf(
                        { Address(it) },
                        { p.readMapOf({ it.toLong() }, String::class.java) },
                    )
                }

                p.isField("queued") -> {
                    queued = p.readMapOf(
                        { Address(it) },
                        { p.readMapOf({ it.toLong() }, String::class.java) },
                    )
                }

                else -> throw IllegalStateException("Unexpected field name: ${p.currentName()}")
            }
        }

        return TxpoolInspectResult(pending, queued)
    }
}
