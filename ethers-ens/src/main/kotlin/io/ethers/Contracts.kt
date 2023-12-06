package io.ethers

import io.ethers.core.types.Address

/** ENS registry contract addresses. */
object Contracts {

    const val MAINNET = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
    const val ROPSTEN = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
    const val RINKEBY = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
    const val GOERLI = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
    const val SEPOLIA = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"

    // TODO - for different chains
    fun getRegistryAddress(chainId: Long): Address {
        return Address(MAINNET)
    }
}
