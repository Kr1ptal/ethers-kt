package io.ethers.solana.providers

import io.ethers.core.Result
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.SignatureStatus
import kotlin.time.Duration

/**
 * Seam through which each platform adds its own conveniences to [PendingSolanaTransaction].
 *
 * It exists so platform helpers can be inherited *members* rather than extension functions - Java
 * callers get `pending.awaitConfirmation()` instead of a static call. All implementation stays in
 * ordinary common code in [PendingSolanaTransaction].
 *
 * JVM and Android actualize this with blocking and `CompletableFuture` variants. A platform without
 * those primitives actualizes it with no extra members.
 */
expect interface PlatformPendingSolanaTransaction {
    suspend fun confirmation(
        commitment: Commitment = Commitment.CONFIRMED,
        interval: Duration = DEFAULT_CONFIRMATION_INTERVAL,
        timeout: Duration = DEFAULT_CONFIRMATION_TIMEOUT,
    ): Result<SignatureStatus, PendingSolanaTransaction.Error>
}
