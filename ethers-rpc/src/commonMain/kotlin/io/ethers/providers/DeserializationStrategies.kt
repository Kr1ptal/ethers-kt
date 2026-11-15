package io.ethers.providers

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder

/**
 * Decode with this strategy, then turn the decoded value into an [R] with [transform].
 *
 * Useful for a result whose type has no serializer of its own but is built from one that does, e.g. an object
 * wrapping a decoded hash. An exception thrown by [transform] fails the decoding, as any decoding error does.
 */
fun <T, R> DeserializationStrategy<T>.map(transform: (T) -> R): DeserializationStrategy<R> {
    return MappingDeserializer(this, transform)
}

private class MappingDeserializer<T, R>(
    private val source: DeserializationStrategy<T>,
    private val transform: (T) -> R,
) : DeserializationStrategy<R> {
    override val descriptor: SerialDescriptor get() = source.descriptor

    override fun deserialize(decoder: Decoder): R = transform(decoder.decodeSerializableValue(source))
}
