@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.ethers.solana.types

import io.ethers.core.Result
import io.ethers.solana.instruction.Ed25519Program
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.TokenAccount
import io.ethers.solana.instruction.TokenMint
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.transaction.AddressLookupTableAccount
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
// carry a `Data` suffix. Where the `ByteArray` parameter is already named `data`, the twin's is named `accountData`.

/**
 * Copy [this] into a new [ByteArray]. Data over 2 GiB has no `ByteArray` to go into: functions returning a value
 * declare the [IllegalArgumentException] this throws, and those returning a `Result` use [toByteArrayOrFailure].
 */
internal fun NSData.toByteArray(): ByteArray {
    val size = length
    require(size <= Int.MAX_VALUE.toULong()) { "NSData of $size bytes does not fit a ByteArray" }
    val bytes = ByteArray(size.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, size) }
    return bytes
}

/** As [toByteArray], reporting data too large for a `ByteArray` as a failure rather than throwing. */
private inline fun <T> NSData.decodeBytes(decode: (ByteArray) -> Result<T, SolanaTransactionError>): Result<T, SolanaTransactionError> {
    if (length > Int.MAX_VALUE.toULong()) return Result.failure(SolanaTransactionError.MalformedBytes("NSData of $length bytes does not fit a ByteArray"))
    return decode(toByteArray())
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

/** As [SolanaBytes.fromBytes], from a copy of [data]. Also how Swift passes `Data` as an [Instruction]'s data. */
@Throws(IllegalArgumentException::class)
fun SolanaBytes.Companion.fromData(data: NSData): SolanaBytes = fromBytes(data.toByteArray())

/** A copy of the bytes. */
fun SolanaBytes.toData(): NSData = asByteArray().toNSData()

/**
 * As [SolanaAddress.verify], for a signature over [data]. Data over 2 GiB is no message this library signs, so it
 * does not verify.
 */
fun SolanaAddress.verify(signature: SolanaSignature, data: NSData): Boolean = data.length <= Int.MAX_VALUE.toULong() && verify(signature, data.toByteArray())

/** As [SolanaAddress.createProgramAddress], from seeds given as `Data`. */
@Throws(IllegalArgumentException::class)
fun SolanaAddress.Companion.createProgramAddress(seedData: List<NSData>, programId: SolanaAddress): SolanaAddress? = createProgramAddress(seedData.map { it.toByteArray() }, programId)

/** As [SolanaAddress.findProgramAddress], from seeds given as `Data`. */
@Throws(IllegalArgumentException::class)
fun SolanaAddress.Companion.findProgramAddress(seedData: List<NSData>, programId: SolanaAddress): ProgramDerivedAddress = findProgramAddress(seedData.map { it.toByteArray() }, programId)

/** As [KeypairSigner.fromSeed], from a 32-byte seed. */
@Throws(IllegalArgumentException::class)
fun KeypairSigner.Companion.fromSeed(data: NSData): KeypairSigner = fromSeed(data.toByteArray())

/** As [KeypairSigner.fromSecretKey], from a 64-byte seed and public key. */
@Throws(IllegalArgumentException::class)
fun KeypairSigner.Companion.fromSecretKey(data: NSData): KeypairSigner = fromSecretKey(data.toByteArray())

/** As [KeypairSigner.signMessage], signing the exact bytes of [data]. */
@Throws(IllegalArgumentException::class)
fun KeypairSigner.signMessage(data: NSData): SolanaSignature = signMessage(data.toByteArray())

/** As [KeypairSigner.toSecretKey]: the 64-byte seed and public key. Keep it as secret as the signer itself. */
fun KeypairSigner.toSecretKeyData(): NSData = toSecretKey().toNSData()

/** As [Ed25519Program.verify], for a signature over [data]. */
@Throws(IllegalArgumentException::class)
fun Ed25519Program.verify(publicKey: SolanaAddress, data: NSData, signature: SolanaSignature): Instruction = verify(publicKey, data.toByteArray(), signature)

/** As [TokenAccount.decode], from an account's data. */
fun TokenAccount.Companion.decode(accountData: NSData): Result<TokenAccount, SolanaTransactionError> = accountData.decodeBytes(::decode)

/** As [TokenMint.decode], from an account's data. */
fun TokenMint.Companion.decode(accountData: NSData): Result<TokenMint, SolanaTransactionError> = accountData.decodeBytes(::decode)

/** As [AddressLookupTableAccount.decode], from the data of the lookup table account at [key]. */
fun AddressLookupTableAccount.Companion.decode(key: SolanaAddress, accountData: NSData): Result<AddressLookupTableAccount, SolanaTransactionError> = accountData.decodeBytes { decode(key, it) }

/** As [SolanaTransactionCompiled.serializeMessage]: the exact message bytes signed by Ed25519. */
fun SolanaTransactionCompiled.serializeMessageData(): NSData = serializeMessage().toNSData()

/** As [SolanaTransactionCompiled.deserialize], from an unsigned or fully signed envelope. */
fun SolanaTransactionCompiled.Companion.deserialize(data: NSData): Result<SolanaTransactionCompiled, SolanaTransactionError> = data.decodeBytes(::deserialize)

/** As [SolanaTransactionUnsigned.deserializeMessage], from message bytes. */
fun SolanaTransactionUnsigned.Companion.deserializeMessage(data: NSData): Result<SolanaTransactionUnsigned, SolanaTransactionError> = data.decodeBytes(::deserializeMessage)

/** As [SolanaTransactionSigned.serialize]: the submit-ready envelope. */
fun SolanaTransactionSigned.serializeData(): NSData = serialize().toNSData()

/** As [SolanaTransactionSigned.deserialize], from a fully signed envelope. */
fun SolanaTransactionSigned.Companion.deserialize(data: NSData): Result<SolanaTransactionSigned, SolanaTransactionError> = data.decodeBytes(::deserialize)

/** As [SolanaTransactionSigned.Builder.serializePartial]: the envelope with zeros for missing signatures. */
fun SolanaTransactionSigned.Builder.serializePartialData(): NSData = serializePartial().toNSData()

/** As [SolanaTransactionSigned.Builder.deserializePartial], from a partially signed envelope. */
fun SolanaTransactionSigned.Builder.Companion.deserializePartial(data: NSData): Result<SolanaTransactionSigned.Builder, SolanaTransactionError> = data.decodeBytes(::deserializePartial)
