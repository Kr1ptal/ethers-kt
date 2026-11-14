package io.ethers.solana.program

import io.ethers.providers.RpcError
import io.ethers.providers.types.RpcSubscribe
import io.ethers.solana.codec.SolanaEvent
import io.ethers.solana.codec.SolanaEventFactory
import io.ethers.solana.providers.LogsFilter
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.Commitment

/**
 * Filter over the events of [factory]'s type that a program emits, like `EventFilter` for EVM events.
 *
 * Only events emitted to the logs can be subscribed to: the logs subscription carries no inner
 * instructions, so events emitted through self-CPIs are not delivered. Decode those from transactions
 * with [eventRecords].
 */
class SolanaEventFilter<T : SolanaEvent>(
    private val api: SolanaApi,
    private val factory: SolanaEventFactory<T>,
    private var program: SolanaAddress,
) {
    private var commitment: Commitment = api.defaultCommitment

    /** Select events emitted by [programId] instead, such as an alternate deployment of the program. */
    fun program(programId: SolanaAddress): SolanaEventFilter<T> {
        program = programId
        return this
    }

    fun commitment(commitment: Commitment): SolanaEventFilter<T> {
        this.commitment = commitment
        return this
    }

    /**
     * Subscribe to the events of this type the program emits to the logs, one element per transaction with
     * its events in log order. Transactions without such an event are skipped, as are events that do not
     * decode. Events of failed transactions are included, marked as such in their context.
     */
    fun subscribe(): RpcSubscribe<List<T>, RpcError> {
        val program = program
        return api.subscribeLogs(LogsFilter.Mentions(program), commitment).map { channel ->
            channel.mapNotNull { notification ->
                notification.logEventRecords(program).decodeEvents(program, factory).takeIf { it.isNotEmpty() }
            }
        }
    }
}
