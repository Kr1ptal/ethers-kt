package io.ethers.providers.types

import io.ethers.core.Kotlinx
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.nonNullOriginal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Positional parameters of a JSON-RPC request.
 *
 * Every parameter is held together with the serializer that encodes it, and the client turns them into JSON with
 * [encode] when it sends the request, just as every result is decoded with an explicit deserializer. No parameter is
 * ever converted by looking at its runtime type:
 * - [RpcParamsBuilder.add] with just a value uses the serializer of the value's **static** type, as kotlinx.serialization
 *   defines it. A type without one fails right there, where the params are built.
 * - Any other encoding is passed explicitly, with [RpcParamsBuilder.add] and a serializer.
 *
 * ```kotlin
 * // a @Serializable type, generic ones included; the serializer is found from the static type
 * @Serializable data class Page<T>(val items: List<T>, val cursor: T?)
 * rpcParams(Page(listOf(address), null), "latest")
 *
 * // a special encoding is passed as a serializer: hex bytes, or a big number as a hex quantity or a JSON number
 * buildRpcParams {
 *     add(signedTx, HexByteArraySerializer)
 *     add(wei, HexBigIntegerSerializer)
 *     add(amount, BigIntegerNumberSerializer)
 * }
 *
 * // values of mixed types are built as a JsonObject
 * rpcParams(buildJsonObject { put("from", from.toString()); put("gas", 21000) })
 * ```
 *
 * From Java, use [builder], adding each parameter with its serializer or with one of the plain overloads:
 * ```java
 * RpcParams params = RpcParams.builder()
 *     .add(page, Page.Companion.serializer(Address.Companion.serializer()))
 *     .add("latest")
 *     .build();
 * ```
 *
 * [ofJson] takes parameters that are already JSON.
 */
class RpcParams @PublishedApi internal constructor(
    private val values: Array<Any?>,
    private val serializers: Array<SerializationStrategy<Any?>>,
) {
    /** Number of parameters. */
    val size: Int get() = values.size

    fun isEmpty(): Boolean = values.isEmpty()

    /** The parameter at [index], as it was passed in. */
    operator fun get(index: Int): Any? = values[index]

    /** The parameters, as they were passed in. */
    fun toList(): List<Any?> = values.asList()

    /** These parameters without the first [n]. */
    fun drop(n: Int): RpcParams {
        if (n == 0) return this
        if (n >= size) return EMPTY
        return RpcParams(values.copyOfRange(n, size), serializers.copyOfRange(n, size))
    }

    /**
     * Encode the parameters as the JSON array sent in the request, each with its serializer.
     *
     * Throws whatever a parameter's serializer throws.
     */
    fun encode(json: Json = Kotlinx.DEFAULT): JsonArray {
        if (values.isEmpty()) return EMPTY_JSON

        val elements = ArrayList<JsonElement>(values.size)
        for (i in values.indices) {
            elements.add(json.encodeToJsonElement(serializers[i], values[i]))
        }
        return JsonArray(elements)
    }

    override fun toString(): String = try {
        encode().toString()
    } catch (e: Exception) {
        "RpcParams(size=$size, unencodable: ${e.message})"
    }

    companion object {
        private val EMPTY_JSON = JsonArray(emptyList())

        /** No parameters. */
        @JvmField
        val EMPTY = RpcParams(emptyArray(), emptyArray())

        /**
         * A builder for callers that can't use [rpcParams] or [buildRpcParams], such as Java. Add each parameter with
         * its serializer, or a basic value with one of the plain overloads, then call [RpcParamsBuilder.build].
         */
        @JvmStatic
        fun builder(): RpcParamsBuilder = RpcParamsBuilder()

        /** Parameters that are already JSON. */
        @JvmStatic
        fun ofJson(params: List<JsonElement>): RpcParams {
            if (params.isEmpty()) return EMPTY
            val serializer = JsonElement.serializer()
            @Suppress("UNCHECKED_CAST")
            return RpcParams(params.toTypedArray(), Array(params.size) { serializer as SerializationStrategy<Any?> })
        }
    }
}

/**
 * Builds [RpcParams]. Use [buildRpcParams] or [rpcParams] from Kotlin, or [RpcParams.builder] from Java.
 *
 * Each parameter is encoded by its own serializer: the one passed in, or for [add] with just a value, the serializer of
 * the value's static type. See [RpcParams].
 */
class RpcParamsBuilder @PublishedApi internal constructor() {
    private val values = ArrayList<Any?>(4)
    private val serializers = ArrayList<SerializationStrategy<Any?>>(4)

    /** Add [value], encoded with [serializer]. */
    @Suppress("UNCHECKED_CAST")
    fun <T> add(value: T, serializer: SerializationStrategy<T>): RpcParamsBuilder {
        values.add(value)
        serializers.add(serializer as SerializationStrategy<Any?>)
        return this
    }

    /** Add a JSON string. */
    fun add(value: String): RpcParamsBuilder = add(value, String.serializer())

    /** Add a JSON boolean. */
    fun add(value: Boolean): RpcParamsBuilder = add(value, Boolean.serializer())

    /** Add a JSON number. */
    fun add(value: Int): RpcParamsBuilder = add(value, Int.serializer())

    /** Add a JSON number. */
    fun add(value: Long): RpcParamsBuilder = add(value, Long.serializer())

    /** Add a value that is already JSON. */
    fun add(value: JsonElement): RpcParamsBuilder = add(value, JsonElement.serializer())

    /**
     * Add [value], encoded with the serializer of its static type [T], exactly as kotlinx.serialization defines it.
     *
     * @throws IllegalArgumentException if [T] has no serializer: annotate it with `@Serializable` (the module declaring
     * it must apply the kotlinx serialization plugin), or pass a serializer with `add(value, serializer)`. Also if [T] is
     * a `ByteArray`, which kotlinx writes as an array of numbers: pass `HexByteArraySerializer` for a hex string.
     */
    // hidden from Java, which can't call a reified function and would otherwise bind add(Object) to it
    @JvmSynthetic
    inline fun <reified T> add(value: T): RpcParamsBuilder {
        val serializer = try {
            serializer<T>()
        } catch (e: SerializationException) {
            throw noSerializer(typeOf<T>(), e)
        }
        if (isByteArray(serializer)) throw byteArrayParam(typeOf<T>())
        return add(value, serializer)
    }

    /** The parameters added so far, or [RpcParams.EMPTY] if none were. */
    fun build(): RpcParams {
        if (values.isEmpty()) return RpcParams.EMPTY
        return RpcParams(values.toTypedArray(), serializers.toTypedArray())
    }
}

@PublishedApi
internal fun noSerializer(type: KType, cause: SerializationException): IllegalArgumentException {
    return IllegalArgumentException(
        "Cannot add a JSON-RPC param of type $type: it has no serializer. Annotate it with @Serializable (the module " +
            "declaring it must apply the kotlinx serialization plugin), or pass a serializer explicitly with " +
            "add(value, serializer).",
        cause,
    )
}

/** Whether [serializer] is kotlinx's own byte array serializer, which writes a JSON array of numbers. */
@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal fun isByteArray(serializer: KSerializer<*>): Boolean = serializer.descriptor.nonNullOriginal == BYTE_ARRAY_DESCRIPTOR

@PublishedApi
internal fun byteArrayParam(type: KType): IllegalArgumentException {
    return IllegalArgumentException(
        "Cannot add a JSON-RPC param of type $type with its default serializer, which writes an array of numbers " +
            "where JSON-RPC expects a hex string. Pass HexByteArraySerializer explicitly with " +
            "add(value, HexByteArraySerializer), or use a type with its own serializer, such as Bytes.",
    )
}

private val BYTE_ARRAY_DESCRIPTOR = ByteArraySerializer().descriptor

/**
 * Build [RpcParams] from any number of parameters, each added with [RpcParamsBuilder.add].
 *
 * ```kotlin
 * val params = buildRpcParams {
 *     add(call)
 *     add(blockId.id)
 *     if (overrides != null) add(overrides)
 * }
 * ```
 */
inline fun buildRpcParams(block: RpcParamsBuilder.() -> Unit): RpcParams = RpcParamsBuilder().apply(block).build()

/** No parameters. */
fun rpcParams(): RpcParams = RpcParams.EMPTY

/** [RpcParams] of one parameter, encoded with the serializer of its static type. */
inline fun <reified A> rpcParams(a: A): RpcParams = RpcParamsBuilder().add(a).build()

/** [RpcParams] of two parameters, encoded with the serializers of their static types. */
inline fun <reified A, reified B> rpcParams(a: A, b: B): RpcParams = RpcParamsBuilder().add(a).add(b).build()

/** [RpcParams] of three parameters, encoded with the serializers of their static types. */
inline fun <reified A, reified B, reified C> rpcParams(a: A, b: B, c: C): RpcParams {
    return RpcParamsBuilder().add(a).add(b).add(c).build()
}

/** [RpcParams] of four parameters, encoded with the serializers of their static types. */
inline fun <reified A, reified B, reified C, reified D> rpcParams(a: A, b: B, c: C, d: D): RpcParams {
    return RpcParamsBuilder().add(a).add(b).add(c).add(d).build()
}
