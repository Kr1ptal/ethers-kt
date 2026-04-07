package io.ethers.core.types

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.core.FastHex

@JsonSerialize(using = BlockIdSerializer::class)
sealed interface BlockId {
    val id: String

    data class Hash(val hash: io.ethers.core.types.Hash) : BlockId {
        override val id: String
            get() = hash.toString()
    }

    data class Number(val number: Long) : BlockId {
        override val id: String
            get() = FastHex.encodeWithPrefix(number)
    }

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

private class BlockIdSerializer : JsonSerializer<BlockId>() {
    override fun serialize(value: BlockId, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.id)
    }
}
