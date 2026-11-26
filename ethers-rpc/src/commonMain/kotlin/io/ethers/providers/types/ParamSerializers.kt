package io.ethers.providers.types

import io.ethers.core.BigDecimalNumberSerializer
import io.ethers.core.BigIntegerNumberSerializer
import io.ethers.providers.HexByteArraySerializer
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Find the serializer for a parameter of static [type], or `null` if the value has to be converted by its runtime
 * type.
 *
 * Mostly this is kotlinx's own lookup. It differs for the types kotlinx can't encode the way JSON-RPC needs, wherever
 * they appear in [type], including as type arguments (`List<ByteArray>`, `Page<BigInteger>`, `Map<String, Any>`):
 * - byte arrays are hex strings, where kotlinx writes an array of numbers,
 * - big numbers, which have no serializer, are JSON numbers,
 * - `Any` and type parameters are converted by their runtime type.
 */
@PublishedApi
internal fun paramSerializer(type: KType): SerializationStrategy<Any?>? {
    if (!needsCustomSerializer(type)) {
        @Suppress("UNCHECKED_CAST")
        return serializerOrNull(type) as SerializationStrategy<Any?>?
    }

    val serializer = customSerializer(type)
    return if (serializer === DynamicSerializer) null else serializer
}

private fun needsCustomSerializer(type: KType): Boolean {
    val kClass = type.classifier as? KClass<*> ?: return true
    if (specialSerializer(kClass) != null) return true

    val arguments = type.arguments
    for (i in arguments.indices) {
        val argument = arguments[i].type ?: return true
        if (needsCustomSerializer(argument)) return true
    }
    return false
}

/** Serializer for the types encoded differently from kotlinx's default, or `null` for any other type. */
private fun specialSerializer(kClass: KClass<*>): KSerializer<*>? = when (kClass) {
    Any::class -> DynamicSerializer
    ByteArray::class -> HexByteArraySerializer
    BigInteger::class -> BigIntegerNumberSerializer
    BigDecimal::class -> BigDecimalNumberSerializer
    else -> null
}

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSerializationApi::class)
private fun customSerializer(type: KType): KSerializer<Any?> {
    // a type parameter, e.g. the T of a generic function that builds the params
    val kClass = type.classifier as? KClass<Any> ?: return DynamicSerializer

    val special = specialSerializer(kClass)
    if (special != null) {
        // the dynamic serializer handles null itself
        if (type.isMarkedNullable && special !== DynamicSerializer) return (special as KSerializer<Any>).nullable
        return special as KSerializer<Any?>
    }

    val arguments = type.arguments
    if (arguments.isEmpty()) {
        return serializerOrNull(type) ?: DynamicSerializer
    }

    // star projections are converted by their runtime type
    val argumentSerializers = arguments.map { it.type?.let(::customSerializer) ?: DynamicSerializer }
    return try {
        EmptySerializersModule().serializer(kClass, argumentSerializers, type.isMarkedNullable)
    } catch (_: SerializationException) {
        // not a serializable class: leave the whole value to the runtime conversion, which reports it
        DynamicSerializer
    }
}

/** Encodes a value by its runtime type. Stands in for types that have no serializer of their own. */
private object DynamicSerializer : KSerializer<Any?> {
    override val descriptor = buildClassSerialDescriptor("io.ethers.providers.types.Dynamic")

    override fun serialize(encoder: Encoder, value: Any?) {
        (encoder as JsonEncoder).encodeJsonElement(value.toDynamicJson())
    }

    override fun deserialize(decoder: Decoder): Any? = throw UnsupportedOperationException("Dynamic values are encode-only")
}
