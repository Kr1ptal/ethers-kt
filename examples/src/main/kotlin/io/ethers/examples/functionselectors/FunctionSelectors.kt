package io.ethers.examples.functionselectors

import io.ethers.examples.ConstantsMainnet
import io.ethers.providers.Provider
import io.ethers.providers.WsClient

/**
 * This example shows how to search for all transactions for specific function selector, starting from the most recent block
 */
fun main() {
    // Initialize provider for subscription and for sending transactions
    val wsClient = WsClient(ConstantsMainnet.WS_URL)
    val wsProvider = Provider(wsClient)

    // swapExactTokensForTokens(uint256 amountIn, uint256 amountOutMin, address[] calldata path, address to, uint256 deadline)
    val selector = "0x38ed1739"
    var blockNumber = wsProvider.getBlockNumber().sendAwait().resultOrThrow()

    println("Searching for transactions with selector $selector, starting with block $blockNumber")
    while (true) {
        val block = wsProvider.getBlockWithTransactions(blockNumber).sendAwait().resultOrThrow()
        block.transactions
            .filter { it.data != null &&
                    it.data.toString().length > 10 &&
                    it.data.toString().substring(0..9) == selector }
            .forEach { println("block: $blockNumber, tx: ${it.hash}") }
        blockNumber--
    }
}
