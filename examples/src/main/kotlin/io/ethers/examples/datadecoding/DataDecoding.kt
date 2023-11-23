package io.ethers.examples.datadecoding

import UniswapV2Router02
import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiFunction
import io.ethers.core.types.Bytes
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default

/**
 * Decoding HEX data from a given swapExactTokensForTokens transaction. This can be accomplished in two scenarios:
 * when the ABI of the smart contract is available or when only the function signature is known.
 */
class DataDecoding(txInput: String) {
    private val calldata = Bytes(txInput)
    fun run() {
        // # Decode function input data when ABI is known
        val autoDec = UniswapV2Router02.FUNCTION_SWAP_EXACT_TOKENS_FOR_TOKENS.decodeCall(calldata)

        // # Manually decode function input data when we only know function signature
        val function = AbiFunction.parseSignature("swapExactTokensForTokens(uint256,uint256,address[],address,uint256)")
        val manualDec = AbiCodec.decodeWithPrefix(4 /*selector is 4 bytes long*/, function.inputs, calldata.value)

        println("""
        Decoded transaction input from ABI:
            amountIn: ${autoDec[0]}
            amountOutMin: ${autoDec[1]}
            path: [${(autoDec[2] as Array<*>)[0]}, ${(autoDec[2] as Array<*>)[1]}]
            to: ${autoDec[3]}
            deadline: ${autoDec[4]}
        
        Decoded transaction input from function signature:
            amountIn: ${manualDec[0]}
            amountOutMin: ${manualDec[1]}
            path: [${(manualDec[2] as Array<*>)[0]}, ${(manualDec[2] as Array<*>)[1]}]
            to: ${manualDec[3]}
            deadline: ${manualDec[4]}
    """.trimIndent())
    }
}

fun main(args: Array<String>) {
    // Parse input arguments
    val argParser = ArgParser("DataDecoding")

    // Calldata of any valid swapExactTokensForTokens transaction. Default value is from tx 0x4bcae8660e6732cbfdd355f2ddcfdfa989c6ff08f81bea22e9de9d1778450790
    val txInput by argParser.option(ArgType.String, description = "Transaction calldata")
        .default("0x38ed1739000000000000000000000000000000000000000000068b60f79065bc3a5e5b2b00000000000000000000000000000000000000000000000004daac6acd8c693c00000000000000000000000000000000000000000000000000000000000000a000000000000000000000000005b5952da949f25368a5473d3d59b5ac73fad48600000000000000000000000000000000000000000000000000000000655b5025000000000000000000000000000000000000000000000000000000000000000200000000000000000000000087d907568a0761ea45d2917e324557920668f224000000000000000000000000c02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")

    argParser.parse(args)

    DataDecoding(txInput).run()
}
