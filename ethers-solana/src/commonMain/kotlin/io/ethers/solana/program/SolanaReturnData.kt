@file:JvmName("SolanaReturnData")

package io.ethers.solana.program

import io.ethers.core.Result
import io.ethers.core.failure
import io.ethers.solana.codec.SolanaDecodeError
import io.ethers.solana.codec.SolanaInstructionDescriptor
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.ReturnData
import kotlin.jvm.JvmName

/**
 * Decode [returnData] as this instruction's return value, when [programId] set it.
 *
 * Return data names only the program that set it, not the instruction, so the caller chooses which
 * instruction's descriptor to decode it with. Data another program set is
 * [SolanaDecodeError.Unrecognized].
 */
fun <A, R> SolanaInstructionDescriptor.Returning<A, R>.decodeReturn(returnData: ReturnData, programId: SolanaAddress): Result<R, SolanaDecodeError> {
    if (returnData.programId != programId) {
        return failure(SolanaDecodeError.Unrecognized(returnData.data, "Return data was set by ${returnData.programId}, not $programId"))
    }
    return decodeReturn(returnData.data)
}
