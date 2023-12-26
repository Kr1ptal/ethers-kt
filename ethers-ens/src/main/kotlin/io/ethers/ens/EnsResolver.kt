package io.ethers.ens

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.core.ENSRegistryWithFallback
import io.ethers.core.FastHex
import io.ethers.core.Jackson
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.providers.Provider
import io.ethers.providers.types.RpcResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.CompletableFuture

private val ENSIP_10_INTERFACE_ID = Bytes("0x9061b923")
private val CALLBACK_FUNCTION_PARAM_TYPES = listOf(AbiType.Bytes, AbiType.Bytes)
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val CCIP_LOOKUP_LIMIT = 4
class EnsResolver(private val provider: Provider) {
    // TODO
    private val client: OkHttpClient = OkHttpClient()
    private val LOG = getLogger()

    data class EnsGatewayRequestDTO(val data: Bytes)

    /**
     * Resolve ens name to Address. On error return [RpcResponse] as error.
     */
    fun resolveName(ensName: String): CompletableFuture<RpcResponse<Address>> = CompletableFuture.supplyAsync {
        OffchainResolver.ERRORS
        // Check that ens name is valid
        if (ensName.isBlank() || (ensName.trim().length == 1 && ensName.contains("."))) {
            return@supplyAsync RpcResponse.error(Error.EnsNameInvalid)
        }

        val nameHashResponse = NameHash.nameHash(ensName)
        if (nameHashResponse.isError) return@supplyAsync nameHashResponse.propagateError()
        val nameHash = nameHashResponse.resultOrThrow()

        val resolverResponse = getResolver(ensName)
        if (resolverResponse.isError) return@supplyAsync resolverResponse.propagateError()

        // Unwrap resolver from RpcResponse and call its addr() function.
        // If RpcResponse is an error, map it to error FailedToResolve.
        val resolver = resolverResponse.resultOrThrow()

        val supportsWildcard =
            resolver.supportsInterface(ENSIP_10_INTERFACE_ID).call(BlockId.LATEST).sendAwait().resultOrThrow()

        if (supportsWildcard) {
            val dnsEncoded = NameHash.dnsEncode(ensName)
            val addrFunction = OffchainResolver.FUNCTION_ADDR.encodeCall(arrayOf(Bytes(nameHash)))

            val resolveResult = resolver
                .resolve(dnsEncoded, addrFunction)
                .call(BlockId.LATEST)
                .sendAwait()
                // todo properly decode dynamic bytes to address
                .map {
                    // 64 do 84 index
                    Address(it.value.sliceArray(12..32))
                }

            // try to decode OffchainLookup error
            val resolveLookupRevert = resolveResult.error?.asTypeOrNull<OffchainResolver.OffchainLookup>()
            if (resolveLookupRevert == null) return@supplyAsync resolveResult
            else {
                return@supplyAsync resolveOffchain(
                    resolveLookupRevert,
                    resolver,
                    CCIP_LOOKUP_LIMIT,
                )
            }
        } else {
            // Resolve ens name with resolver. Return different errors on empty address and failure to resolve
            val address = resolver.addr(Bytes(nameHash))
                .call(BlockId.LATEST)
                .map {
                    if (it.isError) {
                        return@map RpcResponse.error(Error.FailedToResolve(resolver.address, ensName))
                    }

                    if (it.resultOrThrow() == Address.ZERO) {
                        return@map RpcResponse.error(
                            Error.UnknownEnsName(
                                resolver.address,
                                FastHex.encodeWithPrefix(nameHash),
                            ),
                        )
                    }

                    return@map RpcResponse.result(it)
                }
                .sendAwait()

            if (address.isError) {
                return@supplyAsync address.propagateError()
            }
            return@supplyAsync address.resultOrThrow()
        }
    }

    /**
     * If result of resolver.resolve() call is [OffchainResolver.OffchainLookup] error, try to resolve the name using
     * [ERC-3668: CCIP offchain data retrieval](https://eips.ethereum.org/EIPS/eip-3668)
     */
    private fun resolveOffchain(
        resolveLookupRevert: OffchainResolver.OffchainLookup,
        resolver: OffchainResolver,
        lookupLimit: Int,
    ): RpcResponse<Address> {
        // OffchainLookup.sender has to be resolver address
        if (resolveLookupRevert.sender != resolver.address) {
            return RpcResponse.error(Error.NestedOffchainLookup)
        }

        // get gateway result by trying urls one by one and passing sender and data returned by OffchainLookup error
        val gatewayResult = httpCall(resolveLookupRevert.urls, resolveLookupRevert.sender, resolveLookupRevert.callData)

        if (gatewayResult.isError) {
            return gatewayResult.propagateError()
        }

        // call resolver.callbackFunction(gatewayResult, extraData). If this call is CCIP, repeat the procedure.
        val callbackData = AbiCodec.encodeWithPrefix(
            resolveLookupRevert.callbackFunction.value,
            CALLBACK_FUNCTION_PARAM_TYPES,
            arrayOf(gatewayResult.resultOrThrow(), resolveLookupRevert.extraData),
        )

        // dynamic bytes
        val callbackResult = provider.call(
            CallRequest {
                to = resolver.address
                data = Bytes(callbackData)
            },
            BlockId.LATEST,
        )
            .sendAwait()

        // Support for multiple lookups by the same contract
        val callbackLookupRevert = callbackResult.error?.asTypeOrNull<OffchainResolver.OffchainLookup>()
        if (callbackLookupRevert != null) {
            if (lookupLimit <= 0) return RpcResponse.error(Error.CcipLookupLimit)

            return resolveOffchain(callbackLookupRevert, resolver, lookupLimit - 1)
        } else {
            // decode dynamic bytes to address
            val resolvedDecoded = AbiCodec.decode(AbiType.Bytes, callbackResult.resultOrThrow().value) as Bytes
            val resolvedAddress = AbiCodec.decode(AbiType.Address, resolvedDecoded.value) as Address
            return RpcResponse.result(resolvedAddress)
        }
    }

    /**
     * Executes Cross-Chain Interoperability Protocol (CCIP-Read) request and returns opaque gateway response as [Bytes]
     * which is sent to the callbackFunction on Offchain Resolver contract.
     *
     * @param urls urls parameter of Offchain revert [OffchainResolver.OffchainLookup]
     * @param sender sender parameter of [OffchainResolver.OffchainLookup] - replacing {sender} RPC call parameter
     * @param calldata calldata parameter of [OffchainResolver.OffchainLookup] - replacing {data} RPC call parameter
     */
    private fun httpCall(urls: Array<String>, sender: Address, calldata: Bytes): RpcResponse<Bytes> {
        val errors = mutableListOf<String>()

        for (url in urls) {
            // If url contains {data} parameter the request is GET, otherwise POST
            val request = buildRequest(url, sender, calldata)
            if (request.isError) {
                return request.propagateError()
            }

            val call = client.newCall(request.resultOrThrow())

            try {
                call.execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body
                            ?: return RpcResponse.error(Error.CcipCallFailed("Response body is null (url: $url)", null))

                        val result = responseBody.byteStream().readBytes()

                        val gatewayRequestDTO = Jackson.MAPPER.readValue(result, EnsGatewayRequestDTO::class.java)
                        val data = gatewayRequestDTO.data

                        return RpcResponse.result(data)
                    } else {
                        val statusCode = response.code
                        // 4xx - return an error and stop
                        if (statusCode in 400..499) {
                            val mes = "Blocking error during CCIP call: url: $url, error: ${response.message}"
                            LOG.err { mes }
                            return RpcResponse.error(Error.CcipStatus4xx(mes))
                        }

                        // 5xx - server issue, try different url
                        errors.add(response.message)
                        LOG.warn("500 error during CCIP call: url: $url, error: ${response.message}")
                    }
                }
            } catch (e: IOException) {
                LOG.err(e) { e.message ?: "" }
                return RpcResponse.error(Error.CcipCallFailed("Unknown error", e))
            }
        }

        if (errors.isEmpty()) {
            return RpcResponse.error(Error.CcipUrlsMissing)
        } else {
            return RpcResponse.error(Error.CcipStatus5xx(errors))
        }
    }

    /**
     * Builds [okhttp3.Request] CCIP-read request from [url], [sender] and [calldata], where [sender] and [calldata]
     * are RPC request parameters. If RPC url contains {data}, the request is GET, otherwise POST.
     */
    private fun buildRequest(url: String, sender: Address, calldata: Bytes): RpcResponse<Request> {
        if (calldata.isEmpty) {
            return RpcResponse.error(Error.CcipCalldataEmpty)
        }
        if (!url.contains("{sender}")) {
            return RpcResponse.error(Error.CcipSenderEmpty)
        }

        // URL expansion
        // TODO is calldata.toString() ok?
        val href = url.replace("{sender}", sender.toString()).replace("{data}", calldata.toString())
        val builder = Request.Builder().url(href)

        return if (url.contains("{data}")) {
            RpcResponse.result(builder.get().build())
        } else {
            val requestDTO = EnsGatewayRequestDTO(calldata)

            val mapper = Jackson.MAPPER
            RpcResponse.result(
                builder.post(mapper.writeValueAsString(requestDTO).toRequestBody(JSON_MEDIA_TYPE))
                    .addHeader("Content-Type", "application/json")
                    .build(),
            )
        }
    }

    /**
     * Get [OffchainResolver] for [ensName] using [ENSRegistryWithFallback] of current chain.
     */
    private fun getResolver(ensName: String): RpcResponse<OffchainResolver> {
        return getResolverAddress(ensName).map { OffchainResolver(provider, it) }
    }

    /**
     * Get resolver address for [ensName] from [ENSRegistryWithFallback].
     */
    private fun getResolverAddress(ensName: String): RpcResponse<Address> {
        val registryAddr = getRegistryAddress(provider.chainId)
            ?: return RpcResponse.error(Error.RegistryAddrNotExists(provider.chainId))

        val nameHashResponse = NameHash.nameHash(ensName)
        if (nameHashResponse.isError) return nameHashResponse.propagateError()
        val nameHash = nameHashResponse.resultOrThrow()

        if (ensName.isEmpty()) {
            return RpcResponse.error(Error.UnknownResolver)
        }

        val registryContract = ENSRegistryWithFallback(provider, registryAddr)
        val address = registryContract.resolver(Bytes(nameHash))
            .call(BlockId.LATEST)
            .map {
                if (it.isError) {
                    return@map RpcResponse.error(
                        Error.ResolvingResolver(
                            registryAddr,
                            FastHex.encodeWithPrefix(nameHash),
                        ),
                    )
                }

                if (it.resultOrThrow() == Address.ZERO) {
                    return@map RpcResponse.error(Error.UnknownResolver)
                }

                return@map RpcResponse.result(it)
            }
            .sendAwait()

        if (address.isError) {
            if (address.error is Error.UnknownResolver) {
                return getResolverAddress(getParent(ensName))
            }
            return address.propagateError()
        }

        return address.resultOrThrow()
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
         * Resolver address not found for given nameHash on registry.
         */
        data object UnknownResolver : Error()

        /**
         * Cannot handle OffchainLookup raised inside nested call.
         */
        data object NestedOffchainLookup : Error()

        /**
         * Calldata returned with OffchainLookup error is empty
         */
        data object CcipCalldataEmpty : Error()

        /**
         * CCIP calls reached a maximum limit of [CCIP_LOOKUP_LIMIT]
         */
        data object CcipLookupLimit : Error()

        /**
         * Sender returned with OffchainLookup error is empty
         */
        data object CcipSenderEmpty : Error()
        data object CcipUrlsMissing : Error()

        /**
         * Unknown error during CCIP call execution
         */
        data class CcipCallFailed(val message: String, val cause: Throwable?) : Error() {
            override fun doThrow() {
                throw RuntimeException(message, cause)
            }
        }

        /**
         * One CCIP call got 4xx status code. Execution is stopped.
         */
        data class CcipStatus4xx(val message: String) : Error() {
            override fun doThrow() {
                throw RuntimeException(message, null)
            }
        }

        /**
         * All CCIP calls got 5xx status code.
         *
         * @param errors list of all error response messages
         */
        data class CcipStatus5xx(val errors: List<String>) : Error() {
            override fun doThrow() {
                throw RuntimeException(errors.joinToString(prefix = "<", postfix = ">", separator = "\n"), null)
            }
        }

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
         * Error on resolver address resolving.
         */
        data class ResolvingResolver(
            val registryAddress: Address,
            val nameHash: String,
        ) : Error() {
            override fun doThrow() {
                throw RuntimeException("Error when resolving resolver on registry $registryAddress for nameHash $nameHash.")
            }
        }

        /**
         * Resolver address resolved ens name to an empty address
         */
        data class UnknownEnsName(
            val resolverAddr: Address,
            val nameHash: String,
        ) : Error() {
            override fun doThrow() {
                throw RuntimeException("Resolver: $resolverAddr resolved namehash: $nameHash to an empty address!")
            }
        }

        /**
         * Resolver for ensName exists, but was not able to resolve it.
         */
        data class FailedToResolve(
            val resolverAddr: Address,
            val ensName: String,
        ) :
            Error() {
            override fun doThrow() {
                throw RuntimeException("Failed to resolve ens name: $ensName with resolver $resolverAddr.")
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

        fun Provider.resolveName(ensName: String): CompletableFuture<RpcResponse<Address>> {
            val ensResolver = EnsResolver(this)
            return ensResolver.resolveName(ensName)
        }

        fun getParent(name: String): String {
            val ensName = if (name.isNotEmpty()) name.trim() else ""

            return if (ensName == "." || !ensName.contains(".")) ""
            else ensName.substring(ensName.indexOf(".") + 1)
        }
    }
}
