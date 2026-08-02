package io.ethers.abi.eip712

import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.core.FastHex
import io.ethers.core.getOrNull
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = EIP712DomainSerializer::class)
data class EIP712Domain(
    val name: String? = null,
    val version: String? = null,
    val chainId: BigInteger? = null,
    val verifyingContract: Address? = null,
    val salt: Bytes? = null,
) : ContractStruct {
    override val tuple: List<Any> = buildList {
        name?.let { add(it) }
        version?.let { add(it) }
        chainId?.let { add(it) }
        verifyingContract?.let { add(it) }
        salt?.let { add(it) }
    }

    val separator by lazy(LazyThreadSafetyMode.NONE) {
        EIP712Codec.hashStruct(this)
    }

    override val abiType = AbiType.Struct(
        EIP712Domain::class,
        { data: List<Any> ->
            var index = 0
            EIP712Domain(
                name = if (this.name != null) data[index++] as String else null,
                version = if (this.version != null) data[index++] as String else null,
                chainId = if (this.chainId != null) data[index++] as BigInteger else null,
                verifyingContract = if (this.verifyingContract != null) data[index++] as Address else null,
                salt = if (this.salt != null) data[index++] as Bytes else null,
            )
        },
        buildList {
            name?.let { add(AbiType.Struct.Field("name", AbiType.String)) }
            version?.let { add(AbiType.Struct.Field("version", AbiType.String)) }
            chainId?.let { add(AbiType.Struct.Field("chainId", AbiType.UInt(256))) }
            verifyingContract?.let { add(AbiType.Struct.Field("verifyingContract", AbiType.Address)) }
            salt?.let { add(AbiType.Struct.Field("salt", AbiType.FixedBytes(32))) }
        },
    )
}

internal object EIP712DomainSerializer : KSerializer<EIP712Domain> {
    override val descriptor = buildClassSerialDescriptor("EIP712Domain")

    override fun serialize(encoder: Encoder, value: EIP712Domain) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                value.name?.let { put("name", JsonPrimitive(it)) }
                value.version?.let { put("version", JsonPrimitive(it)) }
                value.chainId?.let { put("chainId", JsonPrimitive(it.toString())) }
                value.verifyingContract?.let { put("verifyingContract", JsonPrimitive(it.toString())) }
                value.salt?.let { put("salt", JsonPrimitive(FastHex.encodeWithPrefix(it.asByteArray()))) }
            },
        )
    }

    override fun deserialize(decoder: Decoder): EIP712Domain {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val name: String? = obj.getOrNull("name") { jsonPrimitive.content }
        val version: String? = obj.getOrNull("version") { jsonPrimitive.content }
        val chainId: BigInteger? = obj.getOrNull("chainId") { BigInteger(jsonPrimitive.content) }
        val verifyingContract: Address? = obj.getOrNull("verifyingContract") { Address(jsonPrimitive.content) }
        val salt: Bytes? = obj.getOrNull("salt") { Bytes(jsonPrimitive.content) }

        return EIP712Domain(
            name = name,
            version = version,
            chainId = chainId,
            verifyingContract = verifyingContract,
            salt = salt,
        )
    }
}
