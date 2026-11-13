package io.ethers.solana.signers

import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.toByteArray
import io.ethers.solana.types.toNSData
import io.ethers.solana.types.transaction.SolanaTransactionCompiled
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import platform.Foundation.NSData

/**
 * A [SolanaSigner] whose signatures come from [sign], for keys this library does not hold: the Secure Enclave, a
 * Keychain item or a wallet SDK.
 *
 * Prefer this over implementing [SolanaSigner] in Swift. An Objective-C protocol has no default methods, so a Swift
 * type would have to implement every member of the interface, and exchange `KotlinByteArray` rather than `Data`.
 *
 * [sign] receives the exact message bytes and returns their 64-byte Ed25519 signature, or nil when no signature
 * could be produced. It runs synchronously on the calling thread. A signer that has to wait, such as one asking a
 * remote service, should sign [SolanaTransactionCompiled.serializeMessage]
 * ahead of time and add the result with [SolanaTransactionSigned.Builder.addSignature].
 *
 * A nil or malformed signature throws [IllegalArgumentException] from [signMessage]. The transaction signing
 * functions declare it, so Swift receives it as an error. [signMessage] cannot declare it, as the interface does not,
 * so a Swift caller signing a message directly should use [trySignMessage], which returns it as a [SigningError].
 */
class CallbackSigner(
    override val publicKey: SolanaAddress,
    private val sign: (message: NSData) -> NSData?,
) : SolanaSigner {
    override fun signMessage(message: ByteArray): SolanaSignature {
        val signature = requireNotNull(sign(message.toNSData())) { "Signer $publicKey did not produce a signature" }
        return SolanaSignature(signature.toByteArray())
    }

    override fun toString(): String = "CallbackSigner($publicKey)"
}
