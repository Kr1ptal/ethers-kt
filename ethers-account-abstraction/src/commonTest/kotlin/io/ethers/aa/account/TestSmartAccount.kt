package io.ethers.aa.account

import io.ethers.aa.EntryPoint
import io.ethers.aa.UserOperation
import io.ethers.abi.AbiFunction
import io.ethers.abi.eip712.EIP712TypedData
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.signers.PrivateKeySigner
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

/**
 * A minimal account for tests: `execute`/`executeBatch` call data, and its owner signing the raw user operation
 * hash.
 */
class TestSmartAccount(
    override val address: Address,
    override val entryPoint: EntryPoint = EntryPoint.V0_7,
    val owner: PrivateKeySigner = PrivateKeySigner("0x" + "01".repeat(32)),
    private val factoryArgs: FactoryArgs? = FactoryArgs(FACTORY, Bytes("0x5fbfb9cf")),
    private val eip7702Delegate: Address? = null,
) : SmartAccount {
    var adjusted = 0

    override suspend fun getFactoryArgs(): FactoryArgs? = factoryArgs

    override suspend fun encodeCalls(calls: List<Call>): Bytes {
        if (calls.size == 1) {
            val call = calls.single()
            return EXECUTE.encodeCall(listOf(call.to, call.value, call.data))
        }
        return EXECUTE_BATCH.encodeCall(listOf(calls.map { listOf(it.to, it.value, it.data) }))
    }

    override suspend fun getStubSignature(userOperation: UserOperation): Bytes = STUB_SIGNATURE

    override suspend fun signUserOperation(userOperation: UserOperation, userOpHash: Hash, chainId: Long): Bytes {
        return Bytes(owner.signHash(userOpHash.asByteArray()).toByteArray())
    }

    override suspend fun signEip7702Authorization(chainId: Long, nonce: Long): Authorization? {
        val delegate = eip7702Delegate ?: return null
        return Authorization(chainId, delegate, nonce, 0, bigIntegerOf(1), bigIntegerOf(2))
    }

    override suspend fun adjustUserOperation(userOperation: UserOperation): UserOperation {
        adjusted++
        return userOperation
    }

    override suspend fun signMessage(message: ByteArray): Bytes = Bytes(owner.signMessage(message).toByteArray())

    override suspend fun signTypedData(typedData: EIP712TypedData): Bytes = Bytes(owner.signHash(typedData.signatureHash()).toByteArray())

    companion object {
        val FACTORY = Address("0x9406Cc6185a346906296840746125a0E44976454")
        val STUB_SIGNATURE = Bytes("0x" + "ff".repeat(65))
        val EXECUTE = AbiFunction.parseSignature("execute(address,uint256,bytes)")
        val EXECUTE_BATCH = AbiFunction.parseSignature("executeBatch((address,uint256,bytes)[])")
    }
}

/**
 * A signer of another validator, e.g. a session key, selected through [nonceKey].
 */
class TestSessionSigner(override val nonceKey: BigInteger, private val key: PrivateKeySigner) : UserOperationSigner {
    override suspend fun getStubSignature(userOperation: UserOperation): Bytes = Bytes("0x" + "ee".repeat(65))

    override suspend fun signUserOperation(userOperation: UserOperation, userOpHash: Hash, chainId: Long): Bytes {
        return Bytes(key.signHash(userOpHash.asByteArray()).toByteArray())
    }
}
