package io.ethers.providers.types

import io.ethers.core.Kotlinx
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.concurrent.Volatile
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
 *   defines it. A type without one fails right there, where the params are built. A `ByteArray` uses kotlinx's own
 *   serializer, which writes an array of numbers, so pass `HexByteArraySerializer` (or use a type with its own
 *   serializer) for a hex string.
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
 *
 * The parameters are encoded once, on the first [encode] or [toString], into the text of the JSON array sent in the
 * request, and every later [encode] returns that same string, so sending these params again (a retry, or the same call
 * in another batch) doesn't encode them again. This also lets a client wrapper, such as a response cache, use the
 * string [encode] returns as a key (or hash it) and forward the same params to the client without them being encoded
 * twice. As a consequence, a change to a mutable value passed as a parameter after that first encoding (for example a
 * `CallRequest` modified after the request was created) is not picked up: build new params for a changed value.
 */
class RpcParams @PublishedApi internal constructor(
    private val values: Array<Any?>,
    private val serializers: Array<SerializationStrategy<Any?>>,
) {
    // The first successful encoding. A String is immutable and is published through a volatile field, so a reader sees
    // either null or the complete text.
    @Volatile
    private var encoded: String? = null

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
     * Encode the parameters as the text of the JSON array sent in the request, each with its serializer, such as
     * `["0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2","latest"]`.
     *
     * The parameters are encoded once, on the first [encode] or [toString]: that encoding is kept, and every later call
     * returns that same string without running the serializers again. A mutable value passed as a parameter and changed
     * after that is therefore not picked up; build new params for a changed value. A failed encode keeps nothing, so a
     * later call tries again.
     *
     * Throws whatever a parameter's serializer throws.
     */
    fun encode(): String {
        encoded?.let { return it }
        if (values.isEmpty()) return EMPTY_JSON

        // Callers racing here each encode and publish; their results are equal, and whichever is written last is kept.
        val json = Kotlinx.DEFAULT.encodeToString(ParamsSerializer, this)
        encoded = json
        return json
    }

    /** The encoded JSON, encoding the parameters if they weren't yet, as [encode] does. */
    override fun toString(): String = try {
        encode()
    } catch (e: Exception) {
        "RpcParams(size=$size, unencodable: ${e.message})"
    }

    /**
     * Writes all the parameters as one JSON array with a single encoder, each element with its own serializer, which
     * is what [encode] returns. A kotlinx list descriptor makes the JSON encoder write `[`, `,` and `]` around them, so
     * each one is written exactly as encoding it alone would write it.
     */
    private object ParamsSerializer : SerializationStrategy<RpcParams> {
        // only its LIST kind is read by the JSON encoder; the element type is never used
        override val descriptor: SerialDescriptor = ListSerializer(JsonElement.serializer()).descriptor

        override fun serialize(encoder: Encoder, value: RpcParams) {
            val values = value.values
            val serializers = value.serializers
            val composite = encoder.beginCollection(descriptor, values.size)
            for (i in values.indices) {
                composite.encodeSerializableElement(descriptor, i, serializers[i], values[i])
            }
            composite.endStructure(descriptor)
        }
    }

    companion object {
        private const val EMPTY_JSON = "[]"

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
    // Only ever appended to, and grown by copying when full, so build can hand full arrays to RpcParams without a copy:
    // a later add copies them first and never writes to the ones handed over.
    private var values = arrayOfNulls<Any?>(INITIAL_CAPACITY)
    private var serializers = arrayOfNulls<SerializationStrategy<Any?>>(INITIAL_CAPACITY)
    private var size = 0

    /** Add [value], encoded with [serializer]. */
    @Suppress("UNCHECKED_CAST")
    fun <T> add(value: T, serializer: SerializationStrategy<T>): RpcParamsBuilder {
        if (size == values.size) {
            values = values.copyOf(size * 2)
            serializers = serializers.copyOf(size * 2)
        }
        values[size] = value
        serializers[size] = serializer as SerializationStrategy<Any?>
        size++
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
     * it must apply the kotlinx serialization plugin), or pass a serializer with `add(value, serializer)`.
     */
    // hidden from Java, which can't call a reified function and would otherwise bind add(Object) to it
    @JvmSynthetic
    inline fun <reified T> add(value: T): RpcParamsBuilder = add(value, paramSerializer<T>())

    /** The parameters added so far, or [RpcParams.EMPTY] if none were. */
    @Suppress("UNCHECKED_CAST")
    fun build(): RpcParams {
        if (size == 0) return RpcParams.EMPTY
        val builtValues = if (size == values.size) values else values.copyOf(size)
        val builtSerializers = if (size == serializers.size) serializers else serializers.copyOf(size)
        return RpcParams(builtValues, builtSerializers as Array<SerializationStrategy<Any?>>)
    }

    private companion object {
        const val INITIAL_CAPACITY = 4
    }
}

/**
 * The serializer of the static type [T], exactly as kotlinx.serialization defines it.
 *
 * @throws IllegalArgumentException if [T] has no serializer.
 */
@PublishedApi
internal inline fun <reified T> paramSerializer(): SerializationStrategy<Any?> {
    val serializer = try {
        serializer<T>()
    } catch (e: SerializationException) {
        throw noSerializer(typeOf<T>(), e)
    }
    @Suppress("UNCHECKED_CAST")
    return serializer as SerializationStrategy<Any?>
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

// The fixed-arity rpcParams build their two arrays directly, with no builder. Each serializer is looked up in argument
// order, as RpcParamsBuilder.add would, so a type without one fails the same way.

/** [RpcParams] of one parameter, encoded with the serializer of its static type. */
inline fun <reified A> rpcParams(a: A): RpcParams = RpcParams(arrayOf<Any?>(a), arrayOf(paramSerializer<A>()))

/** [RpcParams] of two parameters, encoded with the serializers of their static types. */
inline fun <reified A, reified B> rpcParams(a: A, b: B): RpcParams {
    return RpcParams(arrayOf<Any?>(a, b), arrayOf(paramSerializer<A>(), paramSerializer<B>()))
}

/** [RpcParams] of three parameters, encoded with the serializers of their static types. */
inline fun <reified A, reified B, reified C> rpcParams(a: A, b: B, c: C): RpcParams {
    return RpcParams(arrayOf<Any?>(a, b, c), arrayOf(paramSerializer<A>(), paramSerializer<B>(), paramSerializer<C>()))
}

/** [RpcParams] of four parameters, encoded with the serializers of their static types. */
inline fun <reified A, reified B, reified C, reified D> rpcParams(a: A, b: B, c: C, d: D): RpcParams {
    return RpcParams(
        arrayOf<Any?>(a, b, c, d),
        arrayOf(paramSerializer<A>(), paramSerializer<B>(), paramSerializer<C>(), paramSerializer<D>()),
    )
}
