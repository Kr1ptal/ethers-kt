package io.ethers.solana.utils

/**
 * Strict UTF-8, as on-chain text must be. An unpaired surrogate throws [IllegalArgumentException] rather than the
 * [CharacterCodingException] the stdlib uses, which is checked on the JVM and is not a `RuntimeException` on
 * native, so it could neither be declared to Java nor caught by a Swift caller.
 */
internal fun String.encodeUtf8Strict(): ByteArray = try {
    encodeToByteArray(throwOnInvalidSequence = true)
} catch (e: CharacterCodingException) {
    throw IllegalArgumentException("Text is not valid UTF-16, so it has no UTF-8 encoding: ${e.message}", e)
}
