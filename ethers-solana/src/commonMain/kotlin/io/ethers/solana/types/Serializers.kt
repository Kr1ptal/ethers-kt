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
object SignatureSerializer : Base58Serializer<Signature>("io.ethers.solana.Signature", ::Signature)
object BlockhashSerializer : Base58Serializer<Blockhash>("io.ethers.solana.Blockhash", ::Blockhash)
