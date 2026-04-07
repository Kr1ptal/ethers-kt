package io.ethers.examples

import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.examples.UniswapV2FeeFinder.Companion.getFeePercent
import io.ethers.examples.gen.UniswapV2Pair
import io.ethers.providers.Provider
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import java.math.BigInteger

/**
 * Calculate Uniswap V2 pool fees. The fees are hardcoded, but we can calculate it by:
 * 1. find the latest swaps in the pool [univ2Pair],
 * 2. get the first swap in the block for the pool,
 * 3. get the pool state before the swap,
 * 4. infer the swap direction,
 * 5. calculate the fees using [getFeePercent].
 * */
class UniswapV2FeeFinder(rpcUrl: String, private val univ2Pair: Address) : Runnable {
    private val provider = Provider.fromUrl(rpcUrl).unwrap()

    override fun run() {
        val latest = provider.getBlockNumber().sendAwait().unwrap()
        val pair = UniswapV2Pair(provider, univ2Pair)

        val swaps = UniswapV2Pair.Swap.filter(provider).address(univ2Pair).blockRange(latest - 100, latest)
            .query()
            .sendAwait()
            .unwrap()

        // group the events by block number, and get the first event in the latest block in which there were any swaps
        val eventsPerBlock = swaps.groupBy { it.blockNumber }.toSortedMap()
        val event = eventsPerBlock[eventsPerBlock.lastKey()]!!.minBy { it.logIndex }

        // get the state of the pool at the start of the block in which the event was emitted
        val (reserve0, reserve1) = pair.getReserves().call(event.blockNumber - 1).sendAwait().unwrap()
        val (_, amount0In, amount1In, amount0Out, amount1Out) = event

        // infer the swap direction
        val (amountIn, amountOut) = if (amount0In > BigInteger.ZERO) {
            amount0In to amount1Out
        } else {
            amount1In to amount0Out
        }

        val (reserveIn, reserveOut) = if (amount0In > BigInteger.ZERO) {
            reserve0 to reserve1
        } else {
            reserve1 to reserve0
        }

        val factory = pair.factory().call(BlockId.LATEST).sendAwait().unwrap()
        println("Uniswap V2 fee for factory '$factory': ${getFeePercent(amountIn, amountOut, reserveIn, reserveOut)}")
    }

    companion object {
        private val FEE_DENOMINATOR: BigInteger = BigInteger.TEN.pow(4)

        /**
         * Calculate uniswap V2 pool swap fee, based on reserves before a swap. Formula is derived from:
         * ```solidity
         * function getAmountIn(uint amountOut, uint reserveIn, uint reserveOut) internal pure returns (uint amountIn) {
         *     require(amountOut > 0, 'UniswapV2Library: INSUFFICIENT_OUTPUT_AMOUNT');
         *     require(reserveIn > 0 && reserveOut > 0, 'UniswapV2Library: INSUFFICIENT_LIQUIDITY');
         *     uint numerator = reserveIn.mul(amountOut).mul(1000);
         *     uint denominator = reserveOut.sub(amountOut).mul(997);
         *     amountIn = (numerator / denominator).add(1);
         * }
         * ```
         * */
        private fun getFeePercent(
            amountIn: BigInteger,
            amountOut: BigInteger,
            reserveInToken: BigInteger,
            reserveOutToken: BigInteger,
        ): BigInteger {
            val numerator = amountOut * reserveInToken * FEE_DENOMINATOR
            val denominator = amountIn * reserveOutToken - amountOut * amountIn
            return FEE_DENOMINATOR - (numerator / denominator) - BigInteger.ONE
        }
    }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("UniswapV2FeeFinder")

    val rpcUrl by argParser.option(ArgType.String, description = "RPC URL").required()
    val univ2Pair by argParser.option(
        ArgType.String,
        description = "Uniswap V2 pair address. Defaults to WETH/USDC pool on mainnet",
    ).default("0xB4e16d0168e52d35CaCD2c6185b44281Ec28C9Dc")

    argParser.parse(args)

    UniswapV2FeeFinder(rpcUrl, Address(univ2Pair)).run()
}
