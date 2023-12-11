package io.ethers

import io.ethers.EnsResolver.Error.FailedToResolve
import io.ethers.EnsResolver.Error.UnknownResolver
import io.ethers.core.ENSRegistryWithFallback
import io.ethers.core.PublicResolver
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.providers.Provider
import io.ethers.providers.types.RpcResponse

class EnsResolver(private val provider: Provider) {

    /**
     * Resolve ens name to Address. On error return [RpcResponse] as error.
     */
    fun resolveName(ensName: String): RpcResponse<Address> {
        // Check that ens name is valid
        if (ensName.isBlank() || (ensName.trim().length == 1 && ensName.contains("."))) {
            return RpcResponse.error(Error.EnsNameInvalid)
        }

        val nameHashResponse = NameHash.nameHash(ensName)
        if (nameHashResponse.isError) return nameHashResponse.propagateError()
        val nameHash = nameHashResponse.resultOrThrow()

        val resolverResponse = getResolver(nameHash)
        if (resolverResponse.isError) return resolverResponse.propagateError()

        // Unwrap resolver from RpcResponse and call its addr() function.
        // If RpcResponse is an error, map it to error FailedToResolve.
        val resolver = resolverResponse.resultOrThrow()
        return resolver.addr(Bytes(nameHash))
            .call(BlockId.LATEST)
            .sendAwait()
            .mapError { FailedToResolve(resolver.address, ensName, it) }
    }

    /**
     * Get [PublicResolver] for [nameHash] using [ENSRegistryWithFallback] of current chain.
     */
    private fun getResolver(nameHash: ByteArray): RpcResponse<PublicResolver> {
        return getResolverAddress(nameHash).map { PublicResolver(provider, it) }
    }

    /**
     * Get resolver address for [nameHash] from [ENSRegistryWithFallback].
     */
    private fun getResolverAddress(nameHash: ByteArray): RpcResponse<Address> {
        val registryAddr = getRegistryAddress(provider.chainId)
            ?: return RpcResponse.error(Error.RegistryAddrNotExists(provider.chainId))

        val registryContract = ENSRegistryWithFallback(provider, registryAddr)
        return registryContract.resolver(Bytes(nameHash))
            .call(BlockId.LATEST)
            .sendAwait()
            .mapError { UnknownResolver(it) }
    }

    /**
     * Possible errors during ens name resolution
     */
    sealed class Error : RpcResponse.Error() {
        /**
         * Ens name is not valid.
         */
        data object EnsNameInvalid : Error()

        /**
         * Error on ens name [NameHash.normalise] attempt.
         */
        data class Normalisation(val cause: Exception) : Error() {
            override fun doThrow() {
                throw RuntimeException("Normalisation failed: $cause")
            }
        }

        /**
         * Registry address does not exist for chain.
         */
        data class RegistryAddrNotExists(val chainId: Long) : Error() {
            override fun doThrow() {
                throw RuntimeException("Registry address not found for chain $chainId!")
            }
        }

        /**
         * Resolver address not found for given nameHash on registry.
         */
        data class UnknownResolver(
            val cause: RpcResponse.Error,
        ) : Error() {
            override fun doThrow() {
                throw RuntimeException("Resolver for given nameHash was not found: $cause")
            }
        }

        /**
         * Resolver for ensName exists, but was not able to resolve it.
         */
        data class FailedToResolve(
            val resolverAddr: Address,
            val ensName: String,
            val cause: RpcResponse.Error,
        ) :
            Error() {
            override fun doThrow() {
                throw RuntimeException("Failed to resolve ens name: $ensName with resolver $resolverAddr: $cause")
            }
        }
    }

    companion object {
        // TODO - refactor
        private val MAINNET_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val ROPSTEN_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val RINKEBY_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val GOERLI_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val SEPOLIA_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")

        fun getRegistryAddress(chainId: Long): Address? {
            return when (chainId) {
                1L -> MAINNET_REGISTRY_ADDR
                3L -> ROPSTEN_REGISTRY_ADDR
                4L -> RINKEBY_REGISTRY_ADDR
                5L -> GOERLI_REGISTRY_ADDR
                11155111L -> SEPOLIA_REGISTRY_ADDR
                else -> return null
            }
        }

        fun Provider.resolveName(ensName: String): RpcResponse<Address> {
            val ensResolver = EnsResolver(this)
            return ensResolver.resolveName(ensName)
        }
    }
}
