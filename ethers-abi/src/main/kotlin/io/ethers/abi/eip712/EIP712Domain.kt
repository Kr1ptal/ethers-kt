package io.ethers.abi.eip712

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.core.FastHex
import io.ethers.core.forEachObjectField
import io.ethers.core.readAddress
import io.ethers.core.readBytes
import io.ethers.core.readOrNull
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import java.math.BigInteger

@JsonSerialize(using = EIP712DomainSerializer::class)
@JsonDeserialize(using = EIP712DomainDeserializer::class)
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
        EIP712Domain::class.java,
        { data ->
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

private class EIP712DomainSerializer : JsonSerializer<EIP712Domain>() {
    override fun serialize(value: EIP712Domain, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        value.name?.let { gen.writeStringField("name", it) }
        value.version?.let { gen.writeStringField("version", it) }
        value.chainId?.let { gen.writeStringField("chainId", it.toString()) }
        value.verifyingContract?.let { gen.writeStringField("verifyingContract", it.toString()) }
        value.salt?.let { gen.writeStringField("salt", FastHex.encodeWithPrefix(it.asByteArray())) }
        gen.writeEndObject()
    }
}

private class EIP712DomainDeserializer : JsonDeserializer<EIP712Domain>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EIP712Domain {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        var name: String? = null
        var version: String? = null
        var chainId: BigInteger? = null
        var verifyingContract: Address? = null
        var salt: Bytes? = null

        p.forEachObjectField { field ->
            when (field) {
                "name" -> name = p.readOrNull { text }
                "version" -> version = p.readOrNull { text }
                "chainId" -> chainId = p.readOrNull { BigInteger(p.text) }
                "verifyingContract" -> verifyingContract = p.readOrNull { readAddress() }
                "salt" -> salt = p.readOrNull { readBytes() }
                else -> p.skipChildren()
            }
        }

        return EIP712Domain(
            name = name,
            version = version,
            chainId = chainId,
            verifyingContract = verifyingContract,
            salt = salt,
        )
    }
}
