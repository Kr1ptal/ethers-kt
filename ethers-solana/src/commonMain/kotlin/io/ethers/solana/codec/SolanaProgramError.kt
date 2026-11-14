package io.ethers.solana.codec

import io.ethers.solana.utils.U32_MAX
import kotlin.jvm.JvmStatic

/**
 * A custom error a program reports through `InstructionError::Custom`.
 *
 * [code] is the unsigned 32-bit code, held as a [Long] as in `InstructionError.Custom`. Codes are
 * only meaningful within one program, since programs reuse the same numbers.
 */
interface SolanaProgramError {
    val code: Long
    val name: String
    val message: String?
}

/** Maps the custom error codes of one program to its errors. */
fun interface SolanaProgramErrorDecoder {
    /** The error declared for [code], or null when the program declares none. */
    fun decode(code: Long): SolanaProgramError?

    companion object {
        /** A decoder over [errors], which must have distinct codes in the unsigned 32-bit range. */
        @JvmStatic
        fun of(errors: Collection<SolanaProgramError>): SolanaProgramErrorDecoder {
            val byCode = HashMap<Long, SolanaProgramError>(errors.size)
            for (error in errors) {
                require(error.code in 0..U32_MAX) { "Error ${error.name} code ${error.code} is outside the u32 range" }
                val previous = byCode.put(error.code, error)
                require(previous == null) { "Errors ${previous?.name} and ${error.name} share code ${error.code}" }
            }
            return SolanaProgramErrorDecoder { byCode[it] }
        }
    }
}
