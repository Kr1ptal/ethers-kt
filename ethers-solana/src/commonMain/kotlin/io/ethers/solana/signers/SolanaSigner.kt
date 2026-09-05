package io.ethers.solana.signers

import io.ethers.core.Result
import io.ethers.core.ThrowableError
import io.ethers.core.failure
import io.ethers.core.success
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned

/** Signs the exact message bytes with Ed25519, without an Ethereum prefix or prehash. */
interface SolanaSigner {
    val publicKey: SolanaAddress
    fun signMessage(message: ByteArray): SolanaSignature
    fun signTransaction(transaction: SolanaTransactionUnsigned): SolanaTransactionSigned = transaction.sign(this)
    fun trySignMessage(message: ByteArray): Result<SolanaSignature, SigningError> = try {
        success(signMessage(message))
    } catch (e: Exception) {
        failure(SigningError(e))
    }
}

data class SigningError(override val cause: Exception) : ThrowableError {
    override val message: String = "Unable to sign Solana message"
}
