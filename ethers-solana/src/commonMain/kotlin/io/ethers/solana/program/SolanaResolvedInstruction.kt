package io.ethers.solana.program

import io.ethers.solana.instruction.Instruction

/**
 * An [instruction] built from [accounts] that were resolved from on-chain data, as the builders of generated program
 * bindings that take an api return it.
 *
 * Pass [accounts] to the builder that takes the accounts class to build further instructions without fetching again.
 * Fixed addresses and program derived addresses never change. Accounts read from other accounts' data, through
 * relations or seeds, are only valid while that data is unchanged on-chain.
 */
data class SolanaResolvedInstruction<out A>(val instruction: Instruction, val accounts: A)
