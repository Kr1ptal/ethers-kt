package io.ethers.solana.types.transaction

import io.ethers.solana.types.U8Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.jvm.JvmStatic

/** Transaction versions readable from RPC, including versions this library cannot construct or sign. */
@Serializable(with = SolanaTxTypeSerializer::class)
sealed class SolanaTxType {
    val isSupported: Boolean get() = this !is Unsupported

    data object Legacy : SolanaTxType()

    sealed class Versioned : SolanaTxType() {
        abstract val version: Int
    }

    data object V0 : Versioned() {
        override val version: Int get() = 0
    }

    data class Unsupported(override val version: Int) : Versioned() {
        init {
            require(version in 1..255) { "Unsupported versions must be in 1..255; version 0 is supported" }
        }
    }

    companion object {
        @JvmStatic
        fun fromVersion(version: Int): SolanaTxType = if (version == 0) V0 else Unsupported(version)
    }
}

/** The RPC version is either "legacy" or an unsigned numeric version, never null. */
object SolanaTxTypeSerializer : KSerializer<SolanaTxType> {
    override val descriptor = PrimitiveSerialDescriptor("SolanaTxType", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): SolanaTxType {
        val input = decoder as JsonDecoder
        val value = input.decodeJsonElement()
        if (value == JsonPrimitive("legacy")) return SolanaTxType.Legacy
        return SolanaTxType.fromVersion(input.json.decodeFromJsonElement(U8Serializer, value))
    }
    override fun serialize(encoder: Encoder, value: SolanaTxType) {
        when (value) {
            SolanaTxType.Legacy -> encoder.encodeString("legacy")
            is SolanaTxType.Versioned -> encoder.encodeInt(value.version)
        }
    }
}
