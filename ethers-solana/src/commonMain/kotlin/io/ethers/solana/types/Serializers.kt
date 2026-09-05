package io.ethers.solana.types

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

abstract class Base58Serializer<T>(name: String, private val decode: (String) -> T) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): T = decode(decoder.decodeString())
}

object SolanaAddressSerializer : Base58Serializer<SolanaAddress>("io.ethers.solana.SolanaAddress", ::SolanaAddress)
object SolanaSignatureSerializer : Base58Serializer<SolanaSignature>("io.ethers.solana.SolanaSignature", ::SolanaSignature)
object SolanaBlockhashSerializer : Base58Serializer<SolanaBlockhash>("io.ethers.solana.SolanaBlockhash", ::SolanaBlockhash)
