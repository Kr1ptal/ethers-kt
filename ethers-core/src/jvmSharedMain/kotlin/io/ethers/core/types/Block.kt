package io.ethers.core.types

import io.ethers.core.HexLongSerializer
import io.ethers.core.asAddress
import io.ethers.core.asBloom
import io.ethers.core.asBytes
import io.ethers.core.asHash
import io.ethers.core.asHexBigInteger
import io.ethers.core.asHexLong
import io.ethers.core.json.JsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger

@Serializable(with = BlockWithHashesSerializer::class)
data class BlockWithHashes(
    override val baseFeePerGas: BigInteger?,
    override val difficulty: BigInteger,
    override val extraData: Bytes,
    override val gasLimit: Long,
    override val gasUsed: Long,
    override val hash: Hash?,
    override val logsBloom: Bloom,
    override val miner: Address?,
    override val mixHash: Hash?,
    override val nonce: BigInteger?,
    override val number: Long,
    override val parentHash: Hash,
    override val receiptsRoot: Hash,
    override val sha3Uncles: Hash,
    override val size: Long,
    override val stateRoot: Hash,
    override val timestamp: Long,
    override val totalDifficulty: BigInteger,
    override val transactions: List<Hash>,
    override val transactionsRoot: Hash,
    override val uncles: List<Hash>,
    override val withdrawals: List<Withdrawal>?,
    override val withdrawalsRoot: Hash?,
    override val blobGasUsed: Long,
    override val excessBlobGas: Long,
    override val parentBeaconBlockRoot: Hash?,
    override val otherFields: Map<String, JsonElement> = emptyMap(),
) : Block<Hash> {
    // overridden so the number, hash, parentHash fields are at the top of the output
    override fun toString(): String {
        return "BlockWithHashes(number=$number, hash=$hash, parentHash=$parentHash, baseFeePerGas=$baseFeePerGas, difficulty=$difficulty, extraData=$extraData, gasLimit=$gasLimit, gasUsed=$gasUsed, logsBloom=$logsBloom, miner=$miner, mixHash=$mixHash, nonce=$nonce, receiptsRoot=$receiptsRoot, sha3Uncles=$sha3Uncles, size=$size, stateRoot=$stateRoot, timestamp=$timestamp, totalDifficulty=$totalDifficulty, transactions=$transactions, transactionsRoot=$transactionsRoot, uncles=$uncles, withdrawals=$withdrawals, withdrawalsRoot=$withdrawalsRoot, blobGasUsed=$blobGasUsed, excessBlobGas=$excessBlobGas, parentBeaconBlockRoot=$parentBeaconBlockRoot, otherFields=$otherFields)"
    }
}

@Serializable(with = BlockWithTransactionsSerializer::class)
data class BlockWithTransactions(
    override val baseFeePerGas: BigInteger?,
    override val difficulty: BigInteger,
    override val extraData: Bytes,
    override val gasLimit: Long,
    override val gasUsed: Long,
    override val hash: Hash?,
    override val logsBloom: Bloom,
    override val miner: Address?,
    override val mixHash: Hash?,
    override val nonce: BigInteger?,
    override val number: Long,
    override val parentHash: Hash,
    override val receiptsRoot: Hash,
    override val sha3Uncles: Hash,
    override val size: Long,
    override val stateRoot: Hash,
    override val timestamp: Long,
    override val totalDifficulty: BigInteger,
    override val transactions: List<RPCTransaction>,
    override val transactionsRoot: Hash,
    override val uncles: List<Hash>,
    override val withdrawals: List<Withdrawal>?,
    override val withdrawalsRoot: Hash?,
    override val blobGasUsed: Long,
    override val excessBlobGas: Long,
    override val parentBeaconBlockRoot: Hash?,
    override val otherFields: Map<String, JsonElement> = emptyMap(),
) : Block<RPCTransaction> {
    // overridden so the number, hash, parentHash fields are at the top of the output
    override fun toString(): String {
        return "BlockWithTransactions(number=$number, hash=$hash, parentHash=$parentHash, baseFeePerGas=$baseFeePerGas, difficulty=$difficulty, extraData=$extraData, gasLimit=$gasLimit, gasUsed=$gasUsed, logsBloom=$logsBloom, miner=$miner, mixHash=$mixHash, nonce=$nonce, receiptsRoot=$receiptsRoot, sha3Uncles=$sha3Uncles, size=$size, stateRoot=$stateRoot, timestamp=$timestamp, totalDifficulty=$totalDifficulty, transactions=$transactions, transactionsRoot=$transactionsRoot, uncles=$uncles, withdrawals=$withdrawals, withdrawalsRoot=$withdrawalsRoot, blobGasUsed=$blobGasUsed, excessBlobGas=$excessBlobGas, parentBeaconBlockRoot=$parentBeaconBlockRoot, otherFields=$otherFields)"
    }
}

interface Block<T> {
    val baseFeePerGas: BigInteger?
    val difficulty: BigInteger
    val extraData: Bytes
    val gasLimit: Long
    val gasUsed: Long
    val hash: Hash?
    val logsBloom: Bloom
    val miner: Address?
    val mixHash: Hash?
    val nonce: BigInteger?
    val number: Long
    val parentHash: Hash
    val receiptsRoot: Hash
    val sha3Uncles: Hash
    val size: Long
    val stateRoot: Hash
    val timestamp: Long
    val totalDifficulty: BigInteger
    val transactions: List<T>
    val transactionsRoot: Hash
    val uncles: List<Hash>
    val withdrawals: List<Withdrawal>?
    val withdrawalsRoot: Hash?
    val blobGasUsed: Long
    val excessBlobGas: Long
    val parentBeaconBlockRoot: Hash?
    val otherFields: Map<String, JsonElement>
}

/**
 * ETH staking withdrawal.
 */
@Serializable
data class Withdrawal(
    @Serializable(with = HexLongSerializer::class) val index: Long,
    @Serializable(with = HexLongSerializer::class) val validatorIndex: Long,
    val address: Address,
    @Serializable(with = HexLongSerializer::class) val amount: Long,
)

private data class BlockCommonData(
    val baseFeePerGas: BigInteger?,
    val difficulty: BigInteger,
    val extraData: Bytes,
    val gasLimit: Long,
    val gasUsed: Long,
    val hash: Hash?,
    val logsBloom: Bloom,
    val miner: Address?,
    val mixHash: Hash?,
    val nonce: BigInteger?,
    val number: Long,
    val parentHash: Hash,
    val receiptsRoot: Hash,
    val sha3Uncles: Hash,
    val size: Long,
    val stateRoot: Hash,
    val timestamp: Long,
    val totalDifficulty: BigInteger,
    val transactionsRoot: Hash,
    val uncles: List<Hash>,
    val withdrawals: List<Withdrawal>?,
    val withdrawalsRoot: Hash?,
    val blobGasUsed: Long,
    val excessBlobGas: Long,
    val parentBeaconBlockRoot: Hash?,
    val otherFields: Map<String, JsonElement>,
)

private fun deserializeBlockCommon(obj: JsonObject, jsonDecoder: JsonDecoder): BlockCommonData {
    var baseFeePerGas: BigInteger? = null
    var difficulty = BigInteger.ZERO
    lateinit var extraData: Bytes
    var gasLimit = -1L
    var gasUsed = -1L
    var hash: Hash? = null
    lateinit var logsBloom: Bloom
    var miner: Address? = null
    var mixHash: Hash? = null
    var nonce: BigInteger? = null
    var number = -1L
    lateinit var parentHash: Hash
    lateinit var receiptsRoot: Hash
    lateinit var sha3Uncles: Hash
    var size = -1L
    lateinit var stateRoot: Hash
    var timestamp = -1L
    var totalDifficulty = BigInteger.ZERO
    lateinit var transactionsRoot: Hash
    var uncles: List<Hash> = emptyList()
    var withdrawals: List<Withdrawal>? = null
    var withdrawalsRoot: Hash? = null
    var blobGasUsed = -1L
    var excessBlobGas = -1L
    var parentBeaconBlockRoot: Hash? = null
    var otherFields: MutableMap<String, JsonElement>? = null

    for ((key, element) in obj.entries) {
        when (key) {
            "baseFeePerGas" -> baseFeePerGas = if (element is JsonNull) null else element.jsonPrimitive.asHexBigInteger()
            "difficulty" -> difficulty = element.jsonPrimitive.asHexBigInteger()
            "extraData" -> extraData = element.jsonPrimitive.asBytes()
            "gasLimit" -> gasLimit = element.jsonPrimitive.asHexLong()
            "gasUsed" -> gasUsed = element.jsonPrimitive.asHexLong()
            "hash" -> hash = if (element is JsonNull) null else element.jsonPrimitive.asHash()
            "logsBloom" -> logsBloom = element.jsonPrimitive.asBloom()
            "miner" -> miner = if (element is JsonNull) null else element.jsonPrimitive.asAddress()
            "mixHash" -> mixHash = if (element is JsonNull) null else element.jsonPrimitive.asHash()
            "nonce" -> nonce = if (element is JsonNull) null else element.jsonPrimitive.asHexBigInteger()
            "number" -> number = element.jsonPrimitive.asHexLong()
            "parentHash" -> parentHash = element.jsonPrimitive.asHash()
            "receiptsRoot" -> receiptsRoot = element.jsonPrimitive.asHash()
            "sha3Uncles" -> sha3Uncles = element.jsonPrimitive.asHash()
            "size" -> size = element.jsonPrimitive.asHexLong()
            "stateRoot" -> stateRoot = element.jsonPrimitive.asHash()
            "timestamp" -> timestamp = element.jsonPrimitive.asHexLong()
            "totalDifficulty" -> totalDifficulty = element.jsonPrimitive.asHexBigInteger()
            "transactionsRoot" -> transactionsRoot = element.jsonPrimitive.asHash()
            "uncles" -> uncles = if (element is JsonNull) emptyList()
            else element.jsonArray.map { it.jsonPrimitive.asHash() }
            "withdrawals" -> withdrawals = if (element is JsonNull) null
            else element.jsonArray.map { jsonDecoder.json.decodeFromJsonElement(Withdrawal.serializer(), it) }
            "withdrawalsRoot" -> withdrawalsRoot = if (element is JsonNull) null else element.jsonPrimitive.asHash()
            "blobGasUsed" -> blobGasUsed = element.jsonPrimitive.asHexLong()
            "excessBlobGas" -> excessBlobGas = element.jsonPrimitive.asHexLong()
            "parentBeaconBlockRoot" -> parentBeaconBlockRoot = if (element is JsonNull) null else element.jsonPrimitive.asHash()
            "transactions" -> { /* handled by the specific serializer */ }
            else -> {
                if (otherFields == null) otherFields = HashMap()
                otherFields[key] = JsonElement(element.toString())
            }
        }
    }

    return BlockCommonData(
        baseFeePerGas, difficulty, extraData, gasLimit, gasUsed, hash, logsBloom, miner,
        mixHash, nonce, number, parentHash, receiptsRoot, sha3Uncles, size, stateRoot,
        timestamp, totalDifficulty, transactionsRoot, uncles, withdrawals, withdrawalsRoot,
        blobGasUsed, excessBlobGas, parentBeaconBlockRoot, otherFields ?: emptyMap(),
    )
}

object BlockWithHashesSerializer : KSerializer<BlockWithHashes> {
    override val descriptor = buildClassSerialDescriptor("BlockWithHashes")

    override fun serialize(encoder: Encoder, value: BlockWithHashes) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): BlockWithHashes {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val common = deserializeBlockCommon(obj, jsonDecoder)
        val transactions = obj["transactions"]?.let { arr ->
            if (arr is JsonNull) emptyList() else arr.jsonArray.map { it.jsonPrimitive.asHash() }
        } ?: emptyList()

        return BlockWithHashes(
            common.baseFeePerGas, common.difficulty, common.extraData, common.gasLimit,
            common.gasUsed, common.hash, common.logsBloom, common.miner, common.mixHash,
            common.nonce, common.number, common.parentHash, common.receiptsRoot,
            common.sha3Uncles, common.size, common.stateRoot, common.timestamp,
            common.totalDifficulty, transactions, common.transactionsRoot, common.uncles,
            common.withdrawals, common.withdrawalsRoot, common.blobGasUsed,
            common.excessBlobGas, common.parentBeaconBlockRoot, common.otherFields,
        )
    }
}

object BlockWithTransactionsSerializer : KSerializer<BlockWithTransactions> {
    override val descriptor = buildClassSerialDescriptor("BlockWithTransactions")

    override fun serialize(encoder: Encoder, value: BlockWithTransactions) = throw UnsupportedOperationException()

    override fun deserialize(decoder: Decoder): BlockWithTransactions {
        val jsonDecoder = decoder as JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val common = deserializeBlockCommon(obj, jsonDecoder)
        val transactions = obj["transactions"]?.let { arr ->
            if (arr is JsonNull) emptyList()
            else arr.jsonArray.map { jsonDecoder.json.decodeFromJsonElement(RPCTransactionSerializer, it) }
        } ?: emptyList()

        return BlockWithTransactions(
            common.baseFeePerGas, common.difficulty, common.extraData, common.gasLimit,
            common.gasUsed, common.hash, common.logsBloom, common.miner, common.mixHash,
            common.nonce, common.number, common.parentHash, common.receiptsRoot,
            common.sha3Uncles, common.size, common.stateRoot, common.timestamp,
            common.totalDifficulty, transactions, common.transactionsRoot, common.uncles,
            common.withdrawals, common.withdrawalsRoot, common.blobGasUsed,
            common.excessBlobGas, common.parentBeaconBlockRoot, common.otherFields,
        )
    }
}
