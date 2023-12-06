package io.ethers

import io.ethers.core.ENSRegistryWithFallback
import io.ethers.core.PublicResolver
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.providers.Provider

class EnsResolver(private val provider: Provider) {

    fun resolveName(ensName: String): Address {
        if (ensName.isBlank() || (ensName.trim().length == 1 && ensName.contains("."))) {
            return Address.ZERO
        }

        if (isValidEnsName(ensName)) {
            val nameHash = NameHash.nameHash(ensName)
            val resolverAddress = getResolverAddress(nameHash)

            if (resolverAddress == Address.ZERO) {
                throw Exception("Resolver for ens name $ensName does not exist!")
            }

            val resolver = getResolver(nameHash)

            try {
                return resolver.addr(Bytes(nameHash)).call(BlockId.LATEST).sendAwait().resultOrThrow()
            } catch (e: Exception) {
                throw RuntimeException("Unable to execute request: ", e)
            }
        } else {
            return Address(ensName)
        }
    }

    private fun isValidEnsName(ensName: String): Boolean {
        return if (ensName.contains(".")) {
            true
        } else {
            try {
                Address(ensName)
                false
            } catch (e: Exception) {
                true
            }
        }
    }

    private fun getResolver(nameHash: ByteArray): PublicResolver {
        // TODO - check if node is fully synced?
        return PublicResolver(provider, getResolverAddress(nameHash))
    }

    private fun getResolverAddress(nameHash: ByteArray): Address {
        val registryContract = ENSRegistryWithFallback(provider, Contracts.getRegistryAddress(provider.chainId))
        return registryContract.resolver(Bytes(nameHash)).call(BlockId.LATEST).sendAwait().resultOrThrow()
    }

    companion object {
        fun Provider.resolveName(ensName: String): Address {
            val ensResolver = EnsResolver(this)
            return ensResolver.resolveName(ensName)
        }
    }
}