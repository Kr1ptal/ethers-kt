package io.ethers.abi.eip712

import io.ethers.abi.AbiType
import io.ethers.abi.ContractStruct
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import java.math.BigInteger

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
