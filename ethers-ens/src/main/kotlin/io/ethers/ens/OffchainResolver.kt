package io.ethers.ens

import io.ethers.abi.AbiContract
import io.ethers.abi.AbiEvent
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.ContractEvent
import io.ethers.abi.EventFactory
import io.ethers.abi.EventFilter
import io.ethers.abi.call.ReadFunctionCall
import io.ethers.abi.error.CustomContractError
import io.ethers.abi.error.CustomErrorFactory
import io.ethers.abi.error.CustomErrorFactoryResolver
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Log
import io.ethers.providers.middleware.Middleware
import java.math.BigInteger
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

public class OffchainResolver(
    provider: Middleware,
    address: Address,
) : AbiContract(provider, address) {
    public fun makeSignatureHash(
        target: Address,
        expires: BigInteger,
        request: Bytes,
        result: Bytes,
    ): ReadFunctionCall<Bytes> = ReadFunctionCall(
        provider,
        address,
        FUNCTION_MAKE_SIGNATURE_HASH.encodeCall(arrayOf(target, expires, request, result)),
    ) {
        val data = FUNCTION_MAKE_SIGNATURE_HASH.decodeResponse(it)
        data[0] as io.ethers.core.types.Bytes
    }

    public fun resolve(name: Bytes, `data`: Bytes): ReadFunctionCall<Bytes> =
        ReadFunctionCall(provider, address, FUNCTION_RESOLVE.encodeCall(arrayOf(name, `data`))) {
            val data = FUNCTION_RESOLVE.decodeResponse(it)
            data[0] as io.ethers.core.types.Bytes
        }

    public fun resolveWithProof(response: Bytes, extraData: Bytes): ReadFunctionCall<Bytes> =
        ReadFunctionCall(
            provider,
            address,
            FUNCTION_RESOLVE_WITH_PROOF.encodeCall(arrayOf(response, extraData)),
        ) {
            val data = FUNCTION_RESOLVE_WITH_PROOF.decodeResponse(it)
            data[0] as io.ethers.core.types.Bytes
        }

    public fun signers(arg0: Address): ReadFunctionCall<Boolean> = ReadFunctionCall(
        provider,
        address,
        FUNCTION_SIGNERS.encodeCall(arrayOf(arg0)),
    ) {
        val data = FUNCTION_SIGNERS.decodeResponse(it)
        data[0] as kotlin.Boolean
    }

    public fun supportsInterface(interfaceID: Bytes): ReadFunctionCall<Boolean> =
        ReadFunctionCall(
            provider,
            address,
            FUNCTION_SUPPORTS_INTERFACE.encodeCall(arrayOf(interfaceID)),
        ) {
            val data = FUNCTION_SUPPORTS_INTERFACE.decodeResponse(it)
            data[0] as kotlin.Boolean
        }

    public fun url(): ReadFunctionCall<String> = ReadFunctionCall(
        provider,
        address,
        FUNCTION_URL.encodeCall(emptyArray()),
    ) {
        val data = FUNCTION_URL.decodeResponse(it)
        data[0] as kotlin.String
    }

    public fun addr(node: Bytes): ReadFunctionCall<Address> = ReadFunctionCall(
        provider,
        address,
        FUNCTION_ADDR.encodeCall(arrayOf(node)),
    ) {
        val data = FUNCTION_ADDR.decodeResponse(it)
        data[0] as io.ethers.core.types.Address
    }

    public sealed class Error : CustomContractError()

    public sealed class Event : ContractEvent

    public data class OffchainLookup(
        public val sender: Address,
        public val urls: Array<String>,
        public val callData: Bytes,
        public val callbackFunction: Bytes,
        public val extraData: Bytes,
    ) : Error() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as OffchainLookup
            if (sender != other.sender) return false
            if (!urls.contentEquals(other.urls)) return false
            if (callData != other.callData) return false
            if (callbackFunction != other.callbackFunction) return false
            if (extraData != other.extraData) return false
            return true
        }

        override fun hashCode(): Int {
            var result = sender.hashCode()
            result = 31 * result + urls.contentHashCode()
            result = 31 * result + callData.hashCode()
            result = 31 * result + callbackFunction.hashCode()
            result = 31 * result + extraData.hashCode()
            return result
        }

        public companion object : CustomErrorFactory<OffchainLookup> {
            @JvmStatic
            override val abi: AbiFunction = AbiFunction(
                "OffchainLookup",
                listOf(
                    AbiType.Address,
                    AbiType.Array(AbiType.String),
                    AbiType.Bytes,
                    AbiType.FixedBytes(4),
                    AbiType.Bytes,
                ),
                emptyList(),
            )

            @JvmStatic
            override fun decode(`data`: Array<Any>): OffchainLookup = OffchainLookup(
                data[0] as
                    Address,
                data[1] as Array<String>,
                data[2] as
                    Bytes,
                data[3] as Bytes,
                data[4] as
                    Bytes,
            )
        }
    }

    public data class NewSigners(
        public val signers: Array<Address>,
        override val log: Log,
    ) : Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as NewSigners
            if (!signers.contentEquals(other.signers)) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = signers.contentHashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<NewSigners> {
            @JvmStatic
            override val abi: AbiEvent = AbiEvent(
                "NewSigners",
                listOf(AbiEvent.Token(AbiType.Array(AbiType.Address), false)),
                false,
            )

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<NewSigners> =
                EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): NewSigners? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: Array<Any>): NewSigners = NewSigners(
                data[0] as
                    Array<Address>,
                log,
            )
        }
    }

    public companion object {
        @JvmField
        public val ERRORS: Array<CustomErrorFactory<out Error>> = arrayOf(OffchainLookup)

        init {
            CustomErrorFactoryResolver.addFactories(ERRORS)
        }

        @JvmField
        public val EVENTS: Array<EventFactory<out Event>> = arrayOf(NewSigners)

        @JvmField
        public val FUNCTION_MAKE_SIGNATURE_HASH: AbiFunction = AbiFunction(
            "makeSignatureHash",
            listOf(AbiType.Address, AbiType.UInt(64), AbiType.Bytes, AbiType.Bytes),
            listOf(AbiType.FixedBytes(32)),
        )

        @JvmField
        public val FUNCTION_SIGNERS: AbiFunction = AbiFunction(
            "signers",
            listOf(AbiType.Address),
            listOf(AbiType.Bool),
        )

        @JvmField
        public val FUNCTION_URL: AbiFunction = AbiFunction("url", listOf(), listOf(AbiType.String))

        @JvmField
        public val FUNCTION_RESOLVE: AbiFunction = AbiFunction(
            "resolve",
            listOf(
                AbiType.Bytes,
                AbiType.Bytes,
            ),
            listOf(AbiType.Bytes),
        )

        @JvmField
        public val FUNCTION_RESOLVE_WITH_PROOF: AbiFunction = AbiFunction(
            "resolveWithProof",
            listOf(AbiType.Bytes, AbiType.Bytes),
            listOf(AbiType.Bytes),
        )

        @JvmField
        public val FUNCTION_SUPPORTS_INTERFACE: AbiFunction = AbiFunction(
            "supportsInterface",
            listOf(AbiType.FixedBytes(4)),
            listOf(AbiType.Bool),
        )

        @JvmField
        public val FUNCTION_ADDR: AbiFunction = AbiFunction(
            "addr",
            listOf(AbiType.FixedBytes(32)),
            listOf(AbiType.Address),
        )

        @JvmStatic
        public fun decodeError(error: Bytes): Error? {
            for (err in ERRORS) {
                return err.decode(error) ?: continue
            }
            return null
        }

        @JvmStatic
        public fun decodeEvent(log: Log): Event? {
            for (event in EVENTS) {
                return event.decode(log) ?: continue
            }
            return null
        }
    }
}
