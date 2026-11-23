package io.ethers.aa.bundle

import io.ethers.aa.account.Call
import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiFunction
import io.ethers.abi.AbiType
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf

/**
 * [CallDataDecoder] for the execution functions most accounts share, regardless of their implementation:
 * - `execute(address,uint256,bytes)` and `executeBatch((address,uint256,bytes)[])`, used by SimpleAccount, Light
 *   Account, Coinbase Smart Wallet, Solady, Modular Account v2 and others,
 * - `executeBatch(address[],uint256[],bytes[])` and `executeBatch(address[],bytes[])`,
 * - ERC-7579 `execute(bytes32,bytes)` with the single and batch call types,
 * - any of the above wrapped into ERC-4337 `executeUserOp`, whose selector prefixes the call data.
 *
 * Call data it does not recognize decodes to null.
 */
object StandardCallDataDecoder : CallDataDecoder {
    private val EXECUTE = AbiFunction.parseSignature("execute(address,uint256,bytes)")
    private val EXECUTE_BATCH_TUPLES = AbiFunction.parseSignature("executeBatch((address,uint256,bytes)[])")
    private val EXECUTE_BATCH_ARRAYS = AbiFunction.parseSignature("executeBatch(address[],uint256[],bytes[])")
    private val EXECUTE_BATCH_NO_VALUE = AbiFunction.parseSignature("executeBatch(address[],bytes[])")
    private val ERC7579_EXECUTE = AbiFunction.parseSignature("execute(bytes32,bytes)")

    // IAccountExecute.executeUserOp(PackedUserOperation,bytes32): the EntryPoint passes the operation's own callData
    // starting with this selector, followed by the account's call data
    private val EXECUTE_USER_OP_SELECTOR = byteArrayOf(0x8d.toByte(), 0xd7.toByte(), 0x71, 0x2f)

    private val ERC7579_BATCH = AbiType.Array(AbiType.Tuple(AbiType.Address, AbiType.UInt(256), AbiType.Bytes))

    private const val CALL_TYPE_SINGLE: Byte = 0x00
    private const val CALL_TYPE_BATCH: Byte = 0x01

    override fun decodeCalls(sender: Address, callData: Bytes): List<Call>? {
        return try {
            decode(callData)
        } catch (e: Exception) {
            null
        }
    }

    private fun decode(callData: Bytes): List<Call>? {
        if (callData.size < 4) return null

        val raw = callData.asByteArray()
        if (startsWith(raw, EXECUTE_USER_OP_SELECTOR)) {
            return decode(Bytes(raw.copyOfRange(4, raw.size)))
        }

        return when {
            callData.startsWith(EXECUTE.selector) -> {
                val args = EXECUTE.decodeCall(callData)
                listOf(Call(args[0] as Address, args[1] as BigInteger, args[2] as Bytes))
            }

            callData.startsWith(EXECUTE_BATCH_TUPLES.selector) -> {
                (EXECUTE_BATCH_TUPLES.decodeCall(callData)[0] as List<*>).map { tupleToCall(it) }
            }

            callData.startsWith(EXECUTE_BATCH_ARRAYS.selector) -> {
                val args = EXECUTE_BATCH_ARRAYS.decodeCall(callData)
                val targets = args[0] as List<*>
                val values = args[1] as List<*>
                val data = args[2] as List<*>
                // SimpleAccount allows an empty value array, meaning no value for every call
                require(values.isEmpty() || values.size == targets.size) { "Mismatched executeBatch arrays" }
                require(data.size == targets.size) { "Mismatched executeBatch arrays" }
                targets.indices.map { i ->
                    Call(
                        targets[i] as Address,
                        if (values.isEmpty()) bigIntegerOf(0) else values[i] as BigInteger,
                        data[i] as Bytes,
                    )
                }
            }

            callData.startsWith(EXECUTE_BATCH_NO_VALUE.selector) -> {
                val args = EXECUTE_BATCH_NO_VALUE.decodeCall(callData)
                val targets = args[0] as List<*>
                val data = args[1] as List<*>
                require(data.size == targets.size) { "Mismatched executeBatch arrays" }
                targets.indices.map { i -> Call(targets[i] as Address, bigIntegerOf(0), data[i] as Bytes) }
            }

            callData.startsWith(ERC7579_EXECUTE.selector) -> decodeErc7579(ERC7579_EXECUTE.decodeCall(callData))

            else -> null
        }
    }

    private fun decodeErc7579(args: List<Any>): List<Call>? {
        val mode = (args[0] as Bytes).asByteArray()
        val executionData = (args[1] as Bytes).asByteArray()

        return when (mode[0]) {
            // abi.encodePacked(target, value, callData)
            CALL_TYPE_SINGLE -> {
                if (executionData.size < 52) return null
                listOf(
                    Call(
                        Address(executionData.copyOfRange(0, 20)),
                        BigInteger(1, executionData.copyOfRange(20, 52)),
                        Bytes(executionData.copyOfRange(52, executionData.size)),
                    ),
                )
            }

            // abi.encode(Execution[])
            CALL_TYPE_BATCH -> (AbiCodec.decode(ERC7579_BATCH, executionData)).map { tupleToCall(it) }

            // delegatecall and other call types do not make plain calls
            else -> null
        }
    }

    private fun tupleToCall(tuple: Any?): Call {
        val fields = tuple as List<*>
        return Call(fields[0] as Address, fields[1] as BigInteger, fields[2] as Bytes)
    }

    private fun startsWith(data: ByteArray, prefix: ByteArray): Boolean {
        if (data.size < prefix.size) return false
        for (i in prefix.indices) if (data[i] != prefix[i]) return false
        return true
    }
}
