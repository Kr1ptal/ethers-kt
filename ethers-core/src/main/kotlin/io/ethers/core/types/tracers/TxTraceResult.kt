package io.ethers.core.types.tracers

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import io.ethers.core.forEachObjectField
import io.ethers.core.readHash
import io.ethers.core.readOrNull
import io.ethers.core.types.Hash

/**
 * Result of traced transactions when tracing the entire block.
 *
 * [txHash] is null if working with older node versions. In that case, the proper way to match
 * trace to transaction is to also do a call to get the same block with hashes, and match the
 * tracing result to the transaction by index.
 * */
@JsonDeserialize(using = TxTraceResultDeserializer::class)
data class TxTraceResult<T>(
    /**
     * Hash of traced transactions, or null on older nodes.
     * */
    val txHash: Hash?,

    /**
     * Tracer result of the transaction, or null if [error] is not null.
     * */
    val result: T?,

    /**
     * Error message, or null if [result] is not null.
     * */
    val error: String?,
)

private class TxTraceResultDeserializer(
    private val resultType: JavaType? = null,
) : JsonDeserializer<TxTraceResult<Any>>(), ContextualDeserializer {
    override fun createContextual(ctxt: DeserializationContext, property: BeanProperty?): JsonDeserializer<*> {
        val contextual = property?.type ?: ctxt.contextualType
        val contained = contextual?.takeIf { it.containedTypeCount() > 0 }?.containedType(0)
        if (contained === resultType) {
            return this
        }
        return TxTraceResultDeserializer(contained)
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TxTraceResult<Any> {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object, got ${p.currentToken}")
        }

        var txHash: Hash? = null
        var result: Any? = null
        var error: String? = null

        val targetType = resultType ?: ctxt.typeFactory.constructType(Any::class.java)

        p.forEachObjectField { field ->
            when (field) {
                "txHash" -> txHash = p.readOrNull { readHash() }
                "result" -> {
                    result = if (p.currentToken == JsonToken.VALUE_NULL) {
                        null
                    } else {
                        readResultValue(p, ctxt, targetType)
                    }
                }

                "error" -> error = if (p.currentToken == JsonToken.VALUE_NULL) null else p.valueAsString
                else -> p.skipChildren()
            }
        }

        @Suppress("UNCHECKED_CAST")
        return TxTraceResult(txHash, result as Any?, error)
    }

    private fun readResultValue(p: JsonParser, ctxt: DeserializationContext, targetType: JavaType): Any {
        return if (targetType.isJavaLangObject) {
            p.readValueAs(Any::class.java)
        } else {
            ctxt.readValue(p, targetType)
        }
    }
}
