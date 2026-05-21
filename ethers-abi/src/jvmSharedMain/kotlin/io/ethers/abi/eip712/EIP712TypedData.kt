package io.ethers.abi.eip712

import io.ethers.abi.ContractStruct
import io.ethers.core.types.Signature
import io.ethers.crypto.Hashing
import io.ethers.signers.Signer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
@Serializable(with = EIP712TypedDataSerializer::class)
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
        @JvmStatic
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
@Serializable(with = EIP712FieldSerializer::class)
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

internal object EIP712TypedDataSerializer : KSerializer<EIP712TypedData> {
    override val descriptor = buildClassSerialDescriptor("EIP712TypedData")

    override fun serialize(encoder: Encoder, value: EIP712TypedData) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("primaryType", JsonPrimitive(value.primaryType))
                put(
                    "types",
                    buildJsonObject {
                        value.types.forEach { (typeName, fields) ->
                            put(
                                typeName,
                                buildJsonArray {
                                    fields.forEach { field ->
                                        add(
                                            buildJsonObject {
                                                put("name", JsonPrimitive(field.name))
                                                put("type", JsonPrimitive(field.type))
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
                put("message", anyMapToJsonObject(value.message))
                put("domain", jsonEncoder.json.encodeToJsonElement(EIP712DomainSerializer, value.domain))
            },
        )
    }

    override fun deserialize(decoder: Decoder): EIP712TypedData {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val primaryType = obj["primaryType"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing primaryType field")

        val typesObj = obj["types"]?.jsonObject
            ?: throw IllegalArgumentException("Missing types field")
        val types = typesObj.entries.associate { (typeName, fieldsElement) ->
            typeName to fieldsElement.jsonArray.map { fieldElement ->
                val fieldObj = fieldElement.jsonObject
                EIP712Field(
                    name = fieldObj["name"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing name field"),
                    type = fieldObj["type"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing type field"),
                )
            }
        }

        val messageObj = obj["message"]?.jsonObject
            ?: throw IllegalArgumentException("Missing message field")
        val message = messageObj.entries.associate { (k, v) -> k to jsonElementToAny(v) }

        val domainElement = obj["domain"]
            ?: throw IllegalArgumentException("Missing domain field")
        val domain = jsonDecoder.json.decodeFromJsonElement(EIP712DomainSerializer, domainElement)

        return EIP712TypedData(
            primaryType = primaryType,
            types = types,
            message = message,
            domain = domain,
        )
    }

    private fun anyToJsonElement(value: Any): JsonElement = when (value) {
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value.toString())
        is Boolean -> JsonPrimitive(value.toString())
        is List<*> -> JsonArray(value.map { anyToJsonElement(it!!) })
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k as String to anyToJsonElement(v!!) })
        else -> throw IllegalArgumentException("Unsupported type in EIP712 message: ${value::class}")
    }

    private fun anyMapToJsonObject(map: Map<String, Any>): JsonObject {
        return JsonObject(map.entries.associate { (k, v) -> k to anyToJsonElement(v) })
    }

    private fun jsonElementToAny(element: JsonElement): Any = when (element) {
        is JsonPrimitive -> element.content
        is JsonArray -> element.map { jsonElementToAny(it) }
        is JsonObject -> element.entries.associate { (k, v) -> k to jsonElementToAny(v) }
        is JsonNull -> throw IllegalArgumentException("Null values in EIP712TypedData are not supported")
    }
}

internal object EIP712FieldSerializer : KSerializer<EIP712Field> {
    override val descriptor = buildClassSerialDescriptor("EIP712Field")

    override fun serialize(encoder: Encoder, value: EIP712Field) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("name", JsonPrimitive(value.name))
                put("type", JsonPrimitive(value.type))
            },
        )
    }

    override fun deserialize(decoder: Decoder): EIP712Field {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject

        val name = obj["name"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing name field")
        val type = obj["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing type field")

        return EIP712Field(name = name, type = type)
    }
}
