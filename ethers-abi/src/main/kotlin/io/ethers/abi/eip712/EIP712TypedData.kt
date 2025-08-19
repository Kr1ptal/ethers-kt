package io.ethers.abi.eip712

import io.ethers.abi.ContractStruct
import io.ethers.crypto.Hashing

data class EIP712TypedData(
    // the top-level struct name, whose fields are passed in under "message"
    val primaryType: String,
    // type definitions of all structs and their fields
    val types: Map<String, List<EIP712Field>>,
    // fields of the primary type
    val message: Map<String, Any>,
    // the signing domain
    val domain: EIP712Domain,
) {
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
        fun from(message: ContractStruct, domain: EIP712Domain): EIP712TypedData {
            val messageTypes = EIP712Codec.toTypeMap(message.abiType)
            val domainTypes = EIP712Codec.toTypeMap(domain.abiType)

            return EIP712TypedData(
                primaryType = message.abiType.name,
                types = messageTypes + domainTypes,
                message = message.toEIP712Message(),
                domain = domain,
            )
        }
    }
}

data class EIP712Field(
    val name: String,
    val type: String,
)
