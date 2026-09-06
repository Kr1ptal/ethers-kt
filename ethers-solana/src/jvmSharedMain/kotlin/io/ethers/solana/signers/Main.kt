package io.ethers.solana.signers

import io.ethers.solana.providers.SolanaCluster
import io.ethers.solana.providers.SolanaProvider
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("starting main")
    SolanaProvider.builder(SolanaCluster.MAINNET).build().unwrap().subscribeLogs().send().unwrap().forEach {
        println(it)
    }
}
