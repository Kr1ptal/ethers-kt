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

public class EnsRegistry(
    provider: Middleware,
    address: Address,
) : AbiContract(provider, address) {
    /**
     * Call contract function
     *
     * Selector: `0xe985e9c5`
     *
     * Signature:
     * ```solidity
     *     function isApprovedForAll(address owner, address operator) external returns (bool);
     * ```
     */
    public fun isApprovedForAll(owner: Address, `operator`: Address): ReadFunctionCall<Boolean> = ReadFunctionCall(this.provider, this.address, FUNCTION_IS_APPROVED_FOR_ALL.encodeCall(listOf<Any>(owner, `operator`))) {
        val data = FUNCTION_IS_APPROVED_FOR_ALL.decodeResponse(it)
        data[0] as kotlin.Boolean
    }

    /**
     * Call contract function
     *
     * Selector: `0x02571be3`
     *
     * Signature:
     * ```solidity
     *     function owner(bytes32 node) external returns (address);
     * ```
     */
    public fun owner(node: Bytes): ReadFunctionCall<Address> = ReadFunctionCall(this.provider, this.address, FUNCTION_OWNER.encodeCall(listOf<Any>(node))) {
        val data = FUNCTION_OWNER.decodeResponse(it)
        data[0] as io.ethers.core.types.Address
    }

    /**
     * Call contract function
     *
     * Selector: `0xf79fe538`
     *
     * Signature:
     * ```solidity
     *     function recordExists(bytes32 node) external returns (bool);
     * ```
     */
    public fun recordExists(node: Bytes): ReadFunctionCall<Boolean> = ReadFunctionCall(this.provider, this.address, FUNCTION_RECORD_EXISTS.encodeCall(listOf<Any>(node))) {
        val data = FUNCTION_RECORD_EXISTS.decodeResponse(it)
        data[0] as kotlin.Boolean
    }

    /**
     * Call contract function
     *
     * Selector: `0x0178b8bf`
     *
     * Signature:
     * ```solidity
     *     function resolver(bytes32 node) external returns (address);
     * ```
     */
    public fun resolver(node: Bytes): ReadFunctionCall<Address> = ReadFunctionCall(this.provider, this.address, FUNCTION_RESOLVER.encodeCall(listOf<Any>(node))) {
        val data = FUNCTION_RESOLVER.decodeResponse(it)
        data[0] as io.ethers.core.types.Address
    }

    /**
     * Call contract function
     *
     * Selector: `0xa22cb465`
     *
     * Signature:
     * ```solidity
     *     function setApprovalForAll(address operator, bool approved) external;
     * ```
     */
    public fun setApprovalForAll(`operator`: Address, approved: Boolean): FunctionCall<Unit> = FunctionCall(this.provider, this.address, FUNCTION_SET_APPROVAL_FOR_ALL.encodeCall(listOf(`operator`, approved))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0x5b0fc9c3`
     *
     * Signature:
     * ```solidity
     *     function setOwner(bytes32 node, address owner) external;
     * ```
     */
    public fun setOwner(node: Bytes, owner: Address): FunctionCall<Unit> = FunctionCall(this.provider, this.address, FUNCTION_SET_OWNER.encodeCall(listOf<Any>(node, owner))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0xcf408823`
     *
     * Signature:
     * ```solidity
     *     function setRecord(bytes32 node, address owner, address resolver, uint64 ttl) external;
     * ```
     */
    public fun setRecord(
        node: Bytes,
        owner: Address,
        resolver: Address,
        ttl: BigInteger,
    ): FunctionCall<Unit> = FunctionCall(this.provider, this.address, FUNCTION_SET_RECORD.encodeCall(listOf(node, owner, resolver, ttl))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0x1896f70a`
     *
     * Signature:
     * ```solidity
     *     function setResolver(bytes32 node, address resolver) external;
     * ```
     */
    public fun setResolver(node: Bytes, resolver: Address): FunctionCall<Unit> = FunctionCall(this.provider, this.address, FUNCTION_SET_RESOLVER.encodeCall(listOf<Any>(node, resolver))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0x06ab5923`
     *
     * Signature:
     * ```solidity
     *     function setSubnodeOwner(bytes32 node, bytes32 label, address owner) external returns (bytes32);
     * ```
     */
    public fun setSubnodeOwner(
        node: Bytes,
        label: Bytes,
        owner: Address,
    ): FunctionCall<Bytes> = FunctionCall(this.provider, this.address, FUNCTION_SET_SUBNODE_OWNER.encodeCall(listOf<Any>(node, label, owner))) {
        val data = FUNCTION_SET_SUBNODE_OWNER.decodeResponse(it)
        data[0] as io.ethers.core.types.Bytes
    }

    /**
     * Call contract function
     *
     * Selector: `0x5ef2c7f0`
     *
     * Signature:
     * ```solidity
     *     function setSubnodeRecord(bytes32 node, bytes32 label, address owner, address resolver, uint64 ttl) external;
     * ```
     */
    public fun setSubnodeRecord(
        node: Bytes,
        label: Bytes,
        owner: Address,
        resolver: Address,
        ttl: BigInteger,
    ): FunctionCall<Unit> = FunctionCall(this.provider, this.address, FUNCTION_SET_SUBNODE_RECORD.encodeCall(listOf(node, label, owner, resolver, ttl))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0x14ab9038`
     *
     * Signature:
     * ```solidity
     *     function setTTL(bytes32 node, uint64 ttl) external;
     * ```
     */
    public fun setTTL(node: Bytes, ttl: BigInteger): FunctionCall<Unit> = FunctionCall(this.provider, this.address, FUNCTION_SET_TTL.encodeCall(listOf(node, ttl))) {
    }

    /**
     * Call contract function
     *
     * Selector: `0x16a25cbd`
     *
     * Signature:
     * ```solidity
     *     function ttl(bytes32 node) external returns (uint64);
     * ```
     */
    public fun ttl(node: Bytes): ReadFunctionCall<BigInteger> = ReadFunctionCall(this.provider, this.address, FUNCTION_TTL.encodeCall(listOf<Any>(node))) {
        val data = FUNCTION_TTL.decodeResponse(it)
        data[0] as java.math.BigInteger
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
     *     event ApprovalForAll(address indexed owner, address indexed operator, bool approved);
     * ```
     */
    public data class ApprovalForAll(
        public val owner: Address,
        public val `operator`: Address,
        public val approved: Boolean,
        override val log: Log,
    ) : EnsRegistry.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as ApprovalForAll
            if (owner != other.owner) return false
            if (operator != other.operator) return false
            if (approved != other.approved) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = owner.hashCode()
            result = 31 * result + operator.hashCode()
            result = 31 * result + approved.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<ApprovalForAll> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("ApprovalForAll", listOf(AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Address, true), AbiEvent.Token(AbiType.Bool, false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<ApprovalForAll> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): ApprovalForAll? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): ApprovalForAll = ApprovalForAll(data[0] as io.ethers.core.types.Address, data[1] as io.ethers.core.types.Address, data[2] as kotlin.Boolean, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xce0457fe73731f824cc272376169235128c118b49d344817417c6d108d155e82`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event NewOwner(bytes32 indexed node, bytes32 indexed label, address owner);
     * ```
     */
    public data class NewOwner(
        public val node: Bytes,
        public val label: Bytes,
        public val owner: Address,
        override val log: Log,
    ) : EnsRegistry.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as NewOwner
            if (node != other.node) return false
            if (label != other.label) return false
            if (owner != other.owner) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = node.hashCode()
            result = 31 * result + label.hashCode()
            result = 31 * result + owner.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<NewOwner> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("NewOwner", listOf(AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.Address, false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<NewOwner> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): NewOwner? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): NewOwner = NewOwner(data[0] as io.ethers.core.types.Bytes, data[1] as io.ethers.core.types.Bytes, data[2] as io.ethers.core.types.Address, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x335721b01866dc23fbee8b6b2c7b1e14d6f05c28cd35a2c934239f94095602a0`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event NewResolver(bytes32 indexed node, address resolver);
     * ```
     */
    public data class NewResolver(
        public val node: Bytes,
        public val resolver: Address,
        override val log: Log,
    ) : EnsRegistry.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as NewResolver
            if (node != other.node) return false
            if (resolver != other.resolver) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = node.hashCode()
            result = 31 * result + resolver.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<NewResolver> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("NewResolver", listOf(AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.Address, false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<NewResolver> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): NewResolver? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): NewResolver = NewResolver(data[0] as io.ethers.core.types.Bytes, data[1] as io.ethers.core.types.Address, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0x1d4f9bbfc9cab89d66e1a1562f2233ccbf1308cb4f63de2ead5787adddb8fa68`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event NewTTL(bytes32 indexed node, uint64 ttl);
     * ```
     */
    public data class NewTTL(
        public val node: Bytes,
        public val ttl: BigInteger,
        override val log: Log,
    ) : EnsRegistry.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as NewTTL
            if (node != other.node) return false
            if (ttl != other.ttl) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = node.hashCode()
            result = 31 * result + ttl.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<NewTTL> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("NewTTL", listOf(AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.UInt(64), false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<NewTTL> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): NewTTL? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): NewTTL = NewTTL(data[0] as io.ethers.core.types.Bytes, data[1] as java.math.BigInteger, log)
        }
    }

    /**
     * Contract event (indexed dynamic types are replaced with `bytes32`)
     *
     * Topic ID: `0xd4735d920b0f87494915f556dd9b54c8f309026070caea5c737245152564d266`
     *
     * Anonymous: `false`
     *
     * Signature:
     * ```solidity
     *     event Transfer(bytes32 indexed node, address owner);
     * ```
     */
    public data class Transfer(
        public val node: Bytes,
        public val owner: Address,
        override val log: Log,
    ) : EnsRegistry.Event() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Transfer
            if (node != other.node) return false
            if (owner != other.owner) return false
            if (log != other.log) return false
            return true
        }

        override fun hashCode(): Int {
            var result = node.hashCode()
            result = 31 * result + owner.hashCode()
            result = 31 * result + log.hashCode()
            return result
        }

        public companion object : EventFactory<Transfer> {
            @JvmStatic
            override val abi: AbiEvent =
                AbiEvent("Transfer", listOf(AbiEvent.Token(AbiType.FixedBytes(32), true), AbiEvent.Token(AbiType.Address, false)), false)

            @JvmStatic
            override fun filter(provider: Middleware): EventFilter<Transfer> = EventFilter(provider, this)

            @JvmStatic
            override fun decode(log: Log): Transfer? = super.decode(log)

            @JvmStatic
            override fun decode(log: Log, `data`: List<Any>): Transfer = Transfer(data[0] as io.ethers.core.types.Bytes, data[1] as io.ethers.core.types.Address, log)
        }
    }

    public companion object {
        @JvmField
        public val EVENTS: List<EventFactory<out EnsRegistry.Event>> =
            listOf(ApprovalForAll, NewOwner, NewResolver, NewTTL, Transfer)

        @JvmField
        public val FUNCTION_SET_SUBNODE_OWNER: AbiFunction =
            AbiFunction("setSubnodeOwner", listOf(AbiType.FixedBytes(32), AbiType.FixedBytes(32), AbiType.Address), listOf(AbiType.FixedBytes(32)))

        @JvmField
        public val FUNCTION_IS_APPROVED_FOR_ALL: AbiFunction =
            AbiFunction("isApprovedForAll", listOf(AbiType.Address, AbiType.Address), listOf(AbiType.Bool))

        @JvmField
        public val FUNCTION_OWNER: AbiFunction =
            AbiFunction("owner", listOf(AbiType.FixedBytes(32)), listOf(AbiType.Address))

        @JvmField
        public val FUNCTION_SET_OWNER: AbiFunction =
            AbiFunction("setOwner", listOf(AbiType.FixedBytes(32), AbiType.Address), listOf())

        @JvmField
        public val FUNCTION_SET_RESOLVER: AbiFunction =
            AbiFunction("setResolver", listOf(AbiType.FixedBytes(32), AbiType.Address), listOf())

        @JvmField
        public val FUNCTION_SET_TTL: AbiFunction =
            AbiFunction("setTTL", listOf(AbiType.FixedBytes(32), AbiType.UInt(64)), listOf())

        @JvmField
        public val FUNCTION_RESOLVER: AbiFunction =
            AbiFunction("resolver", listOf(AbiType.FixedBytes(32)), listOf(AbiType.Address))

        @JvmField
        public val FUNCTION_TTL: AbiFunction =
            AbiFunction("ttl", listOf(AbiType.FixedBytes(32)), listOf(AbiType.UInt(64)))

        @JvmField
        public val FUNCTION_SET_RECORD: AbiFunction =
            AbiFunction("setRecord", listOf(AbiType.FixedBytes(32), AbiType.Address, AbiType.Address, AbiType.UInt(64)), listOf())

        @JvmField
        public val FUNCTION_RECORD_EXISTS: AbiFunction =
            AbiFunction("recordExists", listOf(AbiType.FixedBytes(32)), listOf(AbiType.Bool))

        @JvmField
        public val FUNCTION_SET_APPROVAL_FOR_ALL: AbiFunction =
            AbiFunction("setApprovalForAll", listOf(AbiType.Address, AbiType.Bool), listOf())

        @JvmField
        public val FUNCTION_SET_SUBNODE_RECORD: AbiFunction =
            AbiFunction("setSubnodeRecord", listOf(AbiType.FixedBytes(32), AbiType.FixedBytes(32), AbiType.Address, AbiType.Address, AbiType.UInt(64)), listOf())

        @JvmStatic
        public fun decodeEvent(log: Log): EnsRegistry.Event? {
            for (event in EVENTS) {
                return event.decode(log) ?: continue
            }
            return null
        }
    }
}
