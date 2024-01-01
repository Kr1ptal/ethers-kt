package io.ethers.core.types

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.ethers.core.forEachObjectField
import io.ethers.core.readAddress
import io.ethers.core.readBloom
import io.ethers.core.readBytes
import io.ethers.core.readHash
import io.ethers.core.readHexBigInteger
import io.ethers.core.readHexLong
import io.ethers.core.readListOf
import io.ethers.core.readListOfHashes
import io.ethers.core.readOrNull
import java.math.BigInteger

@JsonDeserialize(using = BlockWithHashesDeserializer::class)
data class BlockWithHashes(
    override val baseFeePerGas: BigInteger?,
    override val difficulty: BigInteger,
    override val extraData: Bytes,
    override val gasLimit: Long,
    override val gasUsed: Long,
    override val hash: Hash?,
    override val logsBloom: Bloom,
    override val miner: Address?,
    override val mixHash: Hash,
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
    override val otherFields: Map<String, JsonNode> = emptyMap(),
) : Block<Hash>

@JsonDeserialize(using = BlockWithTransactionDeserialize::class)
data class BlockWithTransactions(
    override val baseFeePerGas: BigInteger?,
    override val difficulty: BigInteger,
    override val extraData: Bytes,
    override val gasLimit: Long,
    override val gasUsed: Long,
    override val hash: Hash?,
    override val logsBloom: Bloom,
    override val miner: Address?,
    override val mixHash: Hash,
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
    override val otherFields: Map<String, JsonNode> = emptyMap(),
) : Block<RPCTransaction>

interface Block<T> {
    val baseFeePerGas: BigInteger?
    val difficulty: BigInteger
    val extraData: Bytes
    val gasLimit: Long
    val gasUsed: Long
    val hash: Hash?
    val logsBloom: Bloom
    val miner: Address?
    val mixHash: Hash
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
    val otherFields: Map<String, JsonNode>
}

/**
 * ETH staking withdrawal.
 */
@JsonDeserialize(using = WithdrawalDeserializer::class)
data class Withdrawal(
    val index: Long,
    val validatorIndex: Long,
    val address: Address,
    val amount: Long,
)

private class BlockWithHashesDeserializer : GenericBlockDeserializer<Hash, BlockWithHashes>() {
    override fun readTransactions(p: JsonParser): List<Hash> {
        return p.readListOfHashes()
    }

    override fun createBlock(
        baseFeePerGas: BigInteger?,
        difficulty: BigInteger,
        extraData: Bytes,
        gasLimit: Long,
        gasUsed: Long,
        hash: Hash?,
        logsBloom: Bloom,
        miner: Address?,
        mixHash: Hash,
        nonce: BigInteger?,
        number: Long,
        parentHash: Hash,
        receiptsRoot: Hash,
        sha3Uncles: Hash,
        size: Long,
        stateRoot: Hash,
        timestamp: Long,
        totalDifficulty: BigInteger,
        transactions: List<Hash>,
        transactionsRoot: Hash,
        uncles: List<Hash>,
        withdrawals: List<Withdrawal>?,
        withdrawalsRoot: Hash?,
        blobGasUsed: Long,
        excessBlobGas: Long,
        otherFields: Map<String, JsonNode>,
    ): BlockWithHashes {
        return BlockWithHashes(
            baseFeePerGas,
            difficulty,
            extraData,
            gasLimit,
            gasUsed,
            hash,
            logsBloom,
            miner,
            mixHash,
            nonce,
            number,
            parentHash,
            receiptsRoot,
            sha3Uncles,
            size,
            stateRoot,
            timestamp,
            totalDifficulty,
            transactions,
            transactionsRoot,
            uncles,
            withdrawals,
            withdrawalsRoot,
            blobGasUsed,
            excessBlobGas,
            otherFields,
        )
    }
}

private class BlockWithTransactionDeserialize : GenericBlockDeserializer<RPCTransaction, BlockWithTransactions>() {
    override fun readTransactions(p: JsonParser): List<RPCTransaction> {
        return p.readListOf(RPCTransaction::class.java)
    }

    override fun createBlock(
        baseFeePerGas: BigInteger?,
        difficulty: BigInteger,
        extraData: Bytes,
        gasLimit: Long,
        gasUsed: Long,
        hash: Hash?,
        logsBloom: Bloom,
        miner: Address?,
        mixHash: Hash,
        nonce: BigInteger?,
        number: Long,
        parentHash: Hash,
        receiptsRoot: Hash,
        sha3Uncles: Hash,
        size: Long,
        stateRoot: Hash,
        timestamp: Long,
        totalDifficulty: BigInteger,
        transactions: List<RPCTransaction>,
        transactionsRoot: Hash,
        uncles: List<Hash>,
        withdrawals: List<Withdrawal>?,
        withdrawalsRoot: Hash?,
        blobGasUsed: Long,
        excessBlobGas: Long,
        otherFields: Map<String, JsonNode>,
    ): BlockWithTransactions {
        return BlockWithTransactions(
            baseFeePerGas,
            difficulty,
            extraData,
            gasLimit,
            gasUsed,
            hash,
            logsBloom,
            miner,
            mixHash,
            nonce,
            number,
            parentHash,
            receiptsRoot,
            sha3Uncles,
            size,
            stateRoot,
            timestamp,
            totalDifficulty,
            transactions,
            transactionsRoot,
            uncles,
            withdrawals,
            withdrawalsRoot,
            blobGasUsed,
            excessBlobGas,
            otherFields,
        )
    }
}

private abstract class GenericBlockDeserializer<TX, T : Block<TX>> : JsonDeserializer<T>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        var baseFeePerGas: BigInteger? = null
        lateinit var difficulty: BigInteger
        lateinit var extraData: Bytes
        var gasLimit = -1L
        var gasUsed = -1L
        var hash: Hash? = null
        lateinit var logsBloom: Bloom
        var miner: Address? = null
        lateinit var mixHash: Hash
        var nonce: BigInteger? = null
        var number: Long = -1L
        lateinit var parentHash: Hash
        lateinit var receiptsRoot: Hash
        lateinit var sha3Uncles: Hash
        var size = -1L
        lateinit var stateRoot: Hash
        var timestamp = -1L
        var totalDifficulty = BigInteger.ZERO
        var transactions: List<TX>? = null
        lateinit var transactionsRoot: Hash
        var uncles: List<Hash>? = null
        var withdrawals: List<Withdrawal>? = null
        var withdrawalsRoot: Hash? = null
        var blobGasUsed: Long = -1L
        var excessBlobGas: Long = -1L
        var otherFields: MutableMap<String, JsonNode>? = null

        p.forEachObjectField { field ->
            when (field) {
                "baseFeePerGas" -> baseFeePerGas = p.readOrNull { readHexBigInteger() }
                "difficulty" -> difficulty = p.readHexBigInteger()
                "extraData" -> extraData = p.readBytes()
                "gasLimit" -> gasLimit = p.readHexLong()
                "gasUsed" -> gasUsed = p.readHexLong()
                // null if pending block
                "hash" -> hash = p.readOrNull { p.readHash() }
                "logsBloom" -> logsBloom = p.readBloom()
                // null if pending block
                "miner" -> miner = p.readOrNull { readAddress() }
                "mixHash" -> mixHash = p.readHash()
                // null if pending block
                "nonce" -> nonce = p.readOrNull { p.readHexBigInteger() }
                "number" -> number = p.readHexLong()
                "parentHash" -> parentHash = p.readHash()
                "receiptsRoot" -> receiptsRoot = p.readHash()
                "sha3Uncles" -> sha3Uncles = p.readHash()
                "size" -> size = p.readHexLong()
                "stateRoot" -> stateRoot = p.readHash()
                "timestamp" -> timestamp = p.readHexLong()
                "totalDifficulty" -> totalDifficulty = p.readHexBigInteger()
                "transactions" -> transactions = readTransactions(p)
                "transactionsRoot" -> transactionsRoot = p.readHash()
                "uncles" -> uncles = p.readOrNull { readListOfHashes() }
                "withdrawals" -> withdrawals = p.readOrNull { readListOf(Withdrawal::class.java) }
                "withdrawalsRoot" -> withdrawalsRoot = p.readOrNull { readHash() }
                "blobGasUsed" -> blobGasUsed = p.readHexLong()
                "excessBlobGas" -> excessBlobGas = p.readHexLong()
                else -> {
                    if (otherFields == null) {
                        otherFields = HashMap()
                    }
                    otherFields!![field] = p.readValueAsTree()
                }
            }
        }

        return createBlock(
            baseFeePerGas,
            difficulty,
            extraData,
            gasLimit,
            gasUsed,
            hash,
            logsBloom,
            miner,
            mixHash,
            nonce,
            number,
            parentHash,
            receiptsRoot,
            sha3Uncles,
            size,
            stateRoot,
            timestamp,
            totalDifficulty,
            transactions ?: emptyList(),
            transactionsRoot,
            uncles ?: emptyList(),
            withdrawals,
            withdrawalsRoot,
            blobGasUsed,
            excessBlobGas,
            otherFields ?: emptyMap(),
        )
    }

    protected abstract fun readTransactions(p: JsonParser): List<TX>

    protected abstract fun createBlock(
        baseFeePerGas: BigInteger?,
        difficulty: BigInteger,
        extraData: Bytes,
        gasLimit: Long,
        gasUsed: Long,
        hash: Hash?,
        logsBloom: Bloom,
        miner: Address?,
        mixHash: Hash,
        nonce: BigInteger?,
        number: Long,
        parentHash: Hash,
        receiptsRoot: Hash,
        sha3Uncles: Hash,
        size: Long,
        stateRoot: Hash,
        timestamp: Long,
        totalDifficulty: BigInteger,
        transactions: List<TX>,
        transactionsRoot: Hash,
        uncles: List<Hash>,
        withdrawals: List<Withdrawal>?,
        withdrawalsRoot: Hash?,
        blobGasUsed: Long,
        excessBlobGas: Long,
        otherFields: Map<String, JsonNode>,
    ): T
}

private class WithdrawalDeserializer : JsonDeserializer<Withdrawal>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Withdrawal {
        if (p.currentToken != JsonToken.START_OBJECT) {
            throw IllegalArgumentException("Expected start object")
        }

        var index = -1L
        var validatorIndex = -1L
        lateinit var address: Address
        var amount = -1L

        p.forEachObjectField { field ->
            when (field) {
                "index" -> index = p.readHexLong()
                "validatorIndex" -> validatorIndex = p.readHexLong()
                "address" -> address = p.readAddress()
                "amount" -> amount = p.readHexLong()
                else -> throw IllegalArgumentException("Unknown field $field")
            }
        }

        return Withdrawal(
            index = index,
            validatorIndex = validatorIndex,
            address = address,
            amount = amount,
        )
    }
}
