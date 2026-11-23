package io.ethers.aa

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.abi.eip712.EIP712Domain
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.crypto.Hashing
import io.github.artificialpb.bignum.bigIntegerOf

/**
 * Computes user operation hashes exactly as each EntryPoint version's `getUserOpHash` does.
 */
internal object UserOperationHasher {
    private val PACKED_USEROP_TYPEHASH = Bytes(
        Hashing.keccak256(
            (
                "PackedUserOperation(address sender,uint256 nonce,bytes initCode,bytes callData," +
                    "bytes32 accountGasLimits,uint256 preVerificationGas,bytes32 gasFees,bytes paymasterAndData)"
                ).encodeToByteArray(),
        ),
    )

    private const val DOMAIN_NAME = "ERC4337"
    private const val DOMAIN_VERSION = "1"

    private val BYTES32 = AbiType.FixedBytes(32)
    private val UINT256 = AbiType.UInt(256)

    // UserOperationLib.encode of v0.6: gas values are separate uint256 fields
    private val V06_TYPES = listOf(
        AbiType.Address, UINT256, BYTES32, BYTES32, UINT256, UINT256, UINT256, UINT256, UINT256, BYTES32,
    )

    // UserOperationLib.encode of v0.7: gas values are packed into bytes32 words
    private val PACKED_TYPES = listOf(AbiType.Address, UINT256, BYTES32, BYTES32, BYTES32, UINT256, BYTES32, BYTES32)

    // keccak256(abi.encode(keccak256(encodedUserOp), entryPoint, chainId))
    private val OUTER_TYPES = listOf(BYTES32, AbiType.Address, UINT256)

    fun hash(op: UserOperation, entryPoint: EntryPoint, chainId: Long, eip7702Delegate: Address?): Hash {
        val version = entryPoint.version
        val initCodeHash = initCodeHash(op, version, eip7702Delegate)
        val callDataHash = keccak(op.callData.asByteArray())
        val pmHash = keccak(op.paymasterAndDataBytes(version, forHash = true))

        if (version == EntryPointVersion.V0_6) {
            val packed = AbiCodec.encode(
                V06_TYPES,
                listOf(
                    op.sender, op.nonce, initCodeHash, callDataHash, op.callGasLimit, op.verificationGasLimit,
                    op.preVerificationGas, op.maxFeePerGas, op.maxPriorityFeePerGas, pmHash,
                ),
            )
            return outerHash(packed, entryPoint.address, chainId)
        }

        val accountGasLimits = UserOperation.packUints(op.verificationGasLimit, op.callGasLimit)
        val gasFees = UserOperation.packUints(op.maxPriorityFeePerGas, op.maxFeePerGas)
        val packedValues = listOf<Any>(
            op.sender,
            op.nonce,
            initCodeHash,
            callDataHash,
            accountGasLimits,
            op.preVerificationGas,
            gasFees,
            pmHash,
        )

        if (version == EntryPointVersion.V0_7) {
            return outerHash(AbiCodec.encode(PACKED_TYPES, packedValues), entryPoint.address, chainId)
        }

        // v0.8 and later: EIP-712 typed data hash of PackedUserOperation
        val structHash = Hashing.keccak256(AbiCodec.encode(listOf(BYTES32) + PACKED_TYPES, listOf(PACKED_USEROP_TYPEHASH) + packedValues))
        val domainSeparator = domainSeparator(entryPoint.address, chainId)
        val digest = AbiCodec.encodePacked(
            listOf(AbiType.FixedBytes(2), BYTES32, BYTES32),
            listOf(EIP712_PREFIX, Bytes(domainSeparator), Bytes(structHash)),
        )
        return Hash(Hashing.keccak256(digest.asByteArray()))
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

    private val EIP712_PREFIX = Bytes("0x1901")

    private fun initCodeHash(op: UserOperation, version: EntryPointVersion, eip7702Delegate: Address?): Bytes {
        if (version.supportsEip7702 && op.isEip7702) {
            requireNotNull(eip7702Delegate) {
                "Hash of an EIP-7702 user operation commits to the delegate address, which is unknown: pass it " +
                    "explicitly or set eip7702Auth"
            }

            // Eip7702Support._getEip7702InitCodeHashOverride: keccak(delegate || initCode[20:])
            val initCode = AbiCodec.encodePacked(
                listOf(AbiType.Address, AbiType.Bytes),
                listOf(eip7702Delegate, op.factoryData ?: Bytes.EMPTY),
            )
            return keccak(initCode.asByteArray())
        }

        return keccak(op.initCode.asByteArray())
    }

    private fun outerHash(packed: ByteArray, entryPoint: Address, chainId: Long): Hash {
        val outer = AbiCodec.encode(OUTER_TYPES, listOf(keccak(packed), entryPoint, bigIntegerOf(chainId)))
        return Hash(Hashing.keccak256(outer))
    }

    private fun keccak(data: ByteArray): Bytes = Bytes(Hashing.keccak256(data))
}
