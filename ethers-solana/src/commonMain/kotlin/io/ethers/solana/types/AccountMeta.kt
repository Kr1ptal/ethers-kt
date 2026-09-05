package io.ethers.solana.types

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

data class AccountMeta @JvmOverloads constructor(val publicKey: SolanaAddress, val signer: Boolean = false, val writable: Boolean = false) {
    companion object {
        @JvmStatic fun signer(publicKey: SolanaAddress): AccountMeta = AccountMeta(publicKey, signer = true)
        @JvmStatic fun writable(publicKey: SolanaAddress): AccountMeta = AccountMeta(publicKey, writable = true)
        @JvmStatic fun signerAndWritable(publicKey: SolanaAddress): AccountMeta = AccountMeta(publicKey, signer = true, writable = true)
    }
}
