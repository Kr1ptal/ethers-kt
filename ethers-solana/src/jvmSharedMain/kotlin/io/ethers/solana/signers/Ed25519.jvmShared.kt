package io.ethers.solana.signers

import org.sol4k.tweetnacl.TweetNaclFast

internal actual object Ed25519 {
    actual fun publicKey(seed: ByteArray): ByteArray = TweetNaclFast.Signature.keyPair_fromSeed(seed).publicKey
    actual fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        val pair = TweetNaclFast.Signature.keyPair_fromSeed(seed)
        return TweetNaclFast.Signature(pair.publicKey, pair.secretKey).detached(message)
    }
    actual fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        return TweetNaclFast.Signature(publicKey, ByteArray(0)).detached_verify(message, signature)
    }
}
