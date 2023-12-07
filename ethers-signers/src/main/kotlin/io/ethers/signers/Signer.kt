package io.ethers.signers

import io.ethers.core.types.Address
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
     */
    fun signMessage(message: ByteArray): Signature {
        val sig = signHash(Hashing.hashMessage(message))
        sig.updateV(sig.recoveryId() + Signature.V_ELECTRUM_OFFSET)
        return sig
    }

    /**
     * Sign [TransactionUnsigned] and return [TransactionSigned].
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
     */
    fun signHash(hash: ByteArray): Signature
}

fun TransactionUnsigned.sign(signer: Signer) = signer.signTransaction(this)
