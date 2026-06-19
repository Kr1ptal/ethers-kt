package io.ethers.signers

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ethers.core.ThrowingError
import io.ethers.core.types.Address
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
import io.ethers.core.types.transaction.ChainId
import io.ethers.core.types.transaction.TransactionSigned
import io.ethers.core.types.transaction.TransactionUnsigned
import io.ethers.core.types.transaction.TxType
import io.ethers.crypto.Hashing

interface Signer {
    val address: Address

    /**
     * Sign message and return [Signature].
     *
     * If you want to recover from signing failure without throwing an exception, use [trySignMessage].
     */
    fun signMessage(message: ByteArray): Signature {
        val sig = signHash(Hashing.hashMessage(message))
        sig.updateV(sig.recoveryId() + Signature.V_ELECTRUM_OFFSET)
        return sig
    }

    /**
     * Sign [TransactionUnsigned] and return [TransactionSigned].
     *
     * If you want to recover from signing failure without throwing an exception, use [trySignTransaction].
     */
    fun signTransaction(tx: TransactionUnsigned): TransactionSigned {
        val sig = signHash(tx.signatureHash())

        if (tx.type == TxType.Legacy) {
            // applies EIP-155 replay protection if we have a valid chainId for legacy tx
            // see: https://github.com/ethereum/EIPs/blob/master/EIPS/eip-155.md
            if (ChainId.isValid(tx.chainId)) {
                sig.updateV((tx.chainId * 2) + Signature.V_EIP155_OFFSET + sig.recoveryId())
            } else {
                sig.updateV(sig.recoveryId() + Signature.V_ELECTRUM_OFFSET)
            }
        }

        return TransactionSigned(tx, sig, from = address)
    }

    /**
     * Sign hash and return [Signature].
     *
     * If you want to recover from signing failure without throwing an exception, use [trySignHash].
     */
    fun signHash(hash: ByteArray): Signature

    /**
     * Try to sign message and return [Signature]. If [signMessage] fails, it returns [SigningError].
     *
     * Safe alternative to [signMessage].
     * */
    fun trySignMessage(message: ByteArray): Result<Signature, SigningError> {
        return try {
            Ok(signMessage(message))
        } catch (e: Exception) {
            Err(SigningError("Error signing message: ${String(message)}", e))
        }
    }

    /**
     * Try to sign transaction and return [TransactionSigned]. If [signTransaction] fails, it returns [SigningError].
     *
     * Safe alternative to [signTransaction].
     * */
    fun trySignTransaction(tx: TransactionUnsigned): Result<TransactionSigned, SigningError> {
        return try {
            Ok(signTransaction(tx))
        } catch (e: Exception) {
            Err(SigningError("Error signing transaction: $tx", e))
        }
    }

    /**
     * Try to sign hash and return [Signature]. If [signHash] fails, it returns [SigningError].
     *
     * Safe alternative to [signHash].
     * */
    fun trySignHash(hash: ByteArray): Result<Signature, SigningError> {
        return try {
            Ok(signHash(hash))
        } catch (e: Exception) {
            Err(SigningError("Error signing hash: ${Hash(hash)}", e))
        }
    }

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    data class SigningError(val msg: String, val cause: Exception? = null) : ThrowingError {
        override fun toException(): RuntimeException = RuntimeException(msg, cause)
    }
}

fun TransactionUnsigned.sign(signer: Signer) = signer.signTransaction(this)
