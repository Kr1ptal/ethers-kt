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

    /**
     * Sign [message], which the signer must not modify: callers pass the buffer they are about to
     * serialize, and one signer changing it would change what the next one signs.
     */
    fun signMessage(message: ByteArray): SolanaSignature

    fun signTransaction(transaction: SolanaTransactionUnsigned): SolanaTransactionSigned = transaction.sign(this)

    /**
     * Safe alternative to [signMessage], reporting a signer that could not produce a signature as a
     * value. Mirrors the EVM `Signer`, where signing is the one place a `try` prefixed twin remains:
     * a signer is an external device or key store, so its failures are not the caller's to prevent.
     */
    fun trySignMessage(message: ByteArray): Result<SolanaSignature, SigningError> = try {
        success(signMessage(message))
    } catch (e: Exception) {
        failure(SigningError(e))
    }

    /** Safe alternative to [signTransaction]. */
    fun trySignTransaction(transaction: SolanaTransactionUnsigned): Result<SolanaTransactionSigned, SigningError> = try {
        success(signTransaction(transaction))
    } catch (e: Exception) {
        failure(SigningError(e))
    }
}

data class SigningError(override val cause: Exception) : ThrowableError {
    override val message: String = "Unable to sign Solana message"
}
