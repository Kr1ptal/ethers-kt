package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.solana.types.AccountInfo
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.InstructionError
import io.ethers.solana.types.LogsNotification
import io.ethers.solana.types.Programs
import io.ethers.solana.types.RPCInstruction
import io.ethers.solana.types.RPCMessage
import io.ethers.solana.types.RPCTransaction
import io.ethers.solana.types.RPCTransactionData
import io.ethers.solana.types.RPCTransactionMeta
import io.ethers.solana.types.ReturnData
import io.ethers.solana.types.Signature
import io.ethers.solana.types.SolanaBytes
import io.ethers.solana.types.TokenAmount
import io.ethers.solana.types.TransactionError
import io.ethers.solana.types.TransactionSignature
import io.ethers.solana.types.TransactionSimulation
import io.ethers.solana.types.transaction.MessageHeader
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class RpcSharedTypesTest : FunSpec({
    val address = Programs.SYSTEM
    val signature = Signature(ByteArray(64))
    val blockhash = Blockhash(ByteArray(32))
    val json = Kotlinx.DEFAULT

    test("concrete models can be constructed copied and serialized independently") {
        val instruction = RPCInstruction(0, listOf(0), SolanaBytes.fromBytes(byteArrayOf(1, 2, 3)))
        val message = RPCMessage(MessageHeader(1, 0, 0), listOf(address), blockhash, listOf(instruction))
        val payload = RPCTransactionData(listOf(signature), message)
        val meta = RPCTransactionMeta(null, bigIntegerOf(5000), listOf(bigIntegerOf(6000)), listOf(bigIntegerOf(1000)))
        val tx = RPCTransaction(bigIntegerOf(42), null, payload, meta)
        json.decodeFromString<RPCTransaction>(json.encodeToString(tx)) shouldBe tx
        json.decodeFromString<RPCMessage>(json.encodeToString(message)) shouldBe message
        json.decodeFromString<RPCInstruction>(json.encodeToString(instruction)) shouldBe instruction
        json.decodeFromString<MessageHeader>(json.encodeToString(message.header)) shouldBe message.header
        json.decodeFromString<RPCTransactionMeta>(json.encodeToString(meta)) shouldBe meta
        json.decodeFromString<RPCTransactionData>(json.encodeToString(payload)) shouldBe payload
        val updated = tx.copy(meta = meta.copy(fee = bigIntegerOf(123)))
        json.encodeToJsonElement(updated).jsonObject.getValue("meta").jsonObject["fee"] shouldBe JsonPrimitive(123)
        // Class serializers work with strict Json too, not just Kotlinx.DEFAULT's ignoreUnknownKeys.
        val extended = JsonObject(json.encodeToJsonElement(tx).jsonObject + ("future" to JsonPrimitive(true)))
        Json.decodeFromJsonElement<RPCTransaction>(extended).otherFields["future"] shouldBe JsonPrimitive(true)
    }

    test("otherFields is flattened without collisions or accidental interpretation of its wire name") {
        val fields = json.parseToJsonElement("""{"future":1.234567890123456789,"otherFields":{"opaque":true},"programIdIndex":0,"accounts":[],"data":""}""")
        val instruction = json.decodeFromJsonElement<RPCInstruction>(fields)
        instruction.otherFields.keys shouldBe setOf("future", "otherFields")
        json.encodeToJsonElement(instruction) shouldBe fields
        shouldThrow<IllegalArgumentException> {
            json.encodeToString(instruction.copy(otherFields = mapOf("data" to JsonPrimitive("override"))))
        }
    }

    test("token amounts use the same serializer in token endpoints and transaction balances") {
        val amount = TokenAmount(bigIntegerOf(123), 2, "1.23", BigDecimal("1.23"), mapOf("future" to JsonPrimitive(true)))
        json.decodeFromString<TokenAmount>(json.encodeToString(amount)) shouldBe amount
        json.encodeToJsonElement(amount).jsonObject["amount"] shouldBe JsonPrimitive("123")
        json.encodeToJsonElement(amount).jsonObject["uiAmount"] shouldBe json.parseToJsonElement("1.23")
        val nullable = json.decodeFromString<TokenAmount>("""{"amount":"0","decimals":255,"uiAmountString":"0","uiAmount":null}""")
        nullable.uiAmount shouldBe null
        for (invalid in listOf("-1", "256", "null")) {
            shouldThrow<IllegalArgumentException> { json.decodeFromString<TokenAmount>("""{"amount":"0","decimals":$invalid,"uiAmountString":"0"}""") }
        }
    }

    test("simulation composes account return data inner instructions and balance models") {
        val account = """{"data":["AQID","base64"],"executable":false,"lamports":1,"owner":"$address","rentEpoch":18446744073709551615,"space":3,"future":true}"""
        val returned = """{"programId":"$address","data":["AQID","base64"]}"""
        val response = """{"err":null,"accounts":[$account,null],"returnData":$returned,
            "innerInstructions":[{"index":0,"instructions":[{"programIdIndex":0,"accounts":[],"data":""}]}],
            "replacementBlockhash":{"blockhash":"$blockhash","lastValidBlockHeight":42},
            "loadedAccountsDataSize":4294967295,"fee":5000,"preBalances":[10],"postBalances":[5],
            "preTokenBalances":[],"postTokenBalances":[],"loadedAddresses":{"writable":[],"readonly":[]},
            "unitsConsumed":9,"logs":["hello"],"future":true}"""
        val simulation = json.decodeFromString<TransactionSimulation>(response)
        simulation.accounts!!.first() shouldBe json.decodeFromString<AccountInfo>(account)
        simulation.accounts[1] shouldBe null
        simulation.returnData shouldBe json.decodeFromString<ReturnData>(returned)
        simulation.returnData!!.data shouldBe SolanaBytes.fromBytes(byteArrayOf(1, 2, 3))
        simulation.innerInstructions!!.single().instructions.single().data.size shouldBe 0
        simulation.replacementBlockhash!!.blockhash shouldBe blockhash
        simulation.loadedAccountsDataSize shouldBe 4294967295L
        simulation.fee shouldBe bigIntegerOf(5000)
        simulation.preBalances shouldBe listOf(bigIntegerOf(10))
        simulation.postBalances shouldBe listOf(bigIntegerOf(5))
        simulation.preTokenBalances shouldBe emptyList()
        simulation.postTokenBalances shouldBe emptyList()
        simulation.loadedAddresses!!.writable shouldBe emptyList()
        simulation.otherFields["future"] shouldBe JsonPrimitive(true)
        json.decodeFromString<TransactionSimulation>(json.encodeToString(simulation)) shouldBe simulation
        // A null account occupies its original index, and omitted space derives from complete account data.
        val withoutSpace = JsonObject(json.parseToJsonElement(account).jsonObject - "space")
        json.decodeFromJsonElement<AccountInfo>(withoutSpace).space shouldBe bigIntegerOf(3)
        val decodedAccount = simulation.accounts.first()!!
        val bytes = decodedAccount.data
        bytes[0] = 9
        decodedAccount.data shouldBe byteArrayOf(1, 2, 3)
        for (invalid in listOf("""["AQID","base58"]""", """["AQID"]""", """["!","base64"]""", "null")) {
            shouldThrow<IllegalArgumentException> { json.decodeFromString<ReturnData>("""{"programId":"$address","data":$invalid}""") }
        }
    }

    test("all known simple runtime errors roundtrip with their wire names") {
        for (error in TransactionError.Simple.entries) {
            val encoded = JsonPrimitive(error.wireName)
            json.decodeFromJsonElement<TransactionError>(encoded) shouldBe error
            json.encodeToJsonElement<TransactionError>(error) shouldBe encoded
            json.encodeToJsonElement(error) shouldBe encoded
        }
        for (error in InstructionError.Simple.entries) {
            val encoded = JsonPrimitive(error.wireName)
            json.decodeFromJsonElement<InstructionError>(encoded) shouldBe error
            json.encodeToJsonElement<InstructionError>(error) shouldBe encoded
            json.encodeToJsonElement(error) shouldBe encoded
        }
    }

    test("parameterized runtime errors have typed payloads and independent serializers") {
        val failure = TransactionError.InstructionFailure(255, InstructionError.Custom(4294967295L))
        val encoded = json.parseToJsonElement("""{"InstructionError":[255,{"Custom":4294967295}]}""")
        json.decodeFromJsonElement<TransactionError>(encoded) shouldBe failure
        json.encodeToJsonElement<TransactionError>(failure) shouldBe encoded
        json.encodeToJsonElement(failure) shouldBe encoded
        json.decodeFromString<TransactionError.InstructionFailure>(encoded.toString()) shouldBe failure
        val variants = listOf(
            TransactionError.DuplicateInstruction(255) to """{"DuplicateInstruction":255}""",
            TransactionError.InsufficientFundsForRent(128) to """{"InsufficientFundsForRent":{"account_index":128}}""",
            TransactionError.ProgramExecutionTemporarilyRestricted(0) to """{"ProgramExecutionTemporarilyRestricted":{"account_index":0}}""",
        )
        for ((value, wire) in variants) {
            json.decodeFromString<TransactionError>(wire) shouldBe value
            json.encodeToJsonElement(value) shouldBe json.parseToJsonElement(wire)
        }
        val borsh = InstructionError.BorshIoError("invalid data")
        val extended = json.parseToJsonElement("""{"InsufficientFundsForRent":{"account_index":0,"future":1.234567890123456789}}""")
        val rent = json.decodeFromJsonElement<TransactionError.InsufficientFundsForRent>(extended)
        rent.accountIndex shouldBe 0
        rent.otherFields.keys shouldBe setOf("future")
        json.encodeToJsonElement(rent) shouldBe extended
        json.decodeFromString<InstructionError>("""{"BorshIoError":"invalid data"}""") shouldBe borsh
        json.decodeFromString<InstructionError.BorshIoError>(json.encodeToString(borsh)) shouldBe borsh
        for (invalid in listOf("""{"InstructionError":[0]}""", """{"DuplicateInstruction":256}""", """{"InsufficientFundsForRent":{"account_index":null}}""")) {
            shouldThrow<IllegalArgumentException> { json.decodeFromString<TransactionError>(invalid) }
        }
        shouldThrow<IllegalArgumentException> { json.encodeToString(InstructionError.Custom(-1)) }
        shouldThrow<IllegalArgumentException> { json.encodeToString(TransactionError.InstructionFailure(256, InstructionError.Custom(0))) }
    }

    test("unknown errors remain lossless including nested numbers and future named variants") {
        val future = json.parseToJsonElement("""{"FutureError":{"value":1.234567890123456789,"huge":1e999}}""")
        val txError = json.decodeFromJsonElement<TransactionError>(future)
        txError shouldBe TransactionError.Unknown(future)
        json.encodeToJsonElement(txError) shouldBe future
        val ixError = json.decodeFromJsonElement<InstructionError>(future)
        ixError shouldBe InstructionError.Unknown(future)
        json.encodeToJsonElement(ixError) shouldBe future
        val failure: TransactionError = TransactionError.InstructionFailure(0, ixError)
        json.encodeToJsonElement(failure).jsonObject.getValue("InstructionError").jsonArray[1] shouldBe future
        json.decodeFromString<TransactionError>(""""FutureError"""") shouldBe TransactionError.Unknown(JsonPrimitive("FutureError"))
    }

    test("history metadata logs and simulation share the same typed error") {
        val wire = """{"InstructionError":[0,"InvalidArgument"]}"""
        val expected = TransactionError.InstructionFailure(0, InstructionError.Simple.INVALID_ARGUMENT)
        json.decodeFromString<TransactionSignature>("""{"signature":"$signature","slot":1,"err":$wire}""").err shouldBe expected
        json.decodeFromString<LogsNotification>("""{"signature":"$signature","logs":[],"err":$wire}""").err shouldBe expected
        json.decodeFromString<TransactionSimulation>("""{"err":$wire}""").err shouldBe expected
        json.decodeFromString<RPCTransactionMeta>("""{"err":$wire,"fee":1,"preBalances":[],"postBalances":[]}""").err shouldBe expected
        shouldThrow<IllegalArgumentException> { json.decodeFromString<TransactionSimulation>("""{}""") }
        shouldThrow<IllegalArgumentException> { json.decodeFromString<LogsNotification>("""{"signature":"$signature","logs":[]}""") }
        json.decodeFromString<TransactionSimulation>("""{"err":null,"accounts":null}""").accounts shouldBe null
    }
})
