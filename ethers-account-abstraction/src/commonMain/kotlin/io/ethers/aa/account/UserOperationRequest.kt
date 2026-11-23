package io.ethers.aa.account

import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.Bytes
import io.ethers.core.types.StateOverride
import io.github.artificialpb.bignum.BigInteger

/**
 * A user operation to prepare and send with [SmartAccountClient]. Every field is optional except the calls:
 * whatever is left unset is filled in when the operation is prepared.
 *
 * Set either [calls], which the account encodes, or raw [callData].
 */
class UserOperationRequest() {
    var calls: List<Call>? = null
        private set
    var callData: Bytes? = null
        private set
    var nonce: BigInteger? = null
        private set
    var nonceKey: BigInteger? = null
        private set
    var callGasLimit: BigInteger? = null
        private set
    var verificationGasLimit: BigInteger? = null
        private set
    var preVerificationGas: BigInteger? = null
        private set
    var maxFeePerGas: BigInteger? = null
        private set
    var maxPriorityFeePerGas: BigInteger? = null
        private set
    var factoryArgs: FactoryArgs? = null
        private set
    var paymaster: Address? = null
        private set
    var paymasterData: Bytes? = null
        private set
    var paymasterVerificationGasLimit: BigInteger? = null
        private set
    var paymasterPostOpGasLimit: BigInteger? = null
        private set
    var paymasterContext: Map<String, Any?>? = null
        private set
    var sponsored: Boolean? = null
        private set
    var signer: UserOperationSigner? = null
        private set
    var stateOverride: StateOverride? = null
        private set
    var eip7702Auth: Authorization? = null
        private set

    constructor(calls: List<Call>) : this() {
        this.calls = calls
    }

    /** The calls the account makes, encoded with [SmartAccount.encodeCalls]. */
    fun calls(calls: List<Call>) = apply { this.calls = calls }

    /** The calls the account makes, encoded with [SmartAccount.encodeCalls]. */
    fun calls(vararg calls: Call) = apply { this.calls = calls.toList() }

    /** Raw `callData`, used instead of [calls]. */
    fun callData(callData: Bytes) = apply { this.callData = callData }

    /** The full nonce. If unset, the next nonce of [nonceKey] is read from the EntryPoint. */
    fun nonce(nonce: BigInteger) = apply { this.nonce = nonce }

    /** The nonce key to read the next nonce of. Defaults to the [signer]'s [UserOperationSigner.nonceKey]. */
    fun nonceKey(nonceKey: BigInteger) = apply { this.nonceKey = nonceKey }

    fun callGasLimit(callGasLimit: BigInteger) = apply { this.callGasLimit = callGasLimit }

    fun verificationGasLimit(verificationGasLimit: BigInteger) = apply { this.verificationGasLimit = verificationGasLimit }

    fun preVerificationGas(preVerificationGas: BigInteger) = apply { this.preVerificationGas = preVerificationGas }

    fun maxFeePerGas(maxFeePerGas: BigInteger) = apply { this.maxFeePerGas = maxFeePerGas }

    fun maxPriorityFeePerGas(maxPriorityFeePerGas: BigInteger) = apply { this.maxPriorityFeePerGas = maxPriorityFeePerGas }

    /** Deploy the account with these factory arguments instead of [SmartAccount.getFactoryArgs]. */
    fun factoryArgs(factoryArgs: FactoryArgs) = apply { this.factoryArgs = factoryArgs }

    /**
     * Use this paymaster and its data as-is, instead of asking the client's paymaster service for them.
     */
    fun paymaster(paymaster: Address, paymasterData: Bytes) = apply {
        this.paymaster = paymaster
        this.paymasterData = paymasterData
    }

    fun paymasterVerificationGasLimit(limit: BigInteger) = apply { this.paymasterVerificationGasLimit = limit }

    fun paymasterPostOpGasLimit(limit: BigInteger) = apply { this.paymasterPostOpGasLimit = limit }

    /** Paymaster-specific context sent to the paymaster service, e.g. a sponsorship policy ID. */
    fun paymasterContext(context: Map<String, Any?>) = apply { this.paymasterContext = context }

    /**
     * Whether to ask the client's paymaster service to sponsor the operation. Defaults to true when the client has
     * a paymaster service. Set to false to have the account pay for itself.
     */
    fun sponsored(sponsored: Boolean) = apply { this.sponsored = sponsored }

    /** Sign through this validator instead of the account's default one. */
    fun signer(signer: UserOperationSigner) = apply { this.signer = signer }

    /** State to override while estimating gas. */
    fun stateOverride(stateOverride: StateOverride) = apply { this.stateOverride = stateOverride }

    /** Signed EIP-7702 authorization to send with the operation, instead of [SmartAccount.signEip7702Authorization]. */
    fun eip7702Auth(authorization: Authorization) = apply { this.eip7702Auth = authorization }
}
