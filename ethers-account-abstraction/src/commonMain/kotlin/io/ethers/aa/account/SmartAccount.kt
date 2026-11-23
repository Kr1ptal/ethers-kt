package io.ethers.aa.account

import io.ethers.aa.EntryPoint
import io.ethers.aa.UserOperation
import io.ethers.abi.eip712.EIP712TypedData
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

/**
 * A call a smart account makes on behalf of its owner.
 */
data class Call(
    val to: Address,
    val value: BigInteger = bigIntegerOf(0),
    val data: Bytes = Bytes.EMPTY,
)

/**
 * The factory that deploys a smart account, and the call data to deploy it with. Together they form the `initCode`
 * of the account's first user operation.
 */
data class FactoryArgs(val factory: Address, val factoryData: Bytes)

/**
 * Signs user operations through one of an account's validation paths.
 *
 * Every [SmartAccount] is a signer for its default path, usually its owner. Modular accounts can validate an
 * operation through other validators, e.g. a session key module. Such a validator is a separate signer, passed
 * with the request, so a single account can sign through any of them. A validator is typically selected through
 * the nonce key, which [nonceKey] provides.
 */
interface UserOperationSigner {
    /**
     * The nonce key that routes an operation to this signer's validator. Most accounts use key 0 for everything.
     */
    val nonceKey: BigInteger
        get() = bigIntegerOf(0)

    /**
     * Return a placeholder signature for gas estimation. It must have the same length as a real signature and
     * make validation cost as much gas, but does not have to be valid.
     */
    suspend fun getStubSignature(userOperation: UserOperation): Bytes

    /**
     * Sign [userOperation], whose hash on chain [chainId] is [userOpHash], and return the value of its `signature`
     * field.
     */
    suspend fun signUserOperation(userOperation: UserOperation, userOpHash: Hash, chainId: Long): Bytes
}

/**
 * An ERC-4337 smart contract account.
 *
 * This interface is the boundary between the generic user operation flow of [SmartAccountClient] and an account
 * implementation: it knows the account's address, how to deploy it, how to encode calls into its `callData`, and
 * how its default validator signs. Implementations exist outside this library, for each account contract.
 */
interface SmartAccount : UserOperationSigner {
    /**
     * Address of the account, which is known before it is deployed.
     */
    val address: Address

    /**
     * The EntryPoint the account trusts.
     */
    val entryPoint: EntryPoint

    /**
     * Return the factory that deploys the account, which [SmartAccountClient] includes in user operations while the
     * account has no code yet. Return null if the account is not deployed by a factory, e.g. an EIP-7702 account,
     * which uses [signEip7702Authorization] instead.
     */
    suspend fun getFactoryArgs(): FactoryArgs?

    /**
     * Encode [calls] into the `callData` of a user operation, e.g. `execute` for a single call and `executeBatch`
     * for many.
     */
    suspend fun encodeCalls(calls: List<Call>): Bytes

    /**
     * Decode the `callData` of a user operation back into the calls it makes, or return null if it is not a call
     * this account encodes.
     */
    suspend fun decodeCalls(callData: Bytes): List<Call>? = null

    /**
     * For an account that is an EIP-7702 delegated EOA: sign the authorization that delegates the EOA to the account
     * implementation, for chain [chainId] at the EOA's transaction count [nonce]. [SmartAccountClient] calls it while
     * the EOA is not delegated yet, and sends the authorization with the operation, whose factory becomes
     * [UserOperation.EIP7702_FACTORY_MARKER].
     *
     * Return null, the default, if the account is not an EIP-7702 account.
     */
    suspend fun signEip7702Authorization(chainId: Long, nonce: Long): Authorization? = null

    /**
     * Adjust a fully estimated [userOperation] before its final paymaster data is requested, e.g. to raise a gas
     * limit the bundler is known to underestimate for this account. Return it unchanged by default.
     *
     * Not called when the paymaster's stub data was already final, as it may be signed over the gas limits.
     */
    suspend fun adjustUserOperation(userOperation: UserOperation): UserOperation = userOperation

    /**
     * Sign [message] (EIP-191) as the account, returning a signature the account's ERC-1271 `isValidSignature`
     * accepts.
     */
    suspend fun signMessage(message: ByteArray): Bytes

    /**
     * Sign [typedData] (EIP-712) as the account, returning a signature the account's ERC-1271 `isValidSignature`
     * accepts.
     */
    suspend fun signTypedData(typedData: EIP712TypedData): Bytes
}
