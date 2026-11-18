package io.ethers.solana.instruction

import io.ethers.crypto.Hashing
import io.ethers.solana.types.Programs
import io.ethers.solana.utils.littleEndianInto
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

object Secp256k1Program {
    @JvmField val ID = Programs.SECP256K1

    /** Derive the 20-byte Ethereum address from a 64-byte uncompressed X || Y public key (no 04 prefix). */
    @JvmStatic
    fun publicKeyToEthAddress(publicKey: ByteArray): ByteArray {
        require(publicKey.size == 64) { "Public key must contain 64 bytes without the 04 prefix" }
        return Hashing.keccak256(publicKey).copyOfRange(12, 32)
    }

    /**
     * Verify a compact 64-byte r || s signature against keccak256(message), without an Ethereum prefix.
     * [recoveryId] is 0..3, not an Ethereum v value. [instructionIndex] MUST be this instruction's
     * final zero-based index, including any generated compute-budget or nonce instructions.
     * The compiler does not relocate these references. Verification happens on-chain.
     */
    @JvmStatic
    fun verify(ethAddress: ByteArray, message: ByteArray, signature: ByteArray, recoveryId: Int, instructionIndex: Int): Instruction {
        require(ethAddress.size == 20) { "Ethereum address must contain 20 bytes" }
        require(signature.size == 64) { "Signature must contain 64 bytes" }
        require(recoveryId in 0..3) { "Recovery ID must be in 0..3" }
        require(instructionIndex in 0..255) { "Instruction index must fit u8" }
        require(message.size <= 65535) { "Message length must fit u16" }
        val data = ByteArray(97 + message.size)
        data[0] = 1
        littleEndianInto(data, 1, 32L, 2)
        data[3] = instructionIndex.toByte()
        littleEndianInto(data, 4, 12L, 2)
        data[6] = instructionIndex.toByte()
        littleEndianInto(data, 7, 97L, 2)
        littleEndianInto(data, 9, message.size.toLong(), 2)
        data[11] = instructionIndex.toByte()
        ethAddress.copyInto(data, 12)
        signature.copyInto(data, 32)
        data[96] = recoveryId.toByte()
        message.copyInto(data, 97)
        return Instruction(ID, emptyList(), data)
    }

    @JvmStatic
    fun verifyWithPublicKey(publicKey: ByteArray, message: ByteArray, signature: ByteArray, recoveryId: Int, instructionIndex: Int): Instruction = verify(publicKeyToEthAddress(publicKey), message, signature, recoveryId, instructionIndex)
}
