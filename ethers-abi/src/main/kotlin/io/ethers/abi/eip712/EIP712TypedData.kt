package io.ethers.abi.eip712

import io.ethers.abi.ContractStruct

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
    companion object {
        fun from(message: ContractStruct, domain: EIP712Domain): EIP712TypedData {
            return EIP712TypedData(
                primaryType = message.abiType.name,
                types = message.abiType.eip712Components,
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
