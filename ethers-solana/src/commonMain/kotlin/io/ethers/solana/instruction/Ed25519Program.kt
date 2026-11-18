package io.ethers.solana.instruction

import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.utils.littleEndianInto
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

object Ed25519Program {
    @JvmField val ID = Programs.ED25519

    /** Verify a detached signature on the exact message bytes on-chain. All data references point to this instruction. */
    @JvmStatic
    fun verify(publicKey: SolanaAddress, message: ByteArray, signature: SolanaSignature): Instruction {
        require(message.size <= 65535) { "Message length must fit u16" }
        val data = ByteArray(112 + message.size)
        data[0] = 1
        val offsets = listOf(48, 65535, 16, 65535, 112, message.size, 65535)
        offsets.forEachIndexed { index, value -> littleEndianInto(data, 2 + index * 2, value.toLong(), 2) }
        publicKey.asByteArray().copyInto(data, 16)
        signature.asByteArray().copyInto(data, 48)
        message.copyInto(data, 112)
        return Instruction(ID, emptyList(), data)
    }
}
