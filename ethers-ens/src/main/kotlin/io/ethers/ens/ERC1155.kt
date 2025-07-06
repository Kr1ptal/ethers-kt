@file:Suppress("UNCHECKED_CAST", "FunctionName", "PropertyName", "RedundantVisibilityModifier", "RemoveRedundantQualifierName", "LocalVariableName", "unused")

package io.ethers.ens

import io.ethers.abi.AbiContract
import io.ethers.abi.AbiEvent
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.abi.ContractEvent
import io.ethers.abi.EventFactory
import io.ethers.abi.EventFilter
import io.ethers.abi.call.FunctionCall
import io.ethers.abi.call.ReadFunctionCall
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Log
import io.ethers.providers.middleware.Middleware
import java.math.BigInteger

public class ERC1155(
    provider: Middleware,
    address: Address,
) : AbiContract(provider, address) {
    /**
     * Call contract function
     *
     * Selector: `0x00fdd58e`
     *
     * Signature:
     * ```solidity
     *     function balanceOf(address _owner, uint256 _id) external returns (uint256);
     * ```
     */
    public fun balanceOf(_owner: Address, _id: BigInteger): ReadFunctionCall<BigInteger> = ReadFunctionCall(this.provider, this.address, FUNCTION_BALANCE_OF.encodeCall(listOf(_owner, _id))) {
        val data = FUNCTION_BALANCE_OF.decodeResponse(it)
        data[0] as java.math.BigInteger
    }

    /**
     * Call contract function
     *
     * Selector: `0x4e1273f4`
     *
     * Signature:
     * ```solidity
     *     function balanceOfBatch(address[] _owners, uint256[] _ids) external returns (uint256[]);
     * ```
     */
    public fun balanceOfBatch(_owners: List<Address>, _ids: List<BigInteger>): ReadFunctionCall<List<BigInteger>> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_BALANCE_OF_BATCH.encodeCall(listOf<Any>(_owners, _ids)),
    ) {
        val data = FUNCTION_BALANCE_OF_BATCH.decodeResponse(it)
        data[0] as List<java.math.BigInteger>
    }

    /**
     * Call contract function
     *
     * Selector: `0xe985e9c5`
     *
     * Signature:
     * ```solidity
     *     function isApprovedForAll(address _owner, address _operator) external returns (bool);
     * ```
     */
    public fun isApprovedForAll(_owner: Address, _operator: Address): ReadFunctionCall<Boolean> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_IS_APPROVED_FOR_ALL.encodeCall(listOf<Any>(_owner, _operator)),
    ) {
        val data = FUNCTION_IS_APPROVED_FOR_ALL.decodeResponse(it)
        data[0] as kotlin.Boolean
    }

    /**
     * Call contract function
     *
     * Selector: `0xbc197c81`
     *
     * Signature:
     * ```solidity
     *     function onERC1155BatchReceived(address _operator, address _from, uint256[] _ids, uint256[] _values, bytes _data) external returns (bytes4);
     * ```
     */
    public fun onERC1155BatchReceived(
        _operator: Address,
        _from: Address,
        _ids: List<BigInteger>,
        _values: List<BigInteger>,
        _data: Bytes,
    ): FunctionCall<Bytes> = FunctionCall(
        this.provider,
        this.address,
        FUNCTION_ON_ERC1155_BATCH_RECEIVED.encodeCall(listOf(_operator, _from, _ids, _values, _data)),
    ) {
        val data = FUNCTION_ON_ERC1155_BATCH_RECEIVED.decodeResponse(it)
        data[0] as io.ethers.core.types.Bytes
    }

    /**
     * Call contract function
     *
     * Selector: `0xf23a6e61`
     *
     * Signature:
     * ```solidity
     *     function onERC1155Received(address _operator, address _from, uint256 _id, uint256 _value, bytes _data) external returns (bytes4);
     * ```
     */
    public fun onERC1155Received(
        _operator: Address,
        _from: Address,
        _id: BigInteger,
        _value: BigInteger,
        _data: Bytes,
    ): FunctionCall<Bytes> = FunctionCall(
        this.provider,
        this.address,
        FUNCTION_ON_ERC1155_RECEIVED.encodeCall(listOf(_operator, _from, _id, _value, _data)),
    ) {
        val data = FUNCTION_ON_ERC1155_RECEIVED.decodeResponse(it)
        data[0] as io.ethers.core.types.Bytes
    }

    /**
     * Call contract function
     *
     * Selector: `0x2eb2c2d6`
     *
     * Signature:
     * ```solidity
     *     function safeBatchTransferFrom(address _from, address _to, uint256[] _ids, uint256[] _values, bytes _data) external;
     * ```
     */
    public fun safeBatchTransferFrom(
        _from: Address,
        _to: Address,
        _ids: List<BigInteger>,
        _values: List<BigInteger>,
        _data: Bytes,
    ): FunctionCall<Unit> = FunctionCall(
        this.provider,
        this.address,
        FUNCTION_SAFE_BATCH_TRANSFER_FROM.encodeCall(listOf(_from, _to, _ids, _values, _data)),
    ) {
    }

    /**
     * Call contract function
     *
     * Selector: `0xf242432a`
     *
     * Signature:
     * ```solidity
     *     function safeTransferFrom(address _from, address _to, uint256 _id, uint256 _value, bytes _data) external;
     * ```
     */
    public fun safeTransferFrom(
        _from: Address,
        _to: Address,
        _id: BigInteger,
        _value: BigInteger,
        _data: Bytes,
    ): FunctionCall<Unit> = FunctionCall(
        this.provider,
        this.address,
        FUNCTION_SAFE_TRANSFER_FROM.encodeCall(listOf(_from, _to, _id, _value, _data)),
    ) {
    }

    /**
     * Call contract function
     *
     * Selector: `0xa22cb465`
     *
     * Signature:
     * ```solidity
     *     function setApprovalForAll(address _operator, bool _approved) external;
     * ```
     */
    public fun setApprovalForAll(_operator: Address, _approved: Boolean): FunctionCall<Unit> = FunctionCall(
        this.provider,
        this.address,
        FUNCTION_SET_APPROVAL_FOR_ALL.encodeCall(listOf(_operator, _approved)),
    ) {
    }

    /**
     * Call contract function
     *
     * Selector: `0x01ffc9a7`
     *
     * Signature:
     * ```solidity
     *     function supportsInterface(bytes4 interfaceID) external returns (bool);
     * ```
     */
    public fun supportsInterface(interfaceID: Bytes): ReadFunctionCall<Boolean> = ReadFunctionCall(
        this.provider,
        this.address,
        FUNCTION_SUPPORTS_INTERFACE.encodeCall(listOf<Any>(interfaceID)),
    ) {
        val data = FUNCTION_SUPPORTS_INTERFACE.decodeResponse(it)
        data[0] as kotlin.Boolean
    }

    /**
     * Call contract function
     *
     * Selector: `0x0e89341c`
     *
     * Signature:
     * ```solidity
     *     function uri(uint256 _id) external returns (string);
     * ```
     */
    public fun uri(_id: BigInteger): ReadFunctionCall<String> = ReadFunctionCall(this.provider, this.address, FUNCTION_URI.encodeCall(listOf<Any>(_id))) {
        val data = FUNCTION_URI.decodeResponse(it)
        data[0] as kotlin.String
    }

    public sealed class Event : ContractEvent

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event ApprovalForAll(address indexed _owner, address indexed _operator, bool _approved);
     * ```
     */
    public data class ApprovalForAll(
        public val _owner: Address,
        public val _operator: Address,
        public val _approved: Boolean,
        override val log: Log,
    ) : ERC1155.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ApprovalForAll
            if (_owner != other._owner) return false
            if (_operator != other._operator) return false
            if (_approved != other._approved) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = _owner.hashCode()
            result = 31 * result + _operator.hashCode()
            result = 31 * result + _approved.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<ApprovalForAll> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent(
                    "ApprovalForAll",
                    listOf(
                        AbiEvent.Token(AbiType.Address, true),
                        AbiEvent.Token(AbiType.Address, true),
                        AbiEvent.Token(AbiType.Bool, false),
                    ),
                    false,
                )

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<ApprovalForAll> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): ApprovalForAll? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): ApprovalForAll = ApprovalForAll(
                data[0] as io.ethers.core.types.Address,
                data[1] as io.ethers.core.types.Address,
                data[2] as kotlin.Boolean,
                log,
            )
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x4a39dc06d4c0dbc64b70af90fd698a233a518aa5d07e595d983b8c0526c8f7fb`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event TransferBatch(address indexed _operator, address indexed _from, address indexed _to, uint256[] _ids, uint256[] _values);
     * ```
     */
    public data class TransferBatch(
        public val _operator: Address,
        public val _from: Address,
        public val _to: Address,
        public val _ids: List<BigInteger>,
        public val _values: List<BigInteger>,
        override val log: Log,
    ) : ERC1155.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TransferBatch
            if (_operator != other._operator) return false
            if (_from != other._from) return false
            if (_to != other._to) return false
            if (_ids != other._ids) return false
            if (_values != other._values) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = _operator.hashCode()
            result = 31 * result + _from.hashCode()
            result = 31 * result + _to.hashCode()
            result = 31 * result + _ids.hashCode()
            result = 31 * result + _values.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<TransferBatch> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent(
                    "TransferBatch",
                    listOf(
                        AbiEvent.Token(AbiType.Address, true),
                        AbiEvent.Token(AbiType.Address, true),
                        AbiEvent.Token(AbiType.Address, true),
                        AbiEvent.Token(AbiType.Array(AbiType.UInt(256)), false),
                        AbiEvent.Token(AbiType.Array(AbiType.UInt(256)), false),
                    ),
                    false,
                )

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<TransferBatch> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): TransferBatch? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): TransferBatch = TransferBatch(
                data[0] as io.ethers.core.types.Address,
                data[1] as io.ethers.core.types.Address,
                data[2] as io.ethers.core.types.Address,
                data[3] as List<java.math.BigInteger>,
                data[4] as List<java.math.BigInteger>,
                log,
            )
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xc3d58168c5ae7397731d063d5bbf3d657854427343f4c083240f7aacaa2d0f62`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event TransferSingle(address indexed _operator, address indexed _from, address indexed _to, uint256 _id, uint256 _value);
     * ```
     */
    public data class TransferSingle(
        public val _operator: Address,
        public val _from: Address,
        public val _to: Address,
        public val _id: BigInteger,
        public val _value: BigInteger,
        override val log: Log,
    ) : ERC1155.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TransferSingle
            if (_operator != other._operator) return false
            if (_from != other._from) return false
            if (_to != other._to) return false
            if (_id != other._id) return false
            if (_value != other._value) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = _operator.hashCode()
            result = 31 * result + _from.hashCode()
            result = 31 * result + _to.hashCode()
            result = 31 * result + _id.hashCode()
            result = 31 * result + _value.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<TransferSingle> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent(
                    "TransferSingle",
                    listOf(
                        AbiEvent.Token(AbiType.Address, true),
                        AbiEvent.Token(AbiType.Address, true),
                        AbiEvent.Token(AbiType.Address, true),
                        AbiEvent.Token(AbiType.UInt(256), false),
                        AbiEvent.Token(AbiType.UInt(256), false),
                    ),
                    false,
                )

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<TransferSingle> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): TransferSingle? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): TransferSingle = TransferSingle(
                data[0] as io.ethers.core.types.Address,
                data[1] as io.ethers.core.types.Address,
                data[2] as io.ethers.core.types.Address,
                data[3] as java.math.BigInteger,
                data[4] as java.math.BigInteger,
                log,
            )
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x6bb7ff708619ba0610cba295a58592e0451dee2622938c8755667688daf3529b`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event URI(string _value, uint256 indexed _id);
     * ```
     */
    public data class URI(
        public val _value: String,
        public val _id: BigInteger,
        override val log: Log,
    ) : ERC1155.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as URI
            if (_value != other._value) return false
            if (_id != other._id) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = _value.hashCode()
            result = 31 * result + _id.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<URI> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent(
                    "URI",
                    listOf(AbiEvent.Token(AbiType.String, false), AbiEvent.Token(AbiType.UInt(256), true)),
                    false,
                )

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<URI> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): URI? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): URI = URI(data[0] as kotlin.String, data[1] as java.math.BigInteger, log)
        }
    }

    public companion object {
        @JvmField
        public val EVENTS: List<EventFactory<out ERC1155.Event>> =
            listOf(ApprovalForAll, TransferBatch, TransferSingle, URI)

        @JvmField
        public val FUNCTION_ON_ERC1155_RECEIVED: AbiFunction =
            AbiFunction(
                "onERC1155Received",
                listOf(AbiType.Address, AbiType.Address, AbiType.UInt(256), AbiType.UInt(256), AbiType.Bytes),
                listOf(AbiType.FixedBytes(4)),
            )

        @JvmField
        public val FUNCTION_IS_APPROVED_FOR_ALL: AbiFunction =
            AbiFunction("isApprovedForAll", listOf(AbiType.Address, AbiType.Address), listOf(AbiType.Bool))

        @JvmField
        public val FUNCTION_BALANCE_OF: AbiFunction =
            AbiFunction("balanceOf", listOf(AbiType.Address, AbiType.UInt(256)), listOf(AbiType.UInt(256)))

        @JvmField
        public val FUNCTION_SAFE_TRANSFER_FROM: AbiFunction =
            AbiFunction(
                "safeTransferFrom",
                listOf(AbiType.Address, AbiType.Address, AbiType.UInt(256), AbiType.UInt(256), AbiType.Bytes),
                listOf(),
            )

        @JvmField
        public val FUNCTION_BALANCE_OF_BATCH: AbiFunction =
            AbiFunction(
                "balanceOfBatch",
                listOf(AbiType.Array(AbiType.Address), AbiType.Array(AbiType.UInt(256))),
                listOf(AbiType.Array(AbiType.UInt(256))),
            )

        @JvmField
        public val FUNCTION_URI: AbiFunction =
            AbiFunction("uri", listOf(AbiType.UInt(256)), listOf(AbiType.String))

        @JvmField
        public val FUNCTION_ON_ERC1155_BATCH_RECEIVED: AbiFunction =
            AbiFunction(
                "onERC1155BatchReceived",
                listOf(
                    AbiType.Address,
                    AbiType.Address,
                    AbiType.Array(AbiType.UInt(256)),
                    AbiType.Array(AbiType.UInt(256)),
                    AbiType.Bytes,
                ),
                listOf(AbiType.FixedBytes(4)),
            )

        @JvmField
        public val FUNCTION_SAFE_BATCH_TRANSFER_FROM: AbiFunction =
            AbiFunction(
                "safeBatchTransferFrom",
                listOf(
                    AbiType.Address,
                    AbiType.Address,
                    AbiType.Array(AbiType.UInt(256)),
                    AbiType.Array(AbiType.UInt(256)),
                    AbiType.Bytes,
                ),
                listOf(),
            )

        @JvmField
        public val FUNCTION_SET_APPROVAL_FOR_ALL: AbiFunction =
            AbiFunction("setApprovalForAll", listOf(AbiType.Address, AbiType.Bool), listOf())

        @JvmField
        public val FUNCTION_SUPPORTS_INTERFACE: AbiFunction =
            AbiFunction("supportsInterface", listOf(AbiType.FixedBytes(4)), listOf(AbiType.Bool))

        @JvmStatic
        public fun decodeEvent(log: Log): ERC1155.Event? {
            for (event in EVENTS) {
                return event.decode(log) ?: continue
            }
            return null
        }
    }
}
