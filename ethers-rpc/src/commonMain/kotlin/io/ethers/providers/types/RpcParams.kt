package io.ethers.providers.types

import io.ethers.core.Kotlinx
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.reflect.typeOf

/**
 * Positional parameters of a JSON-RPC request.
 *
 * Each parameter is held together with the serializer that encodes it, and the client turns them into JSON with
 * [encode] when it sends the request. The serializer is captured from the parameter's static type where the params are
 * built, so generic and sealed types are encoded the way kotlinx.serialization encodes them everywhere else, on every
 * platform, without looking a serializer up from the runtime value.
 *
 * Build one with:
 * - [rpcParams] or [buildRpcParams], which capture each parameter's type at the call site. Any type that is
 *   `@Serializable` works, as do basic values (strings, numbers, booleans, byte arrays, [JsonElement]s) and
 *   collections and maps of them.
 * - [RpcParamsBuilder.add] with an explicit serializer, when the type has no serializer of its own or the lookup
 *   should be skipped.
 * - [builder], for Java callers: [RpcParamsBuilder.add] with an explicit serializer, which can encode generic
 *   classes, and [RpcParamsBuilder.addDynamic].
 * - [ofDynamic], for callers that can't capture types (Java, or values only known at runtime).
 * - [ofJson], for parameters that are already JSON.
 */
class RpcParams @PublishedApi internal constructor(
    private val values: Array<Any?>,
    // null where the value is converted by its runtime type instead
    private val serializers: Array<SerializationStrategy<Any?>?>,
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
     * Encode the parameters as the JSON array sent in the request.
     *
     * @throws IllegalArgumentException if a parameter without a captured serializer can't be converted by its
     * runtime type
     */
    fun encode(json: Json = Kotlinx.DEFAULT): JsonArray {
        if (values.isEmpty()) return EMPTY_JSON

        val elements = ArrayList<JsonElement>(values.size)
        for (i in values.indices) {
            val value = values[i]
            val serializer = serializers[i]
            elements.add(if (serializer == null) value.toDynamicJson() else json.encodeToJsonElement(serializer, value))
        }
        return JsonArray(elements)
    }

    override fun toString(): String = try {
        encode().toString()
    } catch (e: IllegalArgumentException) {
        "RpcParams(size=$size, unencodable: ${e.message})"
    }

    companion object {
        private val EMPTY_JSON = JsonArray(emptyList())

        /** No parameters. */
        @JvmField
        val EMPTY = RpcParams(emptyArray(), emptyArray())

        /**
         * A builder for callers that can't use [rpcParams] or [buildRpcParams], such as Java. Add each parameter with
         * its serializer, or with [RpcParamsBuilder.addDynamic] to convert it by its runtime type, then call
         * [RpcParamsBuilder.build].
         *
         * ```java
         * RpcParams params = RpcParams.builder()
         *     .add(page, Page.Companion.serializer(Filter.Companion.serializer()))
         *     .addDynamic("latest")
         *     .build();
         * ```
         */
        @JvmStatic
        fun builder(): RpcParamsBuilder = RpcParamsBuilder()

        /** Parameters that are already JSON. */
        @JvmStatic
        fun ofJson(params: List<JsonElement>): RpcParams = ofDynamic(*params.toTypedArray())

        /**
         * Parameters converted by their runtime type when the request is sent: basic values, collections and maps of
         * them, and non-generic `@Serializable` classes. Prefer [rpcParams], which captures the static types; a
         * generic class can only be encoded that way.
         */
        @JvmStatic
        fun ofDynamic(vararg params: Any?): RpcParams {
            if (params.isEmpty()) return EMPTY
            return RpcParams(arrayOf(*params), arrayOfNulls(params.size))
        }
    }
}

/**
 * Builds [RpcParams]. Use [buildRpcParams] from Kotlin, or [RpcParams.builder] from Java.
 */
class RpcParamsBuilder @PublishedApi internal constructor() {
    private val values = ArrayList<Any?>(4)
    private val serializers = ArrayList<SerializationStrategy<Any?>?>(4)

    /** Add [value], encoded with [serializer]. */
    @Suppress("UNCHECKED_CAST")
    fun <T> add(value: T, serializer: SerializationStrategy<T>): RpcParamsBuilder {
        values.add(value)
        serializers.add(serializer as SerializationStrategy<Any?>)
        return this
    }

    /**
     * Add [value], encoded with the serializer of its static type [T].
     *
     * Wherever they appear in [T], byte arrays are written as hex strings and big numbers as JSON numbers, and `Any`
     * is converted by the value's runtime type like [RpcParams.ofDynamic], so maps and lists of mixed values work.
     */
    inline fun <reified T> add(value: T): RpcParamsBuilder {
        // basic values need no serializer, so skip the lookup for them
        return if (isBasicParam(value)) addDynamic(value) else addResolved(value, paramSerializer(typeOf<T>()))
    }

    /** Add [value], converted by its runtime type when the request is sent. */
    fun addDynamic(value: Any?): RpcParamsBuilder {
        values.add(value)
        serializers.add(null)
        return this
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun addResolved(value: Any?, serializer: SerializationStrategy<*>?): RpcParamsBuilder {
        values.add(value)
        serializers.add(serializer as SerializationStrategy<Any?>?)
        return this
    }

    /** The parameters added so far, or [RpcParams.EMPTY] if none were. */
    fun build(): RpcParams {
        if (values.isEmpty()) return RpcParams.EMPTY
        return RpcParams(values.toTypedArray(), serializers.toTypedArray())
    }
}

@PublishedApi
internal fun isBasicParam(value: Any?): Boolean {
    return value == null || value is String || value is Boolean || value is Int || value is Long || value is JsonElement ||
        value is ByteArray
}

/**
 * Build [RpcParams] from any number of parameters, capturing the static type of each [RpcParamsBuilder.add].
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

/** [RpcParams] of one parameter, capturing its static type. */
inline fun <reified A> rpcParams(a: A): RpcParams = RpcParamsBuilder().add(a).build()

/** [RpcParams] of two parameters, capturing their static types. */
inline fun <reified A, reified B> rpcParams(a: A, b: B): RpcParams = RpcParamsBuilder().add(a).add(b).build()

/** [RpcParams] of three parameters, capturing their static types. */
inline fun <reified A, reified B, reified C> rpcParams(a: A, b: B, c: C): RpcParams {
    return RpcParamsBuilder().add(a).add(b).add(c).build()
}

/** [RpcParams] of four parameters, capturing their static types. */
inline fun <reified A, reified B, reified C, reified D> rpcParams(a: A, b: B, c: C, d: D): RpcParams {
    return RpcParamsBuilder().add(a).add(b).add(c).add(d).build()
}
