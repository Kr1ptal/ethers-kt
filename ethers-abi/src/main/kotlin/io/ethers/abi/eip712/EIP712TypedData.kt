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
import io.ethers.abi.ContractStruct
import io.ethers.core.forEachObjectField
import io.ethers.core.readListOf
import io.ethers.core.readMapOf
import io.ethers.core.types.Signature
import io.ethers.crypto.Hashing
import io.ethers.signers.Signer

/**
 * Represents EIP712 typed structured data for signing.
 *
 * This class encapsulates all the components needed for EIP712 typed data signing:
 * the primary type being signed, all type definitions, the actual message data,
 * and the signing domain. It provides functionality to generate the final signature
 * hash according to EIP712 specification.
 *
 * EIP712 is a standard for typed structured data signing that provides a more
 * user-friendly signing experience by showing human-readable data instead of
 * hexadecimal strings.
 *
 * @property primaryType The name of the top-level struct type being signed
 * @property types Map of all struct type definitions used in the message
 * @property message The actual data values for the primary type fields
 * @property domain The EIP712 domain separator containing chain and contract info
 * @see <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712 Specification</a>
 */
@JsonSerialize(using = EIP712TypedDataSerializer::class)
@JsonDeserialize(using = EIP712TypedDataDeserializer::class)
data class EIP712TypedData(
    val primaryType: String,
    val types: Map<String, List<EIP712Field>>,
    val message: Map<String, Any>,
    val domain: EIP712Domain,
) {
    /**
     * Generates the final signature hash for this typed data.
     *
     * Computes the EIP712 signature hash according to the specification:
     * - For domain-only signatures: `keccak256("\x19\x01" ‖ domainSeparator)`
     * - For typed data signatures: `keccak256("\x19\x01" ‖ domainSeparator ‖ hashStruct(message))`
     *
     * @return 32-byte keccak256 hash ready for signing
     */
    fun signatureHash(): ByteArray {
        val isDomainPrimaryType = primaryType == "EIP712Domain"

        val ret = ByteArray(if (isDomainPrimaryType) 34 else 66)
        ret[0] = 0x19.toByte()
        ret[1] = 0x01.toByte()
        domain.separator.copyInto(ret, 2)

        if (!isDomainPrimaryType) {
            val hash = EIP712Codec.hashStruct(this)
            hash.copyInto(ret, 34)
        }

        return Hashing.keccak256(ret)
    }

    /**
     * Sign the [signatureHash] with provided [Signer].
     * */
    fun sign(signer: Signer): Signature {
        return signer.signHash(signatureHash())
    }

    companion object {
        /**
         * Creates an EIP712TypedData instance from a [ContractStruct] and [EIP712Domain].
         *
         * This factory method converts a strongly-typed ContractStruct into the Map-based
         * format required for EIP712 typed data. It automatically extracts all type
         * definitions from both the message struct and domain, combining them into
         * a complete type map.
         *
         * @param message The ContractStruct containing the message data to sign
         * @param domain The EIP712 domain for signature context and replay protection
         * @return A new EIP712TypedData instance ready for signing
         */
        fun from(message: ContractStruct, domain: EIP712Domain): EIP712TypedData {
            val messageTypes = EIP712Codec.toTypeMap(message)
            val domainTypes = EIP712Codec.toTypeMap(domain)

            return EIP712TypedData(
                primaryType = message.abiType.name,
                types = messageTypes + domainTypes,
                message = message.toEIP712Message(),
                domain = domain,
            )
        }
    }
}

/**
 * Represents a single field definition in an EIP712 struct type.
 *
 * Each field has a name and a type string that follows EIP712 type notation.
 * Types can be primitive types (address, uint256, bool, etc.), arrays (`Type[]`),
 * fixed arrays (`Type[n]`), or references to other struct types.
 *
 * @property name The field name as it appears in the struct
 * @property type The EIP712 type string (e.g., "address", "uint256", "Person", "bytes32[]")
 */
@JsonSerialize(using = EIP712FieldSerializer::class)
@JsonDeserialize(using = EIP712FieldDeserializer::class)
data class EIP712Field(
    val name: String,
    val type: String,
)

/**
 * Sign the [EIP712TypedData.signatureHash] with this [Signer].
 * */
fun Signer.signTypedData(typedData: EIP712TypedData): Signature {
    return signHash(typedData.signatureHash())
}

private class EIP712TypedDataSerializer : JsonSerializer<EIP712TypedData>() {
    override fun serialize(value: EIP712TypedData, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        gen.writeStringField("primaryType", value.primaryType)
        gen.writeObjectFieldStart("types")
        value.types.forEach { (typeName, fields) ->
            gen.writeArrayFieldStart(typeName)
            fields.forEach { field ->
                gen.writeStartObject()
                gen.writeStringField("name", field.name)
                gen.writeStringField("type", field.type)
                gen.writeEndObject()
            }
            gen.writeEndArray()
        }
        gen.writeEndObject()
        gen.writeObjectField("message", value.message)
        gen.writeObjectField("domain", value.domain)
        gen.writeEndObject()
    }
}

private class EIP712TypedDataDeserializer : JsonDeserializer<EIP712TypedData>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EIP712TypedData {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        lateinit var primaryType: String
        var types: Map<String, List<EIP712Field>>? = null
        var message: Map<String, Any>? = null
        var domain: EIP712Domain? = null

        p.forEachObjectField { field ->
            when (field) {
                "primaryType" -> primaryType = p.text
                "types" -> types = p.readMapOf({ it }, { readEIP712Fields() })
                "message" -> message = p.readValueAs(object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any>>() {})
                "domain" -> domain = p.readValueAs(EIP712Domain::class.java)
                else -> p.skipChildren()
            }
        }

        return EIP712TypedData(
            primaryType = primaryType,
            types = types ?: throw IllegalArgumentException("Missing types field"),
            message = message ?: throw IllegalArgumentException("Missing message field"),
            domain = domain ?: throw IllegalArgumentException("Missing domain field"),
        )
    }

    private fun JsonParser.readEIP712Fields(): List<EIP712Field> {
        return readListOf(EIP712Field::class.java)
    }
}

private class EIP712FieldSerializer : JsonSerializer<EIP712Field>() {
    override fun serialize(value: EIP712Field, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        gen.writeStringField("name", value.name)
        gen.writeStringField("type", value.type)
        gen.writeEndObject()
    }
}

private class EIP712FieldDeserializer : JsonDeserializer<EIP712Field>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EIP712Field {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        var name: String? = null
        var type: String? = null

        p.forEachObjectField { field ->
            when (field) {
                "name" -> name = p.text
                "type" -> type = p.text
                else -> p.skipChildren()
            }
        }

        return EIP712Field(
            name = name ?: throw IllegalArgumentException("Missing name field"),
            type = type ?: throw IllegalArgumentException("Missing type field"),
        )
    }
}
