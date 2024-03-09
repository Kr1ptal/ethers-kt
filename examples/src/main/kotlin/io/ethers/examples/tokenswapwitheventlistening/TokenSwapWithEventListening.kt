package io.ethers.examples.tokenswapwitheventlistening

import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.examples.gen.UniswapV2Factory
import io.ethers.examples.gen.UniswapV2Pair
import io.ethers.examples.gen.UniswapV2Router02
import io.ethers.providers.Provider
import io.ethers.signers.PrivateKeySigner
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.math.BigInteger

/**
 * Uniswap V2 ETH to ERC20 swap with listening to UniswapV2Pair Sync event
 */
class TokenSwapWithEventListening(
    privateKey: String,
    routerAddress: String,
    rpcUrl: String,
    private val wethAddress: String,
    private val tokenAddress: String,
    private val ethAmount: BigInteger,
) {
    // Init provider for swap and subscription
    private val provider = Provider.fromUrl(rpcUrl).unwrap()
    private val signer = PrivateKeySigner(privateKey)
    private val router = UniswapV2Router02(provider, Address(routerAddress))

    fun run() {
        // Get pool address via factory
        val factoryAddress = router.factory().call(BlockId.LATEST).sendAwait().unwrap()
        val pairAddress = UniswapV2Factory(provider, factoryAddress)
            .getPair(Address(wethAddress), Address(tokenAddress))
            .call(BlockId.LATEST)
            .sendAwait()
            .unwrap()

        if (pairAddress == Address.ZERO) {
            throw Exception("Pair does not exist!")
        }

        // # Event listening
        // We listen to UniswapV2Pair Sync events on provided pairAddress, using a subscription or polling mechanism,
        // depending on the provider capabilities (WS or HTTP/S).
        //
        // It is also possible to filter based on topics and block numbers
        println("Listening for Sync events on pair: $pairAddress")

        val filter = UniswapV2Pair.Sync.filter(provider).address(pairAddress)
        val stream = filter
            .subscribe()
            .sendAwait()
            .orElse { filter.watch().sendAwait() } // Fallback to polling if subscription is not supported
            .unwrap()

        // We use forEachAsync which listens to events in a separate thread, to avoid blocking the caller
        stream.forEachAsync {
            println("Found tx (${it.transactionHash}) with Sync event")
        }

        // # Executing swap that emits Sync event
        println("Executing swapExactETHForTokens...")
        val deadline = ((System.currentTimeMillis() / 1000) + 1800).toBigInteger()
        val call = router.swapExactETHForTokens(
            BigInteger.ZERO, // amountOutMin
            arrayOf(Address(wethAddress), Address(tokenAddress)), // path
            signer.address, // to
            deadline,
        )
        val pendingTx = call.value(ethAmount).send(signer).sendAwait().unwrap()

        println("Wait for transaction: ${pendingTx.hash} to be included in a block...")
        val receipt = pendingTx.awaitInclusion(retries = 10).unwrap()
        println("Buy tx ${receipt.transactionHash} was included in block ${receipt.blockNumber}")

        // We don't close the wsClient to keep listening to events
    }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("TokenSwapWithEventListening")

    val privateKey by argParser.option(ArgType.String, description = "Private key").required()
    val rpcUrl by argParser.option(ArgType.String, description = "RPC URL").required()
    val routerAddress by argParser.option(ArgType.String, description = "Uniswap V2 Router address")
        .default("0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D")
    val wethAddress by argParser.option(ArgType.String, description = "WETH address")
        .default("0xb4fbf271143f4fbf7b91a5ded31805e42b2208d6") // Goerli WETH address
    val tokenAddress by argParser.option(ArgType.String, description = "Token address")
        .default("0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984") // Goerli UNI address
    val ethAmount by argParser.option(ArgType.String, description = "Amount of ETH in wei to spend when buying tokens")
        .default("1000000000000") // 0,000001 ETH

    argParser.parse(args)

    TokenSwapWithEventListening(
        privateKey,
        routerAddress,
        rpcUrl,
        wethAddress,
        tokenAddress,
        ethAmount.toBigInteger(),
    ).run()
}
