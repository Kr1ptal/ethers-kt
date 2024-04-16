package io.ethers.examples.batchrequests

import io.ethers.abi.call.Multicall3
import io.ethers.abi.call.aggregate
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.examples.gen.ERC20
import io.ethers.providers.Provider
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.math.BigInteger

/**
 * Multicall example demonstrates how to aggregate multiple contract calls into Multicall3.
 */
class Multicall(
    private val rpcUrl: String,
    private val tokens: List<Address>,
) {
    // Init provider
    private val provider = Provider.fromUrl(rpcUrl).unwrap()

    fun run() {
        // We are nesting multiple aggregations. By default, calls cannot revert. To prevent top-level
        // aggregation from reverting when a call fails, use .allowFailure() as demonstrated below
        val aggregate = Multicall3.aggregate(
            tokens.map { ERC20(provider, it).name() }.aggregate().allowFailure(),
            tokens.map { ERC20(provider, it).symbol() }.aggregate(),
            tokens.map { ERC20(provider, it).decimals() }.aggregate(),
        ).call(BlockId.LATEST).sendAwait().unwrap()

        // Each aggregated call result is returned in the same order the calls were defined
        val nameResults = aggregate.getAsAggregation<String>(0).unwrapOrNull()
        val symbolResults = aggregate.getAsAggregation<String>(1).unwrapOrNull()
        val decimalsResults = aggregate.getAsAggregation<BigInteger>(2).unwrapOrNull()

        for (i in tokens.indices) {
            println(
                """
            Token: ${tokens[i]}
            Name: ${nameResults?.get(i)?.unwrap()}
            Symbol: ${symbolResults?.get(i)?.unwrap()}
            Decimals: ${decimalsResults?.get(i)?.unwrap()}
                """.trimIndent() + '\n',
            )
        }
    }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("Multicall")

    val rpcUrl by argParser.option(ArgType.String, description = "RPC URL")
        .default("https://ethereum.publicnode.com") // Mainnet HTTP url

    // Mainnet tokens
    val tokens by argParser.option(ArgType.String, description = "List of ERC20 tokens")
        .default(
            "0xdAC17F958D2ee523a2206206994597C13D831ec7, " + // USDT
                "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48, " + // USDC
                "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", // WETH
        )

    argParser.parse(args)

    // Create a list of ERC20 token addresses
    val erc20Tokens = tokens.split(", ").map { Address(it) }

    Multicall(rpcUrl, erc20Tokens).run()
}
