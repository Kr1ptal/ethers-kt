package io.ethers.aa

import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.core.Kotlinx
import io.ethers.core.types.Address
import io.ethers.core.types.Authorization
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * An ERC-4337 user operation, in the unpacked form used by bundler JSON-RPC APIs.
 *
 * A single type covers every [EntryPointVersion]. The on-chain layout differs between versions, so use [initCode],
 * [paymasterAndData], [toPacked] and [hash] with the [EntryPoint] the operation targets:
 * - **v0.6** has no paymaster gas limits ([paymasterVerificationGasLimit] and [paymasterPostOpGasLimit] must be
 *   null) and no [paymasterSignature]. On-chain, [factory] and [factoryData] form `initCode`, and [paymaster] and
 *   [paymasterData] form `paymasterAndData`.
 * - **v0.7 and later** pack gas limits and fees into 32-byte words, see [PackedUserOperation].
 * - **v0.8 and later** support EIP-7702 accounts: [factory] is [EIP7702_FACTORY_MARKER], and [eip7702Auth] carries
 *   the authorization the bundler includes in the bundle transaction.
 * - **v0.9** adds [paymasterSignature], which is excluded from the hash so the paymaster can sign the final hash.
 *
 * @property sender the smart account sending the operation.
 * @property nonce the full 256-bit nonce, made of a 192-bit key and a 64-bit sequence, see [nonceKey] and
 * [nonceSequence].
 * @property eip7702Auth the signed EIP-7702 authorization of [sender], when the account is an EIP-7702 delegated
 * EOA that still needs to be delegated. It is not part of the on-chain user operation.
 */
data class UserOperation @JvmOverloads constructor(
    val sender: Address,
    val nonce: BigInteger,
    val callData: Bytes,
    val callGasLimit: BigInteger,
    val verificationGasLimit: BigInteger,
    val preVerificationGas: BigInteger,
    val maxFeePerGas: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
    val signature: Bytes,
    val factory: Address? = null,
    val factoryData: Bytes? = null,
    val paymaster: Address? = null,
    val paymasterVerificationGasLimit: BigInteger? = null,
    val paymasterPostOpGasLimit: BigInteger? = null,
    val paymasterData: Bytes? = null,
    val paymasterSignature: Bytes? = null,
    val eip7702Auth: Authorization? = null,
) {
    init {
        require(factory != null || factoryData == null || factoryData.isEmpty) { "factoryData set without factory" }
        require(paymaster != null || paymasterData == null || paymasterData.isEmpty) {
            "paymasterData set without paymaster"
        }
        require(paymaster != null || (paymasterSignature == null || paymasterSignature.isEmpty)) {
            "paymasterSignature set without paymaster"
        }
    }

    /**
     * The 192-bit key of [nonce]. Each key has its own sequence, which lets an account send operations in parallel.
     */
    val nonceKey: BigInteger
        get() = nonce.shiftRight(64)

    /**
     * The 64-bit sequence of [nonce] within its [nonceKey].
     */
    val nonceSequence: BigInteger
        get() = nonce.subtract(nonceKey.shiftLeft(64))

    /**
     * Whether this operation is sent from an EIP-7702 delegated EOA, i.e. [factory] is [EIP7702_FACTORY_MARKER].
     */
    val isEip7702: Boolean
        get() = factory == EIP7702_FACTORY_MARKER

    /**
     * The on-chain `initCode`: [factory] followed by [factoryData], or empty if there is no factory.
     */
    val initCode: Bytes
        get() = when (factory) {
            null -> Bytes.EMPTY
            else -> AbiCodec.encodePacked(listOf(AbiType.Address, AbiType.Bytes), listOf(factory, factoryData ?: Bytes.EMPTY))
        }

    /**
     * Return the on-chain `paymasterAndData` field for the given EntryPoint [version], or empty if there is no
     * paymaster.
     *
     * For v0.6 it is [paymaster] followed by [paymasterData]. For v0.7 and later it also includes the two 16-byte
     * paymaster gas limits, and for v0.9 a [paymasterSignature] suffix.
     */
    fun paymasterAndData(version: EntryPointVersion): Bytes {
        return Bytes(paymasterAndDataBytes(version, forHash = false))
    }

    /**
     * Return the packed on-chain form of this operation for EntryPoint [version] (v0.7 or later).
     *
     * @throws IllegalArgumentException if [version] is v0.6.
     * @throws io.ethers.abi.AbiCodecException if a gas value overflows its packed 128 bits.
     */
    fun toPacked(version: EntryPointVersion): PackedUserOperation {
        require(version.usesPackedUserOperation) { "EntryPoint $version does not use packed user operations" }
        return PackedUserOperation(
            sender = sender,
            nonce = nonce,
            initCode = initCode,
            callData = callData,
            accountGasLimits = packUints(verificationGasLimit, callGasLimit),
            preVerificationGas = preVerificationGas,
            gasFees = packUints(maxPriorityFeePerGas, maxFeePerGas),
            paymasterAndData = paymasterAndData(version),
            signature = signature,
        )
    }

    /**
     * Compute the hash of this operation as [entryPoint] on chain [chainId] computes it. This is the hash the
     * account signs, and which identifies the operation in bundler APIs and EntryPoint events.
     *
     * For an EIP-7702 operation on EntryPoint v0.8 or later ([isEip7702]), the hash covers the address the sender
     * delegates to, which defaults to the address of [eip7702Auth]. Pass [eip7702Delegate] if the sender is already
     * delegated and the operation carries no authorization.
     *
     * @throws IllegalArgumentException if the operation cannot be represented on the EntryPoint version, e.g. v0.6
     * with paymaster gas limits, or the delegate of an EIP-7702 operation is unknown.
     * @throws io.ethers.abi.AbiCodecException if a gas value overflows its packed 128 bits.
     */
    @JvmOverloads
    fun hash(entryPoint: EntryPoint, chainId: Long, eip7702Delegate: Address? = eip7702Auth?.address): Hash {
        return UserOperationHasher.hash(this, entryPoint, chainId, eip7702Delegate)
    }

    /**
     * Encode this operation into its ERC-7769 JSON-RPC form for EntryPoint [version], see [UserOperationSerializer].
     */
    fun toJson(version: EntryPointVersion): JsonObject {
        return Kotlinx.DEFAULT.encodeToJsonElement(UserOperationSerializer.of(version), this).jsonObject
    }

    /**
     * Return a copy of this operation with [signature] replaced.
     */
    fun withSignature(signature: Bytes): UserOperation = copy(signature = signature)

    internal fun paymasterAndDataBytes(version: EntryPointVersion, forHash: Boolean): ByteArray {
        val paymaster = this.paymaster ?: return EMPTY_ARRAY
        val data = paymasterData ?: Bytes.EMPTY

        if (!version.usesPackedUserOperation) {
            require(paymasterVerificationGasLimit == null && paymasterPostOpGasLimit == null) {
                "EntryPoint v0.6 does not support paymaster gas limits, they are part of verificationGasLimit"
            }
            require(paymasterSignature == null || paymasterSignature.isEmpty) {
                "EntryPoint $version does not support paymaster signatures"
            }
            return AbiCodec.encodePacked(listOf(AbiType.Address, AbiType.Bytes), listOf(paymaster, data)).asByteArray()
        }

        val pmSignature = paymasterSignature ?: Bytes.EMPTY
        require(pmSignature.isEmpty || version.supportsPaymasterSignature) {
            "EntryPoint $version does not support paymaster signatures"
        }

        // UserOperationLib: paymaster || uint128 verificationGasLimit || uint128 postOpGasLimit || paymasterData,
        // followed for v0.9 by paymasterSignature || uint16 signatureLength || PAYMASTER_SIG_MAGIC
        val types = arrayListOf<AbiType<*>>(AbiType.Address, AbiType.UInt(128), AbiType.UInt(128), AbiType.Bytes)
        val values = arrayListOf<Any>(
            paymaster,
            paymasterVerificationGasLimit ?: ZERO,
            paymasterPostOpGasLimit ?: ZERO,
            data,
        )

        when {
            pmSignature.isEmpty -> Unit

            // the signature is excluded from the hash, but its magic is kept, so a signed and unsigned
            // paymasterAndData never hash the same
            forHash -> {
                types.add(AbiType.FixedBytes(8))
                values.add(PAYMASTER_SIG_MAGIC)
            }

            else -> {
                types.addAll(listOf(AbiType.Bytes, AbiType.UInt(16), AbiType.FixedBytes(8)))
                values.addAll(listOf(pmSignature, bigIntegerOf(pmSignature.size.toLong()), PAYMASTER_SIG_MAGIC))
            }
        }

        return AbiCodec.encodePacked(types, values).asByteArray()
    }

    companion object {
        /**
         * The `factory` of an EIP-7702 user operation (EntryPoint v0.8 and later): `0x7702` padded to 20 bytes. It
         * tells the EntryPoint the sender is a delegated EOA, and makes [hash] commit to the delegate address.
         */
        @JvmField
        val EIP7702_FACTORY_MARKER = Address("0x7702000000000000000000000000000000000000")

        internal val EMPTY_ARRAY = ByteArray(0)
        private val ZERO = bigIntegerOf(0)

        // keccak256("PaymasterSignature")[:8]
        internal val PAYMASTER_SIG_MAGIC = Bytes("0x22e325a297439656")
        internal const val PAYMASTER_DATA_OFFSET = 52
        private const val PAYMASTER_SUFFIX_LEN = 10

        /**
         * Decode an operation from its ERC-7769 JSON-RPC form for EntryPoint [version], or detect the layout from the
         * fields present if [version] is null, see [UserOperationSerializer].
         *
         * @throws kotlinx.serialization.SerializationException if a required field is missing or malformed.
         */
        @JvmStatic
        @JvmOverloads
        fun fromJson(json: JsonElement, version: EntryPointVersion? = null): UserOperation {
            return Kotlinx.DEFAULT.decodeFromJsonElement(UserOperationSerializer.of(version), json)
        }

        /**
         * Build an operation from its on-chain EntryPoint v0.6 fields, splitting [initCode] into factory and factory
         * data, and [paymasterAndData] into paymaster and paymaster data.
         *
         * @throws IllegalArgumentException if [initCode] or [paymasterAndData] is non-empty but shorter than an
         * address.
         */
        @JvmStatic
        fun fromV06(
            sender: Address,
            nonce: BigInteger,
            initCode: Bytes,
            callData: Bytes,
            callGasLimit: BigInteger,
            verificationGasLimit: BigInteger,
            preVerificationGas: BigInteger,
            maxFeePerGas: BigInteger,
            maxPriorityFeePerGas: BigInteger,
            paymasterAndData: Bytes,
            signature: Bytes,
        ): UserOperation {
            val (factory, factoryData) = splitAddressPrefix(initCode, "initCode")
            val (paymaster, paymasterData) = splitAddressPrefix(paymasterAndData, "paymasterAndData")
            return UserOperation(
                sender = sender,
                nonce = nonce,
                callData = callData,
                callGasLimit = callGasLimit,
                verificationGasLimit = verificationGasLimit,
                preVerificationGas = preVerificationGas,
                maxFeePerGas = maxFeePerGas,
                maxPriorityFeePerGas = maxPriorityFeePerGas,
                signature = signature,
                factory = factory,
                factoryData = factoryData,
                paymaster = paymaster,
                paymasterData = paymasterData,
            )
        }

        /**
         * Unpack an on-chain [PackedUserOperation] of EntryPoint [version] (v0.7 or later).
         *
         * For v0.9, a paymaster signature suffix of `paymasterAndData` is split into [paymasterSignature].
         *
         * @throws IllegalArgumentException if [version] is v0.6, or a field is malformed in a way the EntryPoint
         * would reject, e.g. `paymasterAndData` is shorter than its 52-byte static part.
         */
        @JvmStatic
        fun fromPacked(packed: PackedUserOperation, version: EntryPointVersion): UserOperation {
            require(version.usesPackedUserOperation) { "EntryPoint $version does not use packed user operations" }

            val (factory, factoryData) = splitAddressPrefix(packed.initCode, "initCode")
            val accountGasLimits = packed.accountGasLimits.asByteArray()
            val gasFees = packed.gasFees.asByteArray()

            var paymaster: Address? = null
            var paymasterVerificationGasLimit: BigInteger? = null
            var paymasterPostOpGasLimit: BigInteger? = null
            var paymasterData: Bytes? = null
            var paymasterSignature: Bytes? = null

            val pmAndData = packed.paymasterAndData.asByteArray()
            if (pmAndData.isNotEmpty()) {
                require(pmAndData.size >= PAYMASTER_DATA_OFFSET) {
                    "paymasterAndData must be at least $PAYMASTER_DATA_OFFSET bytes, got ${pmAndData.size}"
                }

                paymaster = Address(pmAndData.copyOfRange(0, 20))
                paymasterVerificationGasLimit = uint(pmAndData, 20, 36)
                paymasterPostOpGasLimit = uint(pmAndData, 36, 52)

                var dataEnd = pmAndData.size
                if (version.supportsPaymasterSignature) {
                    val sigLength = paymasterSignatureLength(pmAndData)
                    if (sigLength > 0) {
                        val sigEnd = pmAndData.size - PAYMASTER_SUFFIX_LEN
                        dataEnd = sigEnd - sigLength
                        paymasterSignature = Bytes(pmAndData.copyOfRange(dataEnd, sigEnd))
                    }
                }
                paymasterData = Bytes(pmAndData.copyOfRange(PAYMASTER_DATA_OFFSET, dataEnd))
            }

            return UserOperation(
                sender = packed.sender,
                nonce = packed.nonce,
                callData = packed.callData,
                callGasLimit = uint(accountGasLimits, 16, 32),
                verificationGasLimit = uint(accountGasLimits, 0, 16),
                preVerificationGas = packed.preVerificationGas,
                maxFeePerGas = uint(gasFees, 16, 32),
                maxPriorityFeePerGas = uint(gasFees, 0, 16),
                signature = packed.signature,
                factory = factory,
                factoryData = factoryData,
                paymaster = paymaster,
                paymasterVerificationGasLimit = paymasterVerificationGasLimit,
                paymasterPostOpGasLimit = paymasterPostOpGasLimit,
                paymasterData = paymasterData,
                paymasterSignature = paymasterSignature,
            )
        }

        /**
         * Mirrors `UserOperationLib.getPaymasterSignatureLength` of EntryPoint v0.9.
         */
        private fun paymasterSignatureLength(pmAndData: ByteArray): Int {
            val size = pmAndData.size
            if (size < PAYMASTER_DATA_OFFSET + PAYMASTER_SUFFIX_LEN) return 0

            val magic = PAYMASTER_SIG_MAGIC.asByteArray()
            val magicStart = size - magic.size
            for (i in magic.indices) {
                if (pmAndData[magicStart + i] != magic[i]) return 0
            }

            val length = ((pmAndData[size - 10].toInt() and 0xFF) shl 8) or (pmAndData[size - 9].toInt() and 0xFF)
            require(length <= size - PAYMASTER_DATA_OFFSET - PAYMASTER_SUFFIX_LEN) {
                "Invalid paymaster signature length $length for paymasterAndData of $size bytes"
            }
            return length
        }

        private fun splitAddressPrefix(data: Bytes, name: String): Pair<Address?, Bytes?> {
            if (data.isEmpty) return null to null
            // The EntryPoint detects EIP-7702 init code by reading a full word from its start, so a shortened,
            // zero-padded marker such as `0x7702` is valid too. Expand it to the 20-byte marker.
            if (data.size < 20 && isShortEip7702Marker(data.asByteArray())) return EIP7702_FACTORY_MARKER to Bytes.EMPTY
            require(data.size >= 20) { "$name must be empty or at least 20 bytes, got ${data.size}" }

            val arr = data.asByteArray()
            return Address(arr.copyOfRange(0, 20)) to Bytes(arr.copyOfRange(20, arr.size))
        }

        private fun isShortEip7702Marker(data: ByteArray): Boolean {
            if (data.size < 2 || data[0] != 0x77.toByte() || data[1] != 0x02.toByte()) return false
            for (i in 2 until data.size) if (data[i].toInt() != 0) return false
            return true
        }

        /**
         * Pack two 128-bit values into a 32-byte word, as `accountGasLimits` and `gasFees` are.
         */
        internal fun packUints(high: BigInteger, low: BigInteger): Bytes {
            return AbiCodec.encodePacked(listOf(AbiType.UInt(128), AbiType.UInt(128)), listOf(high, low))
        }

        /**
         * Read the big-endian unsigned integer in [data] from [from] (inclusive) to [to] (exclusive).
         */
        internal fun uint(data: ByteArray, from: Int, to: Int): BigInteger = BigInteger(1, data.copyOfRange(from, to))
    }
}

/**
 * The on-chain `PackedUserOperation` struct of EntryPoint v0.7 and later.
 *
 * @property accountGasLimits `verificationGasLimit` (high 128 bits) and `callGasLimit` (low 128 bits).
 * @property gasFees `maxPriorityFeePerGas` (high 128 bits) and `maxFeePerGas` (low 128 bits).
 */
data class PackedUserOperation(
    val sender: Address,
    val nonce: BigInteger,
    val initCode: Bytes,
    val callData: Bytes,
    val accountGasLimits: Bytes,
    val preVerificationGas: BigInteger,
    val gasFees: Bytes,
    val paymasterAndData: Bytes,
    val signature: Bytes,
) {
    init {
        require(accountGasLimits.size == 32) { "accountGasLimits must be 32 bytes" }
        require(gasFees.size == 32) { "gasFees must be 32 bytes" }
    }

    /**
     * Unpack into a [UserOperation] of EntryPoint [version], see [UserOperation.fromPacked].
     */
    fun toUserOperation(version: EntryPointVersion): UserOperation = UserOperation.fromPacked(this, version)
}
