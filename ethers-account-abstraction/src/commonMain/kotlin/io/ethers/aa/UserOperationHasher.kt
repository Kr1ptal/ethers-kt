package io.ethers.aa

import io.ethers.aa.internal.WordWriter
import io.ethers.aa.internal.concatBytes
import io.ethers.aa.internal.uintToBytes
import io.ethers.abi.eip712.EIP712Domain
import io.ethers.core.types.Address
import io.ethers.core.types.Hash
import io.ethers.crypto.Hashing
import io.github.artificialpb.bignum.bigIntegerOf

/**
 * Computes user operation hashes exactly as each EntryPoint version's `getUserOpHash` does.
 */
internal object UserOperationHasher {
    // keccak256("PackedUserOperation(address sender,uint256 nonce,bytes initCode,bytes callData,bytes32 accountGasLimits,uint256 preVerificationGas,bytes32 gasFees,bytes paymasterAndData)")
    private val PACKED_USEROP_TYPEHASH = Hashing.keccak256(
        (
            "PackedUserOperation(address sender,uint256 nonce,bytes initCode,bytes callData," +
                "bytes32 accountGasLimits,uint256 preVerificationGas,bytes32 gasFees,bytes paymasterAndData)"
            ).encodeToByteArray(),
    )

    private const val DOMAIN_NAME = "ERC4337"
    private const val DOMAIN_VERSION = "1"

    fun hash(op: UserOperation, entryPoint: EntryPoint, chainId: Long, eip7702Delegate: Address?): Hash {
        val version = entryPoint.version
        val initCodeHash = initCodeHash(op, version, eip7702Delegate)
        val callDataHash = Hashing.keccak256(op.callData.asByteArray())
        val pmHash = Hashing.keccak256(op.paymasterAndDataBytes(version, forHash = true))

        if (version == EntryPointVersion.V0_6) {
            val packed = WordWriter(10)
                .address(op.sender)
                .uint(op.nonce)
                .raw(initCodeHash)
                .raw(callDataHash)
                .uint(op.callGasLimit)
                .uint(op.verificationGasLimit)
                .uint(op.preVerificationGas)
                .uint(op.maxFeePerGas)
                .uint(op.maxPriorityFeePerGas)
                .raw(pmHash)
                .toByteArray()

            return outerHash(packed, entryPoint.address, chainId)
        }

        val accountGasLimits = concatBytes(
            uintToBytes(op.verificationGasLimit, 16, "verificationGasLimit"),
            uintToBytes(op.callGasLimit, 16, "callGasLimit"),
        )
        val gasFees = concatBytes(
            uintToBytes(op.maxPriorityFeePerGas, 16, "maxPriorityFeePerGas"),
            uintToBytes(op.maxFeePerGas, 16, "maxFeePerGas"),
        )

        if (version == EntryPointVersion.V0_7) {
            val packed = WordWriter(8)
                .address(op.sender)
                .uint(op.nonce)
                .raw(initCodeHash)
                .raw(callDataHash)
                .raw(accountGasLimits)
                .uint(op.preVerificationGas)
                .raw(gasFees)
                .raw(pmHash)
                .toByteArray()

            return outerHash(packed, entryPoint.address, chainId)
        }

        // v0.8 and later: EIP-712 typed data hash of PackedUserOperation
        val structHash = Hashing.keccak256(
            WordWriter(9)
                .raw(PACKED_USEROP_TYPEHASH)
                .address(op.sender)
                .uint(op.nonce)
                .raw(initCodeHash)
                .raw(callDataHash)
                .raw(accountGasLimits)
                .uint(op.preVerificationGas)
                .raw(gasFees)
                .raw(pmHash)
                .toByteArray(),
        )

        val domainSeparator = domainSeparator(entryPoint.address, chainId)
        val digest = ByteArray(66)
        digest[0] = 0x19
        digest[1] = 0x01
        domainSeparator.copyInto(digest, 2)
        structHash.copyInto(digest, 34)
        return Hash(Hashing.keccak256(digest))
    }

    /**
     * Return the EIP-712 domain separator of EntryPoint v0.8 and later at [entryPoint] on [chainId].
     */
    fun domainSeparator(entryPoint: Address, chainId: Long): ByteArray {
        return EIP712Domain(
            name = DOMAIN_NAME,
            version = DOMAIN_VERSION,
            chainId = bigIntegerOf(chainId),
            verifyingContract = entryPoint,
        ).separator
    }

    private fun initCodeHash(op: UserOperation, version: EntryPointVersion, eip7702Delegate: Address?): ByteArray {
        if (version.supportsEip7702 && op.isEip7702) {
            requireNotNull(eip7702Delegate) {
                "Hash of an EIP-7702 user operation commits to the delegate address, which is unknown: pass it " +
                    "explicitly or set eip7702Auth"
            }

            // Eip7702Support._getEip7702InitCodeHashOverride: keccak(delegate || initCode[20:])
            val factoryData = op.factoryData?.asByteArray() ?: UserOperation.EMPTY_ARRAY
            return Hashing.keccak256(concatBytes(eip7702Delegate.asByteArray(), factoryData))
        }

        return Hashing.keccak256(op.initCode.asByteArray())
    }

    private fun outerHash(packed: ByteArray, entryPoint: Address, chainId: Long): Hash {
        val outer = WordWriter(3)
            .raw(Hashing.keccak256(packed))
            .address(entryPoint)
            .uint(bigIntegerOf(chainId))
            .toByteArray()

        return Hash(Hashing.keccak256(outer))
    }
}
