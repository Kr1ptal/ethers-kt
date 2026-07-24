package io.ethers.core.types

import io.ethers.core.FastHex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BlockIdSerializer::class)
sealed interface BlockId {
    val id: String

    @Serializable(with = BlockIdHashSerializer::class)
    data class Hash(val hash: io.ethers.core.types.Hash) : BlockId {
        override val id: String
            get() = hash.toString()
    }

    @Serializable(with = BlockIdNumberSerializer::class)
    data class Number(val number: Long) : BlockId {
        override val id: String
            get() = FastHex.encodeWithPrefix(number)
    }

    @Serializable(with = BlockIdNameSerializer::class)
    data class Name(override val id: String) : BlockId

    companion object {
        @JvmField
        val LATEST = Name("latest")

        @JvmField
        val FINALIZED = Name("finalized")

        @JvmField
        val SAFE = Name("safe")

        @JvmField
        val EARLIEST = Name("earliest")

        @JvmField
        val PENDING = Name("pending")
    }
}

object BlockIdSerializer : KSerializer<BlockId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BlockId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BlockId) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): BlockId = throw UnsupportedOperationException()
}

object BlockIdHashSerializer : KSerializer<BlockId.Hash> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BlockId.Hash", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BlockId.Hash) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): BlockId.Hash = throw UnsupportedOperationException()
}

object BlockIdNumberSerializer : KSerializer<BlockId.Number> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BlockId.Number", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BlockId.Number) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): BlockId.Number = throw UnsupportedOperationException()
}

object BlockIdNameSerializer : KSerializer<BlockId.Name> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BlockId.Name", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BlockId.Name) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): BlockId.Name = throw UnsupportedOperationException()
}
