package io.ethers.abi.eip712

import io.ethers.abi.ContractStruct
import io.ethers.crypto.Hashing

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
data class EIP712Field(
    val name: String,
    val type: String,
)
