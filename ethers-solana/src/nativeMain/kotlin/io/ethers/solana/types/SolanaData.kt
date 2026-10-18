@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.ethers.solana.types

import io.ethers.core.Result
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.transaction.SolanaTransactionCompiled
import io.ethers.solana.types.transaction.SolanaTransactionError
import io.ethers.solana.types.transaction.SolanaTransactionSigned
import io.ethers.solana.types.transaction.SolanaTransactionUnsigned
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

// `NSData` twins of the byte-heavy entry points, so a Swift caller can pass and receive `Data` instead of copying
// it through `KotlinByteArray` by hand. Inputs keep their `ByteArray` name and take a parameter named `data`, so their
// Objective-C selector never collides with the `ByteArray` one; outputs cannot overload on the return type, so they
// carry a `Data` suffix.

/**
 * Copy [this] into a new [ByteArray]. Data over 2 GiB has no `ByteArray` to go into, which is why every function
 * taking `NSData` declares [IllegalArgumentException], even those returning a `Result`.
 */
internal fun NSData.toByteArray(): ByteArray {
    val size = length
    require(size <= Int.MAX_VALUE.toULong()) { "NSData of $size bytes does not fit a ByteArray" }
    val bytes = ByteArray(size.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, size) }
    return bytes
}

/** Copy [this] into a new [NSData]. */
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}

/** As the `SolanaAddress(ByteArray)` constructor, from 32 bytes of [data]. */
@Throws(IllegalArgumentException::class)
fun SolanaAddress.Companion.fromData(data: NSData): SolanaAddress = SolanaAddress(data.toByteArray())

/** A copy of the address's 32 bytes. */
fun SolanaAddress.toData(): NSData = asByteArray().toNSData()

/** As the `SolanaSignature(ByteArray)` constructor, from 64 bytes of [data]. */
@Throws(IllegalArgumentException::class)
fun SolanaSignature.Companion.fromData(data: NSData): SolanaSignature = SolanaSignature(data.toByteArray())

/** A copy of the signature's 64 bytes. */
fun SolanaSignature.toData(): NSData = asByteArray().toNSData()

/** As the `SolanaBlockhash(ByteArray)` constructor, from 32 bytes of [data]. */
@Throws(IllegalArgumentException::class)
fun SolanaBlockhash.Companion.fromData(data: NSData): SolanaBlockhash = SolanaBlockhash(data.toByteArray())

/** A copy of the blockhash's 32 bytes. */
fun SolanaBlockhash.toData(): NSData = asByteArray().toNSData()

/** As [SolanaBytes.fromBytes], from a copy of [data]. */
@Throws(IllegalArgumentException::class)
fun SolanaBytes.Companion.fromData(data: NSData): SolanaBytes = fromBytes(data.toByteArray())

/** A copy of the bytes. */
fun SolanaBytes.toData(): NSData = asByteArray().toNSData()

/** As [KeypairSigner.fromSeed], from a 32-byte seed. */
@Throws(IllegalArgumentException::class)
fun KeypairSigner.Companion.fromSeed(data: NSData): KeypairSigner = fromSeed(data.toByteArray())

/** As [KeypairSigner.fromSecretKey], from a 64-byte seed and public key. */
@Throws(IllegalArgumentException::class)
fun KeypairSigner.Companion.fromSecretKey(data: NSData): KeypairSigner = fromSecretKey(data.toByteArray())

/** As [SolanaTransactionCompiled.serializeMessage]: the exact message bytes signed by Ed25519. */
fun SolanaTransactionCompiled.serializeMessageData(): NSData = serializeMessage().toNSData()

/** As [SolanaTransactionCompiled.deserialize], from an unsigned or fully signed envelope. */
@Throws(IllegalArgumentException::class)
fun SolanaTransactionCompiled.Companion.deserialize(data: NSData): Result<SolanaTransactionCompiled, SolanaTransactionError> = deserialize(data.toByteArray())

/** As [SolanaTransactionUnsigned.deserializeMessage], from message bytes. */
@Throws(IllegalArgumentException::class)
fun SolanaTransactionUnsigned.Companion.deserializeMessage(data: NSData): Result<SolanaTransactionUnsigned, SolanaTransactionError> = deserializeMessage(data.toByteArray())

/** As [SolanaTransactionSigned.serialize]: the submit-ready envelope. */
fun SolanaTransactionSigned.serializeData(): NSData = serialize().toNSData()

/** As [SolanaTransactionSigned.deserialize], from a fully signed envelope. */
@Throws(IllegalArgumentException::class)
fun SolanaTransactionSigned.Companion.deserialize(data: NSData): Result<SolanaTransactionSigned, SolanaTransactionError> = deserialize(data.toByteArray())

/** As [SolanaTransactionSigned.Builder.serializePartial]: the envelope with zeros for missing signatures. */
fun SolanaTransactionSigned.Builder.serializePartialData(): NSData = serializePartial().toNSData()

/** As [SolanaTransactionSigned.Builder.deserializePartial], from a partially signed envelope. */
@Throws(IllegalArgumentException::class)
fun SolanaTransactionSigned.Builder.Companion.deserializePartial(data: NSData): Result<SolanaTransactionSigned.Builder, SolanaTransactionError> = deserializePartial(data.toByteArray())
