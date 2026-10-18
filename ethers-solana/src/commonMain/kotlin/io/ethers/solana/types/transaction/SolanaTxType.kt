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
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/** Transaction versions readable from RPC, including versions this library cannot construct or sign. */
@Serializable(with = SolanaTxTypeSerializer::class)
@ObjCName("SolanaTxType", exact = true)
sealed class SolanaTxType {
    val isSupported: Boolean get() = this !is Unsupported

    @ObjCName("SolanaTxTypeLegacy", exact = true)
    data object Legacy : SolanaTxType()

    @ObjCName("SolanaTxTypeVersioned", exact = true)
    sealed class Versioned : SolanaTxType() {
        abstract val version: Int
    }

    @ObjCName("SolanaTxTypeV0", exact = true)
    data object V0 : Versioned() {
        override val version: Int get() = 0
    }

    @ObjCName("SolanaTxTypeV1", exact = true)
    data object V1 : Versioned() {
        override val version: Int get() = 1
    }

    /** A version this library reads but cannot construct or sign. Obtained from [fromVersion] only. */
    @ObjCName("SolanaTxTypeUnsupported", exact = true)
    @ConsistentCopyVisibility
    data class Unsupported private constructor(override val version: Int) : Versioned() {
        internal companion object {
            fun of(version: Int): Unsupported {
                require(version in 2..255) { "Unsupported versions must be in 2..255; versions 0 and 1 are supported" }
                return Unsupported(version)
            }
        }
    }

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromVersion(version: Int): SolanaTxType = when (version) {
            0 -> V0
            1 -> V1
            else -> Unsupported.of(version)
        }
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
