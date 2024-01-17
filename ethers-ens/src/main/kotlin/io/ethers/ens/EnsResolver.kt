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
import java.math.BigInteger
import java.net.URI
import java.net.URL
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

    /**
     * Resolve ENS name to [Address], as per [specification](https://docs.ens.domains/dapp-developer-guide/resolving-names).
     *
     * Returns [RpcResponse] as [CompletableFuture]. Returns error [RpcResponse.Error].
     *
     * Additional support for:
     * - [Wildcard resolution](https://docs.ens.domains/ens-improvement-proposals/ensip-10-wildcard-resolution)
     * - [Offchain metadata](https://docs.ens.domains/ens-improvement-proposals/ensip-16-offchain-metadata)
     */
    fun resolveAddress(ensName: String): CompletableFuture<RpcResponse<Address>> = CompletableFuture.supplyAsync {
        resolveWithParameters(
            ensName,
            ExtendedResolver.FUNCTION_ADDR,
        ).map { AbiCodec.decode(AbiType.Address, it.value) as Address }
    }

    /**
     * Resolve ENS name to a text record associated with a [key], as per [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-5-text-records).
     *
     * Returns [RpcResponse] as [CompletableFuture]. Returns error [RpcResponse.Error].
     */
    fun resolveText(ensName: String, key: String): CompletableFuture<RpcResponse<String>> =
        CompletableFuture.supplyAsync {
            return@supplyAsync resolveWithParameters(
                ensName,
                ExtendedResolver.FUNCTION_TEXT,
                mutableListOf(key),
                mutableListOf(AbiType.String),
            ).map { AbiCodec.decode(AbiType.String, it.value) as String }
        }

    /**
     * Reverse ENS name resolution, as per [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-3-reverse-resolution).
     *
     * Returns [RpcResponse] as [CompletableFuture]. Returns error [RpcResponse.Error].
     */
    fun resolveEnsName(address: Address): CompletableFuture<RpcResponse<String>> =
        CompletableFuture.supplyAsync {
            val ensName = resolveWithParameters(
                reverseAddressEnsName(address),
                ExtendedResolver.FUNCTION_NAME,
            ).map { AbiCodec.decode(AbiType.String, it.value) as String }

            // To be certain of reverse lookup ENS name, forward resolution must resolve to the original address
            if (!ensName.isError) {
                val trueOwner = resolveAddress(ensName.resultOrThrow()).get()
                if (!trueOwner.isError && trueOwner.resultOrThrow() == address) {
                    return@supplyAsync ensName
                } else {
                    return@supplyAsync RpcResponse.error(
                        Error.IncorrectOwner("True owner (${trueOwner.resultOrThrow()}) of ENS name: $ensName is not the same as provided address: $address"),
                    )
                }
            }

            return@supplyAsync ensName
        }

    /**
     * Resolve avatar of [ensName], as per
     * [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-12-avatar-text-records).
     *
     * Returns [RpcResponse] as [CompletableFuture]. Returns error [RpcResponse.Error].
     */
    fun resolveAvatar(ensName: String): CompletableFuture<RpcResponse<URI>> = CompletableFuture.supplyAsync {
        val uriRes = getAvatarUri(ensName)
        if (uriRes.isError) return@supplyAsync uriRes.propagateError()

        // Get owner of ens name for NFT ownership validation
        val ensOwnerRes = resolveAddress(ensName).get()
        if (ensOwnerRes.isError) return@supplyAsync ensOwnerRes.propagateError()

        return@supplyAsync matchAvatarUri(uriRes.resultOrThrow(), ensOwnerRes.resultOrThrow())
    }

    /**
     * Resolve avatar of [address], as per
     * [specification](https://docs.ens.domains/ens-improvement-proposals/ensip-12-avatar-text-records).
     *
     * Returns [RpcResponse] as [CompletableFuture]. Returns error [RpcResponse.Error].
     */
    fun resolveAvatar(address: Address): CompletableFuture<RpcResponse<URI>> = CompletableFuture.supplyAsync {
        val uriRes = getAvatarUri(address)
        if (uriRes.isError) return@supplyAsync uriRes.propagateError()

        return@supplyAsync matchAvatarUri(uriRes.resultOrThrow(), address)
    }

    /**
     * Resolves [ensName] with provided [abiFunction] (eg. addr(bytes32), text(bytes32,string), name(bytes32), ...)
     * and corresponding parameters and parameter types.
     */
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

        val nameHash = runCatching { NameHash.nameHash(ensName) }
            .getOrElse { return RpcResponse.error(Error.Normalisation(Exception(it))) }

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
            val resolveLookupRevert = resolveResult.error?.asTypeOrNull<ExtendedResolver.OffchainLookup>()

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
                    it.isError -> RpcResponse.error(Error.FailedToResolve("Failed to resolve ens name: $ensName with resolver ${resolver.address}."))
                    // TODO - handle differently
                    abiFunction.selector.contentEquals(ExtendedResolver.FUNCTION_ADDR.selector) &&
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
     * Get [ExtendedResolver] for [ensName] using [EnsRegistry] of current chain.
     */
    private fun getResolver(ensName: String): RpcResponse<ExtendedResolver> {
        return getResolverAddress(ensName).map { ExtendedResolver(provider, it) }
    }

    /**
     * Get resolver address for [ensName] from [EnsRegistry].
     */
    private fun getResolverAddress(ensName: String): RpcResponse<Address> {
        val nameHash: ByteArray = runCatching { NameHash.nameHash(ensName) }
            .getOrElse { return RpcResponse.error(Error.Normalisation(Exception(it))) }
            .also { if (ensName.isEmpty()) return RpcResponse.error(Error.UnknownResolver) }

        val registryContract = EnsRegistry(provider, registryAddress)
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
     * If result of resolver.resolve() call is [ExtendedResolver.OffchainLookup] error, try to resolve the name using
     * [ERC-3668: CCIP offchain data retrieval](https://eips.ethereum.org/EIPS/eip-3668)
     */
    private fun resolveOffchain(
        revert: ExtendedResolver.OffchainLookup,
        resolver: ExtendedResolver,
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
        val callbackLookupRevert = callbackResult.error?.asTypeOrNull<ExtendedResolver.OffchainLookup>()
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
     * @param urls urls parameter of Offchain revert [ExtendedResolver.OffchainLookup]
     * @param sender sender parameter of [ExtendedResolver.OffchainLookup] - replacing {sender} RPC call parameter
     * @param calldata calldata parameter of [ExtendedResolver.OffchainLookup] - replacing {data} RPC call parameter
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
     * Converts avatar URI to URL.
     *
     * If URI schema is:
     * - https or data, return unchanged
     * - ipfs, converts IPFS into HTTPS url
     * - eip155, resolves NFT avatar
     */
    private fun matchAvatarUri(avatarUri: URI, ensOwner: Address): RpcResponse<URI> {
        return when (val scheme = avatarUri.scheme) {
            "https", "data" -> RpcResponse.result(avatarUri)
            "ipfs" -> joinWithIPFSGateway(avatarUri)
            "eip155" -> {
                val token = getAvatarNFT(avatarUri, ensOwner)
                if (token.isError) return token.propagateError()

                val imageUriRes = resolveNftMetadata(token.resultOrThrow())

                if (imageUriRes.isError) {
                    return imageUriRes.propagateError()
                }

                val imageUri = imageUriRes.resultOrThrow()
                if (imageUri.scheme == "ipfs") {
                    val httpsImageUrlRes = joinWithIPFSGateway(imageUriRes.resultOrThrow())
                    if (httpsImageUrlRes.isError) return httpsImageUrlRes.propagateError()
                    return httpsImageUrlRes
                }
                return imageUriRes
            }

            else -> RpcResponse.error(Error.UnsupportedScheme(scheme))
        }
    }

    /**
     * Retrieves [AvatarNFT] from [avatarUri] and returns it as [RpcResponse]. Performs NFT ownership validation.
     */
    private fun getAvatarNFT(avatarUri: URI, ensOwner: Address): RpcResponse<AvatarNFT> {
        val parseResult = AvatarNFT.parse(avatarUri)
        if (parseResult.isError) return parseResult.propagateError()
        val nftToken = parseResult.resultOrThrow()

        // validate NFT ownership
        when (nftToken.nftType) {
            AvatarNFTType.ERC721 -> {
                val nft = ERC721(provider, nftToken.nftAddr)
                val nftOwnerRes = nft.ownerOf(nftToken.tokenId).call(BlockId.LATEST).sendAwait()

                if (nftOwnerRes.isError) {
                    return RpcResponse.error(
                        Error.AvatarParsing(
                            "Error when retrieving owner of nft ${nftToken.nftAddr} (token id: ${nftToken.tokenId})",
                            null,
                        ),
                    )
                }

                val nftOwner = nftOwnerRes.resultOrThrow()
                if (nftOwner != ensOwner) {
                    return RpcResponse.error(
                        Error.IncorrectOwner(
                            "ENS name owner: $ensOwner does not match NFT owner: $nftOwner",
                        ),
                    )
                }
            }

            AvatarNFTType.ERC1155 -> {
                val nft = ERC1155(provider, nftToken.nftAddr)
                val balanceRes = nft.balanceOf(ensOwner, nftToken.tokenId).call(BlockId.LATEST).sendAwait()

                if (balanceRes.isError) {
                    return RpcResponse.error(
                        Error.AvatarParsing(
                            "Error when retrieving balance of nft ${nftToken.nftAddr} (token id: ${nftToken.tokenId}, owner: $ensOwner)",
                            null,
                        ),
                    )
                } else if (balanceRes.resultOrThrow().compareTo(BigInteger.ZERO) == 0) {
                    return RpcResponse.error(
                        Error.IncorrectOwner(
                            "ENS owner has 0 balance of token: ${nftToken.tokenId} for nft: ${nftToken.nftAddr}",
                        ),
                    )
                }
            }
        }

        return RpcResponse.result(nftToken)
    }

    /**
     * Retrieves metadata URL from NFT, executes it and retrieves "image" parameter from result.
     *
     * Metadata URL [example](https://ipfs.io/ipfs/QmYTuHaoY1winNAxmf7JmCmSrkChuMAAnqgSuJBTiWZe9f):
     * ipfs://ipfs/QmYTuHaoY1winNAxmf7JmCmSrkChuMAAnqgSuJBTiWZe9f
     */
    private fun resolveNftMetadata(token: AvatarNFT): RpcResponse<URI> {
        val metadataUrlRes = when (token.nftType) {
            AvatarNFTType.ERC721 ->
                ERC721(provider, token.nftAddr).tokenURI(token.tokenId).call(BlockId.LATEST).sendAwait()

            AvatarNFTType.ERC1155 ->
                ERC1155(provider, token.nftAddr).uri(token.tokenId).call(BlockId.LATEST).sendAwait()
        }

        if (metadataUrlRes.isError) return metadataUrlRes.propagateError()
        val metadataUriStr = metadataUrlRes.resultOrThrow()

        if (token.nftType == AvatarNFTType.ERC1155) {
            // Replace {id} with token id, zero padded to 64 hex characters
            metadataUriStr.replace(
                "{id}",
                FastHex.encodeWithoutPrefix(AbiCodec.encode(AbiType.UInt(256), token.tokenId)),
            )
        }

        val metadataUri = runCatching {
            val uri = URI(metadataUriStr)
            if (uri.scheme == "ipfs") joinWithIPFSGateway(uri).resultOrThrow()
            else uri
        }
            .getOrElse {
                return RpcResponse.error(
                    Error.AvatarParsing("Error on parsing NFT metadata URL: $metadataUriStr", it),
                )
            }

        // Execute metadataUri request and extract "image" attribute
        val request = Request.Builder().url(metadataUri.toURL()).build()
        val response = client.newCall(request).execute()

        return if (response.isSuccessful) {
            val responseBody = response.body
                ?: return RpcResponse.error(Error.AvatarParsing("Response body is null (url: $metadataUri)", null))

            val metadataDTO = runCatching {
                Jackson.MAPPER.readValue(
                    responseBody.byteStream(),
                    MetadataDTO::class.java,
                )
            }.getOrElse { return RpcResponse.error(Error.AvatarParsing("Failed to parse response body", it)) }

            runCatching { RpcResponse.result(URI(metadataDTO.image)) }
                .getOrElse {
                    RpcResponse.error(
                        Error.AvatarParsing("Failed to convert metadata image: ${metadataDTO.image} to URI", it),
                    )
                }
        } else {
            RpcResponse.error(
                Error.AvatarParsing(
                    "Error on executing NFT metadata URL: $metadataUri for token: ${token.tokenId} of " +
                        "NFT: ${token.nftAddr} (${response.message})",
                    null,
                ),
            )
        }
    }

    /**
     * Get avatar URI text record from [ensName].
     */
    private fun getAvatarUri(ensName: String): RpcResponse<URI> {
        val uriRes = resolveText(ensName, "avatar").get()
        if (uriRes.isError) {
            return uriRes.propagateError()
        } else if (uriRes.resultOrThrow().isEmpty()) {
            return RpcResponse.error(Error.FailedToResolve("Failed to resolve avatar of ens name: $ensName"))
        }

        return runCatching { uriRes.map { URI(it) } }
            .getOrElse {
                RpcResponse.error(Error.AvatarParsing("Error on parsing URI: ${uriRes.resultOrThrow()}", it))
            }
    }

    /**
     * Get avatar URI text record from [address].
     */
    private fun getAvatarUri(address: Address): RpcResponse<URI> {
        // Build reverse address ENS
        val reverseAddr = reverseAddressEnsName(address)

        // Get resolver for reverse address ENS
        val resolverResponse = getResolver(reverseAddr)
        if (resolverResponse.isError) return resolverResponse.propagateError()
        val reverseResolver = resolverResponse.resultOrThrow()

        // Try to get avatar via text() for reverse address ENS
        val uriRes = reverseResolver.text(Bytes(NameHash.nameHash(reverseAddr)), "avatar")
            .call(BlockId.LATEST)
            .sendAwait()

        if (uriRes.isError || uriRes.resultOrThrow().isEmpty()) {
            // If text() is unsuccessful, reverse resolve address to ENS name, validate its ownership and forward resolve to avatar
            val ensNameRes = reverseResolver.name(Bytes(NameHash.nameHash(reverseAddr)))
                .call(BlockId.LATEST)
                .sendAwait()

            if (ensNameRes.isError) return ensNameRes.propagateError()
            val ensName = ensNameRes.resultOrThrow()

            // Validate ENS name by "resolving the returned name and calling addr on the resolver, checking it matches the original Ethereum address"
            val ensResolvesTo = resolveAddress(ensName).get()
            if (ensResolvesTo.isError) {
                return ensResolvesTo.propagateError()
            } else if (ensResolvesTo.resultOrThrow() != address) {
                return RpcResponse.error(
                    Error.IncorrectOwner("ENS name: $ensName resolves to: ${ensResolvesTo.resultOrThrow()} which is not equal to original address: $address"),
                )
            }

            // Forward resolve avatar
            return getAvatarUri(ensName)
        }

        return runCatching { uriRes.map { URI(it) } }
            .getOrElse {
                RpcResponse.error(Error.AvatarParsing("Error on parsing URI: ${uriRes.resultOrThrow()}", it))
            }
    }

    /**
     * Build ENS name used for reverse address resolution: <address without prefix>.addr.reverse
     */
    private fun reverseAddressEnsName(address: Address): String {
        return "${address.toString().lowercase().substring(2)}.$ENS_DOMAIN_REVERSE_REGISTER"
    }

    /**
     * Convert IPFS native URL into HTTPS using [IPFS_GATEWAY], as per
     * [specification](https://docs-ipfs-tech.ipns.dweb.link/how-to/address-ipfs-on-web/#ipfs-addressing-in-brief).
     */
    private fun joinWithIPFSGateway(uri: URI): RpcResponse<URI> {
        val path = uri.toString().removePrefix("ipfs://").removePrefix("ipfs/")
        return RpcResponse.result(URL(URL(IPFS_GATEWAY), path).toURI())
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
        data class Normalisation(val cause: Throwable?) : Error() {
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
        data class FailedToResolve(val message: String) :
            Error() {
            override fun doThrow() {
                throw RuntimeException(message)
            }
        }

        // Reverse lookup specific errors
        /**
         * Reverse lookup ENS name does not resolve to original address.
         * The owner of ENS name is incorrect
         */
        data class IncorrectOwner(val message: String) : Error() {
            override fun doThrow() {
                throw RuntimeException(message)
            }
        }

        // Avatar specific errors
        /**
         * Avatar URI scheme is not supported
         */
        data class UnsupportedScheme(val scheme: String) : Error() {
            override fun doThrow() {
                throw RuntimeException("Avatar URI scheme: $scheme is not supported")
            }
        }

        data class AvatarParsing(val message: String, val cause: Throwable?) : Error() {
            override fun doThrow() {
                throw RuntimeException(message, cause)
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
        private const val ENS_DOMAIN_REVERSE_REGISTER = "addr.reverse"
        private val IPFS_GATEWAY = "https://ipfs.io/ipfs/"

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
        fun Provider.resolveAddress(ensName: String): CompletableFuture<RpcResponse<Address>> {
            val ensResolver = EnsResolver(this)
            return ensResolver.resolveAddress(ensName)
        }

        fun Provider.resolveText(ensName: String, key: String): CompletableFuture<RpcResponse<String>> {
            val ensResolver = EnsResolver(this)
            return ensResolver.resolveText(ensName, key)
        }

        fun Provider.resolveEnsName(address: Address): CompletableFuture<RpcResponse<String>> {
            val ensResolver = EnsResolver(this)
            return ensResolver.resolveEnsName(address)
        }

        private fun getParent(name: String): String {
            val ensName = if (name.isNotEmpty()) name.trim() else ""

            return if (ensName == "." || !ensName.contains(".")) ""
            else ensName.substring(ensName.indexOf(".") + 1)
        }
    }
}

private data class EnsGatewayRequestDTO(val data: Bytes, val sender: String)
private data class EnsGatewayResponseDTO(val data: Bytes)
