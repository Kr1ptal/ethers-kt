package io.ethers.examples.balancetracker

import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.examples.gen.ERC20
import io.ethers.providers.Provider
import io.ethers.providers.WsClient
import io.ethers.providers.types.sendAwait
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.math.BigInteger

/**
 * Monitoring of ETH balance and balance of list of ERC20 tokens for a given address, using manual request batching.
 */

class BalanceTracker(
    private val address: Address,
    wsRpcUrl: String,
    private val tokenList: List<String>,
) {
    // Init providers
    private val wsClient = WsClient(wsRpcUrl)
    private val provider = Provider(wsClient)

    fun run() {
        val tokens = tokenList.map { ERC20(provider, Address(it)) }

        // Get symbols and decimals for each token
        // When using .sendAwait on a list of RpcRequests, it returns a BatchResponse that we can iterate through to retrieve the results
        val symbols = tokens.map { it.symbol().call(BlockId.LATEST) }.sendAwait().map { it.resultOrThrow() }
        val decimals = tokens.map { it.decimals().call(BlockId.LATEST) }.sendAwait().map { it.resultOrThrow() }

        // Get balances for ETH and each token
        var balanceEth = provider.getBalance(address, BlockId.LATEST).sendAwait().resultOrThrow()
        var balances = tokens.map { it.balanceOf(address).call(BlockId.LATEST) }.sendAwait().map { it.resultOrThrow() }

        println("Balances for block ${provider.getBlockNumber().sendAwait().resultOrThrow()}")
        println("ETH - ${balanceEth.toBigDecimal(18).toPlainString()}")
        displayTokenBalances(symbols, decimals, balances)

        // For each new block, update token balances and display them
        provider.subscribeNewHeads().sendAwait().resultOrThrow().forEach { head ->
            balanceEth = provider.getBalance(address, BlockId.LATEST).sendAwait().resultOrThrow()
            balances = tokens.map { it.balanceOf(address).call(BlockId.LATEST) }.sendAwait().map { it.resultOrThrow() }

            println("\nBalances for block ${head.number}")
            println("ETH - ${balanceEth.toBigDecimal(18).toPlainString()}")
            displayTokenBalances(symbols, decimals, balances)
        }

        // Close web socket client
        wsClient.close()
    }
}

private fun displayTokenBalances(symbols: List<String>, decimals: List<BigInteger>, balances: List<BigInteger>) {
    balances.forEachIndexed { i, balance ->
        val symbol = symbols[i]
        val scaled = balance.toBigDecimal(decimals[i].toInt())
        println("$symbol - ${scaled.toPlainString()}")
    }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("BalanceTracker")

    // Problems with public ws rpc url - add your own
    val wsRpc by argParser.option(ArgType.String, description = "WS RPC URL").required()
    val address by argParser.option(ArgType.String, description = "Token holder address")
        .default("0x0D0707963952f2fBA59dD06f2b425ace40b492Fe") // Gate.io address
    val tokenList by argParser.option(ArgType.String, description = "Observed token list, separated by ','")
        .default(
            "0xff56Cc6b1E6dEd347aA0B7676C85AB0B3D08B0FA," +
                "0xfe9A29aB92522D14Fc65880d817214261D8479AE," +
                "0xfc05987bd2be489ACCF0f509E44B0145d68240f7," +
                "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
        )

    argParser.parse(args)

    val balanceTracker = BalanceTracker(Address(address), wsRpc, tokenList.split(','))
    balanceTracker.run()
}
