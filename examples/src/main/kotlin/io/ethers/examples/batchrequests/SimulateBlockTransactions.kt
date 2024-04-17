package io.ethers.examples.batchrequests

import io.ethers.core.isFailure
import io.ethers.core.types.StateOverride
import io.ethers.core.types.toBlockOverride
import io.ethers.core.types.tracers.CallTracer
import io.ethers.core.types.tracers.MuxTracer
import io.ethers.core.types.tracers.PrestateTracer
import io.ethers.core.types.tracers.TracerConfig
import io.ethers.providers.Provider
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required

class SimulateBlockTransactions(
    rpcUrl: String,
) {
    // Init provider
    private val provider = Provider.fromUrl(rpcUrl).unwrap()

    fun run() {
        // For each new block, simulate all transactions in the block with traceCall
        provider.subscribeNewHeads().sendAwait().unwrap().forEach { head ->
            println("New block: ${head.number}")

            val blockWithTransactions = provider.getBlockWithTransactions(head.number).sendAwait().unwrap()
            val stateOverrides = StateOverride()
            val blockOverrides = blockWithTransactions.toBlockOverride()
            for (tx in blockWithTransactions.transactions) {
                val config = TracerConfig(
                    tracer = MULTICALL_MUX_TRACER,
                    stateOverrides = stateOverrides,
                    blockOverrides = blockOverrides,
                )

                val txReceipt = provider.getTransactionReceipt(tx.hash).sendAsync()

                // The Transaction (including RPCTransaction) implements the IntoCallRequest interface,
                // allowing it to be directly utilized in the call without the need for manual conversion to CallRequest.
                val unwrappedResult = provider.traceCall(tx, head.number - 1, config).sendAsync()
                if (unwrappedResult.get().isFailure()) {
                    continue
                }

                val result = unwrappedResult.get().unwrap()

                // Accumulate all changes made by transactions, upon which subsequent transactions are executed.
                // This mirrors the process of building a block on the node.
                // Utilize takeChanges to bypass copying the resulting state override, as it is not required post-call.
                stateOverrides.takeChanges(result[PrestateTracer::class.java].toStateOverride())

                val call = result[CallTracer::class.java]

                if (call.isError == txReceipt.get().isFailure() && call.gasUsed == txReceipt.get().unwrap()
                        .get().gasUsed
                ) {
                    println("Transaction simulation ${tx.hash} was correct")
                } else {
                    println("Transaction simulation ${tx.hash} was incorrect")
                }
            }
        }
    }

    companion object {
        private val MULTICALL_MUX_TRACER = MuxTracer(
            PrestateTracer(diffMode = true),
            CallTracer(onlyTopCall = true, withLog = true),
        )
    }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("IntoCallRequest")

    // Problems with public ws rpc url - add your own
    val rpcUrl by argParser.option(ArgType.String, description = "RPC URL").required()

    argParser.parse(args)

    SimulateBlockTransactions(rpcUrl).run()
}
