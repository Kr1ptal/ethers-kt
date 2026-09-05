package io.ethers.solana.signers

internal expect object Ed25519 {
    fun publicKey(seed: ByteArray): ByteArray
    fun sign(seed: ByteArray, message: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean
}
