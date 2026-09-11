package io.ethers.solana.providers

import io.ethers.core.Result
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SignatureStatus
import kotlin.time.Duration

actual interface PlatformPendingSolanaTransaction {
    actual suspend fun confirmation(
        commitment: Commitment,
        interval: Duration,
        timeout: Duration,
    ): Result<SignatureStatus, PendingSolanaTransaction.Error>
}
