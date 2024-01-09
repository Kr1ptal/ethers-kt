package io.ethers.ens

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.core.FastHex
import io.ethers.core.Jackson
import io.ethers.core.types.Address
import io.ethers.core.types.BlockId
import io.ethers.core.types.Bytes
import io.ethers.core.types.CallRequest
import io.ethers.logger.err
import io.ethers.logger.getLogger
import io.ethers.logger.wrn
import io.ethers.providers.Provider
import io.ethers.providers.types.RpcResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.CompletableFuture

class EnsResolver @JvmOverloads constructor(
    private val provider: Provider,
    private val registryAddress: Address,
    private val ccipLookupLimit: Int = 4,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val LOG = getLogger()

    @JvmOverloads
    constructor(
        provider: Provider,
        ccipLookupLimit: Int = 4,
        client: OkHttpClient = OkHttpClient(),
    ) : this(
        provider,
        getRegistryAddressOrThrow(provider.chainId),
        ccipLookupLimit,
        client,
    )

    private data class EnsGatewayRequestDTO(val data: Bytes, val sender: String)
    private data class EnsGatewayResponseDTO(val data: Bytes)

    /**
     * Resolve ENS name to [Address], as per [specification](https://docs.ens.domains/dapp-developer-guide/resolving-names).
     *
     * On error return [RpcResponse] as error.
     *
     * Additional support for:
     * - [Wildcard resolution](https://docs.ens.domains/ens-improvement-proposals/ensip-10-wildcard-resolution)
     * - [Offchain metadata](https://docs.ens.domains/ens-improvement-proposals/ensip-16-offchain-metadata)
     */
    fun resolveName(ensName: String): CompletableFuture<RpcResponse<Address>> = CompletableFuture.supplyAsync {
        resolveWithParameters(
            ensName,
            OffchainResolver.FUNCTION_ADDR,
        ).map { AbiCodec.decode(AbiType.Address, it.value) as Address }
    }

    /**
     * Resolve ENS name to a text record associated with a [key], as per [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-5-text-records).
     *
     * On error return [RpcResponse] as error.
     */
    fun resolveText(ensName: String, key: String): CompletableFuture<RpcResponse<String>> =
        CompletableFuture.supplyAsync {
            return@supplyAsync resolveWithParameters(
                ensName,
                PublicResolver.FUNCTION_TEXT,
                mutableListOf(key),
                mutableListOf(AbiType.String),
            ).map { AbiCodec.decode(AbiType.String, it.value) as String }
        }

    /**
     * Reverse ENS name resolution, as per [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-3-reverse-resolution).
     *
     * On error return [RpcResponse] as error.
     */
    fun reverseLookup(address: Address): CompletableFuture<RpcResponse<String>> =
        CompletableFuture.supplyAsync {
            return@supplyAsync resolveWithParameters(
                "${address.toString().substring(2)}.$ENS_DOMAIN_REVERSE_REGISTER",
                PublicResolver.FUNCTION_NAME,
            ).map { AbiCodec.decode(AbiType.String, it.value) as String }
        }

    private fun resolveWithParameters(
        ensName: String,
        abiFunction: AbiFunction,
        parameters: MutableList<Any> = mutableListOf(),
        paramTypes: MutableList<AbiType> = mutableListOf(),
    ): RpcResponse<Bytes> {
        // Check that ens name is valid
        if (ensName.isBlank() || (ensName.trim().length == 1 && ensName.contains("."))) {
            return RpcResponse.error(Error.EnsNameInvalid)
        }

        val nameHash: ByteArray
        try {
            nameHash = NameHash.nameHash(ensName)
        } catch (e: Exception) {
            return RpcResponse.error(Error.Normalisation(e))
        }

        val resolverResponse = getResolver(ensName)
        if (resolverResponse.isError) return resolverResponse.propagateError()

        // Unwrap resolver from RpcResponse and call its addr() function.
        // If RpcResponse is an error, map it to error FailedToResolve.
        val resolver = resolverResponse.resultOrThrow()

        val supportsWildcard = resolver.supportsInterface(ENSIP_10_INTERFACE_ID).call(BlockId.LATEST).sendAwait()

        // add nodehash as first parameter, because it is present in all resolutions
        parameters.add(0, Bytes(nameHash))
        paramTypes.add(0, AbiType.FixedBytes(32))
        if (!supportsWildcard.isError && supportsWildcard.resultOrThrow()) {
            val dnsEncoded = NameHash.dnsEncode(ensName)
            val encodedParams = abiFunction.encodeCall(parameters.toTypedArray())

            val resolveResult = resolver.resolve(dnsEncoded, encodedParams)
                .call(BlockId.LATEST)
                .sendAwait()

            // try to decode OffchainLookup error
            val resolveLookupRevert = resolveResult.error?.asTypeOrNull<OffchainResolver.OffchainLookup>()

            return if (resolveLookupRevert == null) {
                // result is resolved ens name
                resolveResult
            } else {
                // result is OffchainLookup error
                resolveOffchain(
                    resolveLookupRevert,
                    resolver,
                    ccipLookupLimit,
                )
            }
        } else {
            // Simple ENS name resolution: resolve by calling corresponding resolving function
            // (eg. addr(bytes32), text(bytes32, string)) of resolver directly.

            // Validate that resolver supports abiFunction
            val supportsFunction =
                resolver.supportsInterface(Bytes(abiFunction.selector)).call(BlockId.LATEST).sendAwait()

            if (supportsFunction.isError || !supportsFunction.resultOrThrow()) {
                return RpcResponse.error(
                    Error.UnsupportedSelector(
                        resolver.address,
                        FastHex.encodeWithPrefix(abiFunction.selector),
                    ),
                )
            }

            // create callback for corresponding function selector
            val callbackData = AbiCodec.encodeWithPrefix(
                abiFunction.selector,
                paramTypes,
                parameters.toTypedArray(),
            )

            return provider.call(
                CallRequest {
                    to = resolver.address
                    data = Bytes(callbackData)
                },
                BlockId.LATEST,
            ).map {
                return@map when {
                    // Return different errors on empty address and failure to resolve
                    it.isError -> RpcResponse.error(Error.FailedToResolve(resolver.address, ensName))
                    // TODO - handle differently
                    abiFunction.selector.contentEquals(PublicResolver.FUNCTION_ADDR.selector) &&
                        (AbiCodec.decode(AbiType.Address, it.resultOrThrow().value) as Address) == Address.ZERO ->
                        RpcResponse.error(
                            Error.UnknownEnsName(
                                resolver.address,
                                FastHex.encodeWithPrefix(nameHash),
                            ),
                        )

                    else -> it
                }
            }.sendAwait()
        }
    }

    /**
     * If result of resolver.resolve() call is [OffchainResolver.OffchainLookup] error, try to resolve the name using
     * [ERC-3668: CCIP offchain data retrieval](https://eips.ethereum.org/EIPS/eip-3668)
     */
    private fun resolveOffchain(
        revert: OffchainResolver.OffchainLookup,
        resolver: OffchainResolver,
        lookupLimit: Int,
    ): RpcResponse<Bytes> {
        // OffchainLookup.sender has to be resolver address
        if (revert.sender != resolver.address) {
            return RpcResponse.error(Error.NestedOffchainLookup)
        }

        if (revert.callData.isEmpty) return RpcResponse.error(Error.CcipRevertDataInvalid("Calldata is empty!"))

        // get gateway result by trying urls one by one and passing sender and data returned by OffchainLookup error
        val gatewayResult = httpCall(revert.urls, revert.sender, revert.callData)

        if (gatewayResult.isError) return gatewayResult.propagateError()

        // call resolver.callbackFunction(gatewayResult, extraData). If this call is CCIP, repeat the procedure.
        val callbackData = AbiCodec.encodeWithPrefix(
            revert.callbackFunction.value,
            CALLBACK_FUNCTION_PARAM_TYPES,
            arrayOf(gatewayResult.resultOrThrow(), revert.extraData),
        )

        // dynamic bytes
        val callbackResult = provider.call(
            CallRequest {
                to = resolver.address
                data = Bytes(callbackData)
            },
            BlockId.LATEST,
        ).sendAwait()

        // If callbackResult is OffchainLookup error, resolve using recursive CCIP calls
        val callbackLookupRevert = callbackResult.error?.asTypeOrNull<OffchainResolver.OffchainLookup>()
        if (callbackLookupRevert != null) {
            if (lookupLimit <= 0) return RpcResponse.error(Error.CcipLookupLimit)

            return resolveOffchain(callbackLookupRevert, resolver, lookupLimit - 1)
        } else {
            // callbackResult is resolved ENS name. Decode dynamic bytes to address
            val resolvedDecoded = AbiCodec.decode(AbiType.Bytes, callbackResult.resultOrThrow().value) as Bytes
            return RpcResponse.result(resolvedDecoded)
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
        if (urls.isEmpty()) return RpcResponse.error(Error.CcipCallFailed("No urls to resolve ens name!", null))

        for (url in urls) {
            // If url is missing mandatory {sender} parameter, try next url
            val request = buildCcipRequest(url, sender, calldata) ?: continue

            return try {
                val response = client.newCall(request).execute().use { handleCcipResponse(it, url) } ?: continue

                if (response.isError) {
                    return response.propagateError()
                }
                response
            } catch (e: Exception) {
                LOG.err(e) { e.message ?: "" }
                RpcResponse.error(Error.CcipCallFailed("Unknown error", e))
            }
        }

        return RpcResponse.error(Error.CcipCallFailed("All urls are invalid or got server response 5xx", null))
    }

    /**
     * If CCIP [response] from [url]:
     * - is successful, decode it and return [Bytes].
     * - has status code 4xx, return error. Execution is stopped.
     * - has status code 5xx, return null and try another url, if present.
     */
    private fun handleCcipResponse(
        response: Response,
        url: String,
    ): RpcResponse<Bytes>? {
        if (response.isSuccessful) {
            val responseBody = response.body
                ?: return RpcResponse.error(Error.CcipCallFailed("Response body is null (url: $url)", null))

            val gatewayRequestDTO = Jackson.MAPPER.readValue(
                responseBody.byteStream(),
                EnsGatewayResponseDTO::class.java,
            )
            val data = gatewayRequestDTO.data

            return RpcResponse.result(data)
        }

        return if (response.code in 400..499) {
            // 4xx - return an error and stop
            val mes = "Received status code: ${response.code} during CCIP call (url: $url, error: ${response.message})"
            LOG.err { mes }
            RpcResponse.error(Error.CcipCallFailed(mes, null))
        } else {
            // 5xx - server issue, try different url
            LOG.wrn { "500 error during CCIP call: url: $url, error: ${response.message}" }
            null
        }
    }

    /**
     * Builds CCIP-read [okhttp3.Request] from [url], [sender] and [calldata], where [sender] and [calldata]
     * are RPC request parameters.
     *
     * If RPC url contains {data}, the request is GET, otherwise POST.
     *
     * If [url] is missing {sender} parameter, return null.
     */
    private fun buildCcipRequest(url: String, sender: Address, calldata: Bytes): Request? {
        if (!url.contains("{sender}")) return null // skip this url

        // URL expansion
        var href = url.replace("{sender}", sender.toString())

        return if (url.contains("{data}")) {
            href = href.replace("{data}", calldata.toString())
            Request.Builder().url(href).get().build()
        } else {
            val requestDTO = EnsGatewayRequestDTO(calldata, sender.toString())

            Request.Builder().url(href)
                .post(
                    Jackson.MAPPER.writeValueAsString(requestDTO)
                        .toRequestBody(JSON_MEDIA_TYPE),
                )
                .addHeader("Content-Type", "application/json")
                .build()
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
        val nameHash: ByteArray
        try {
            nameHash = NameHash.nameHash(ensName)
        } catch (e: Exception) {
            return RpcResponse.error(Error.Normalisation(e))
        }

        if (ensName.isEmpty()) {
            return RpcResponse.error(Error.UnknownResolver)
        }

        val registryContract = ENSRegistryWithFallback(provider, registryAddress)
        val address = registryContract.resolver(Bytes(nameHash))
            .call(BlockId.LATEST)
            .map {
                return@map when {
                    it.isError -> RpcResponse.error(
                        Error.ResolvingResolver(
                            registryAddress,
                            FastHex.encodeWithPrefix(nameHash),
                        ),
                    )

                    it.resultOrThrow() == Address.ZERO -> RpcResponse.error(Error.UnknownResolver)
                    else -> it
                }
            }
            .sendAwait()

        if (address.isError) {
            if (address.error is Error.UnknownResolver) {
                return getResolverAddress(getParent(ensName))
            }
            return address.propagateError()
        }

        return address
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
         * Error on ens name normalisation attempt.
         */
        data class Normalisation(val cause: Exception) : Error() {
            override fun doThrow() {
                throw RuntimeException("Normalisation failed: $cause")
            }
        }

        // Errors when getting resolver address
        /**
         * Error on getting resolver address from registry
         */
        data class ResolvingResolver(
            val registryAddress: Address,
            val nameHash: String,
        ) : Error() {
            override fun doThrow() {
                throw RuntimeException("Error when getting resolver address from registry> $registryAddress for nameHash: $nameHash.")
            }
        }

        /**
         * Resolver address not found for given nameHash on registry.
         */
        data object UnknownResolver : Error()

        // Errors when resolving ENS name with resolver
        /**
         * Resolver does not support selector
         */
        data class UnsupportedSelector(
            val resolver: Address,
            val selector: String,
        ) : Error() {
            override fun doThrow() {
                throw RuntimeException("Resolver: ")
            }
        }

        /**
         * Resolver address resolved ens name to an empty address.
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

        // CCIP specific errors
        /**
         * Cannot handle OffchainLookup raised inside nested call.
         */
        data object NestedOffchainLookup : Error()

        /**
         * CCIP calls reached a maximum limit.
         */
        data object CcipLookupLimit : Error()

        /**
         * Data returned with OffchainLookup error is invalid.
         */
        data class CcipRevertDataInvalid(val message: String) : Error() {
            override fun doThrow() {
                throw RuntimeException(message)
            }
        }

        /**
         * Unknown error during CCIP call execution.
         */
        data class CcipCallFailed(val message: String, val cause: Throwable?) : Error() {
            override fun doThrow() {
                throw RuntimeException(message, cause)
            }
        }
    }

    companion object {
        private val ENSIP_10_INTERFACE_ID = Bytes("0x9061b923")
        private val CALLBACK_FUNCTION_PARAM_TYPES = listOf(AbiType.Bytes, AbiType.Bytes)
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val ENS_DOMAIN_REVERSE_REGISTER = "addr.reverse"

        private val MAINNET_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val ROPSTEN_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val RINKEBY_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val GOERLI_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")
        private val SEPOLIA_REGISTRY_ADDR = Address("0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e")

        private fun getRegistryAddress(chainId: Long): Address? {
            return when (chainId) {
                1L -> MAINNET_REGISTRY_ADDR
                3L -> ROPSTEN_REGISTRY_ADDR
                4L -> RINKEBY_REGISTRY_ADDR
                5L -> GOERLI_REGISTRY_ADDR
                11155111L -> SEPOLIA_REGISTRY_ADDR
                else -> return null
            }
        }

        private fun getRegistryAddressOrThrow(chainId: Long): Address {
            return getRegistryAddress(chainId)
                ?: throw IllegalArgumentException("No registry address found for chain id: $chainId")
        }

        // TODO: structure differently, new Middleware?
        fun Provider.resolveName(ensName: String): CompletableFuture<RpcResponse<Address>> {
            val ensResolver = EnsResolver(this)
            return ensResolver.resolveName(ensName)
        }

        fun Provider.resolveText(ensName: String, key: String): CompletableFuture<RpcResponse<String>> {
            val ensResolver = EnsResolver(this)
            return ensResolver.resolveText(ensName, key)
        }

        fun Provider.reverseLookup(address: Address): CompletableFuture<RpcResponse<String>> {
            val ensResolver = EnsResolver(this)
            return ensResolver.reverseLookup(address)
        }

        private fun getParent(name: String): String {
            val ensName = if (name.isNotEmpty()) name.trim() else ""

            return if (ensName == "." || !ensName.contains(".")) ""
            else ensName.substring(ensName.indexOf(".") + 1)
        }
    }
}
