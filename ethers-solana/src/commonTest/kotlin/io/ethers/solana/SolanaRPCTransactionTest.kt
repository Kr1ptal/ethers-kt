package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.crypto.Base58
import io.ethers.solana.types.InnerInstructions
import io.ethers.solana.types.InstructionError
import io.ethers.solana.types.LoadedAddresses
import io.ethers.solana.types.Programs
import io.ethers.solana.types.ReturnData
import io.ethers.solana.types.Reward
import io.ethers.solana.types.RewardType
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaRPCInstruction
import io.ethers.solana.types.SolanaRPCMessage
import io.ethers.solana.types.SolanaRPCTransaction
import io.ethers.solana.types.SolanaRPCTransactionData
import io.ethers.solana.types.SolanaRPCTransactionMeta
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.TokenAmount
import io.ethers.solana.types.TokenBalance
import io.ethers.solana.types.transaction.CompiledAddressLookupTable
import io.ethers.solana.types.transaction.MessageHeader
import io.ethers.solana.types.transaction.SolanaTxType
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ethers.core.json.JsonElement as RawJson

class SolanaRPCTransactionTest : FunSpec({
    val signature = SolanaSignature(ByteArray(64)) // Decode format, but do not verify this invalid signature.
    val address = Programs.SYSTEM
    val blockhash = SolanaBlockhash(ByteArray(32) { 3 })
    val fixtures = (
        Kotlinx.DEFAULT.parseToJsonElement(liveRpcTransactions).jsonArray +
            Kotlinx.DEFAULT.parseToJsonElement(liveRpcVersionResponses).jsonArray
        ).associate {
        it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject.getValue("result").jsonObject
    }
    val legacy = fixtures.getValue("1")
    fun decode(json: JsonElement): SolanaRPCTransaction = Kotlinx.DEFAULT.decodeFromJsonElement(json)
    fun roundtrip(json: JsonElement) {
        val tx = decode(json)
        // unknown fields are re-emitted as the text they arrived as, so compare the wire string
        val encoded = Kotlinx.DEFAULT.parseToJsonElement(Kotlinx.DEFAULT.encodeToString(tx))
        Kotlinx.DEFAULT.parseToJsonElement(Kotlinx.DEFAULT.encodeToString(decode(encoded))) shouldBe encoded
    }
    fun checkRequired(obj: JsonObject, keys: List<String>, parse: (JsonObject) -> Any) {
        for (key in keys) {
            shouldThrow<IllegalArgumentException> { parse(JsonObject(obj - key)) }
            shouldThrow<IllegalArgumentException> { parse(JsonObject(obj + (key to JsonNull))) }
        }
    }

    test("captured mainnet legacy and v0 responses have non-null type and required fields") {
        listOf("1", "3", "4").map(fixtures::getValue).forEach(::roundtrip)
        val legacyTx = decode(legacy)
        val legacyType: SolanaTxType = legacyTx.type
        legacyType shouldBe SolanaTxType.Legacy
        val transaction = legacyTx.transaction
        transaction.signatures.single().toString() shouldBe "3AkXwBzV2XtehqncPowVrgKXre7DH8iTsuCU3eDDNqrsBEPKWS4Q5xYzrD3sD9GjWbyxaZJng76xFYg2nocAQ57R"
        val message = transaction.message
        message.header.requiredSignatures shouldBe 1
        message.accountKeys.size shouldBe 24
        message.addressTableLookups shouldBe emptyList()
        (message.instructions.first()).programIdIndex shouldBe 14
        val meta = legacyTx.meta!!
        val fee: BigInteger = meta.fee
        fee shouldBe BigInteger("5000")
        meta.isSuccess shouldBe true
        meta.preBalances.size shouldBe 24
        meta.postTokenBalances!!.first().uiTokenAmount.decimals shouldBe 6
        legacyTx.otherFields["transactionIndex"] shouldBe RawJson("1069")

        val compiled = decode(fixtures.getValue("4"))
        compiled.type shouldBe SolanaTxType.V0
        val v0Message = compiled.transaction.message
        v0Message.addressTableLookups.single().writableIndexes shouldBe listOf(11)
        compiled.meta!!.loadedAddresses!!.writable.size shouldBe 1
    }

    test("observed legacy version omission maps to Legacy while explicit malformed versions fail") {
        val omitted = fixtures.getValue("3")
        omitted.containsKey("version") shouldBe false
        decode(omitted).type shouldBe SolanaTxType.Legacy
        for (version in listOf(
            JsonNull,
            JsonPrimitive("future"),
            JsonPrimitive("0"),
            JsonPrimitive(-1),
            JsonPrimitive(256),
            JsonObject(emptyMap()),
            JsonPrimitive(0.5),
        )) {
            shouldThrow<IllegalArgumentException> { decode(JsonObject(legacy + ("version" to version))) }
        }
        for (version in listOf(2, 127, 255)) {
            val tx = decode(JsonObject(legacy + ("version" to JsonPrimitive(version))))
            tx.type shouldBe SolanaTxType.Unsupported(version)
            val message = tx.transaction.message
            message.header.requiredSignatures shouldBe 1
        }
        SolanaTxType.fromVersion(0) shouldBe SolanaTxType.V0
        SolanaTxType.fromVersion(1) shouldBe SolanaTxType.V1
        SolanaTxType.V1.isSupported shouldBe true
        shouldThrow<IllegalArgumentException> { SolanaTxType.Unsupported(1) }
        val versioned: SolanaTxType.Versioned = SolanaTxType.Unsupported(2)
        versioned.version shouldBe 2
        versioned.isSupported shouldBe false
        shouldThrow<IllegalArgumentException> { SolanaTxType.fromVersion(-1) }
    }

    test("required fields cannot be omitted or null in recognized response structures") {
        checkRequired(legacy, listOf("slot", "transaction"), ::decode)
        for (key in listOf("blockTime", "meta")) {
            shouldThrow<IllegalArgumentException> { decode(JsonObject(legacy - key)) }
        }
        val payload = legacy.getValue("transaction").jsonObject
        checkRequired(payload, listOf("signatures", "message")) { Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCTransactionData>(it) }
        val message = payload.getValue("message").jsonObject
        checkRequired(message, listOf("header", "accountKeys", "recentBlockhash", "instructions"), { Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCMessage>(it) })
        checkRequired(
            message.getValue("header").jsonObject,
            listOf("numRequiredSignatures", "numReadonlySignedAccounts", "numReadonlyUnsignedAccounts"),
            { Kotlinx.DEFAULT.decodeFromJsonElement<MessageHeader>(it) },
        )
        checkRequired(
            message.getValue("instructions").jsonArray.first().jsonObject,
            listOf("programIdIndex", "accounts", "data"),
            { Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCInstruction>(it) },
        )
        val meta = legacy.getValue("meta").jsonObject
        checkRequired(meta, listOf("fee", "preBalances", "postBalances"), { Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCTransactionMeta>(it) })
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCTransactionMeta>(JsonObject(meta - "err")) }
        checkRequired(meta.getValue("loadedAddresses").jsonObject, listOf("writable", "readonly"), { Kotlinx.DEFAULT.decodeFromJsonElement<LoadedAddresses>(it) })
        val token = meta.getValue("postTokenBalances").jsonArray.first().jsonObject
        checkRequired(token, listOf("accountIndex", "mint", "uiTokenAmount"), { Kotlinx.DEFAULT.decodeFromJsonElement<TokenBalance>(it) })
        checkRequired(token.getValue("uiTokenAmount").jsonObject, listOf("amount", "decimals", "uiAmountString"), { Kotlinx.DEFAULT.decodeFromJsonElement<TokenAmount>(it) })
        checkRequired(meta.getValue("innerInstructions").jsonArray.first().jsonObject, listOf("index", "instructions"), { Kotlinx.DEFAULT.decodeFromJsonElement<InnerInstructions>(it) })
        val lookup = fixtures.getValue("4").getValue("transaction").jsonObject.getValue("message").jsonObject.getValue("addressTableLookups").jsonArray.first().jsonObject
        checkRequired(lookup, listOf("accountKey", "writableIndexes", "readonlyIndexes")) { Kotlinx.DEFAULT.decodeFromJsonElement<CompiledAddressLookupTable>(it) }
        val returnData = Kotlinx.DEFAULT.parseToJsonElement("""{"programId":"$address","data":["AQID","base64"]}""").jsonObject
        checkRequired(returnData, listOf("programId", "data"), { Kotlinx.DEFAULT.decodeFromJsonElement<ReturnData>(it) })
        val reward = Kotlinx.DEFAULT.parseToJsonElement("""{"pubkey":"$address","lamports":-1,"postBalance":0}""").jsonObject
        checkRequired(reward, listOf("pubkey", "lamports", "postBalance"), { Kotlinx.DEFAULT.decodeFromJsonElement<Reward>(it) })
    }

    test("known fields are fully decoded for legacy, v0, v1 and unsupported versions") {
        for (version in listOf("\"legacy\"", "0", "1", "2")) {
            val json = Kotlinx.DEFAULT.parseToJsonElement(
                """{
                    "slot":9007199254740993,"blockTime":123,"version":$version,"extra":1,
                    "transaction":{"signatures":["$signature"],"extra":2,"message":{
                        "header":{"numRequiredSignatures":1,"numReadonlySignedAccounts":0,"numReadonlyUnsignedAccounts":1,"extra":3},
                        "accountKeys":["$address"],"recentBlockhash":"$blockhash","extra":4,
                        "instructions":[{"programIdIndex":255,"accounts":[0,128,255],"data":"${Base58.encode(byteArrayOf(1, 2, 3))}","stackHeight":4294967295,"extra":5}],
                        "addressTableLookups":[{"accountKey":"$address","writableIndexes":[128],"readonlyIndexes":[255],"extra":6}]
                    }},
                    "meta":{"err":null,"fee":18446744073709551615,
                        "preBalances":[0,18446744073709551615],"postBalances":[9007199254740993],
                        "logMessages":["Program log: hello"],
                        "innerInstructions":[{"index":255,"extra":7,"instructions":[{"programIdIndex":128,"accounts":[],"data":"","stackHeight":null}]}],
                        "preTokenBalances":[{"accountIndex":128,"mint":"$address","owner":"$address","programId":"$address","extra":8,
                            "uiTokenAmount":{"amount":"18446744073709551615","decimals":9,"uiAmount":1.234567890123456789,"uiAmountString":"18446744073.709551615","extra":9}}],
                        "postTokenBalances":[],
                        "loadedAddresses":{"writable":["$address"],"readonly":[],"extra":10},
                        "returnData":{"programId":"$address","data":["AQID","base64"],"extra":11},
                        "rewards":[{"pubkey":"$address","lamports":-9223372036854775808,"postBalance":18446744073709551615,"rewardType":"futureReward","commission":255,"extra":12}],
                        "computeUnitsConsumed":18446744073709551615,"costUnits":9007199254740993,
                        "status":{"Ok":null},"extra":13}
                }""",
            )
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCTransaction>(json)
            tx.transaction.signatures shouldBe listOf(signature)
            val message = tx.transaction.message
            message.header.requiredSignatures shouldBe 1
            message.header.readonlySignedAccounts shouldBe 0
            message.header.readonlyUnsignedAccounts shouldBe 1
            message.accountKeys.single() shouldBe address

            message.recentBlockhash shouldBe blockhash
            val instruction = message.instructions.single()
            instruction.programIdIndex shouldBe 255 // No binary sanitization against the account list.
            instruction.accounts shouldBe listOf(0, 128, 255)
            instruction.data.toByteArray() shouldBe byteArrayOf(1, 2, 3)
            instruction.stackHeight shouldBe 4294967295L
            val lookup = message.addressTableLookups.single()
            lookup.key shouldBe address
            lookup.writableIndexes shouldBe listOf(128)
            lookup.readonlyIndexes shouldBe listOf(255)
            val meta = tx.meta!!
            meta.isSuccess shouldBe true
            meta.fee shouldBe BigInteger("18446744073709551615")
            meta.preBalances shouldBe listOf(BigInteger("0"), BigInteger("18446744073709551615"))
            meta.postBalances shouldBe listOf(BigInteger("9007199254740993"))
            meta.logMessages shouldBe listOf("Program log: hello")
            val inner = meta.innerInstructions!!.single()
            inner.index shouldBe 255
            (inner.instructions.single()).data.size shouldBe 0
            (inner.instructions.single()).stackHeight shouldBe null
            val token = meta.preTokenBalances!!.single()
            token.accountIndex shouldBe 128
            token.mint shouldBe address
            token.owner shouldBe address
            token.programId shouldBe address
            val amount = token.uiTokenAmount
            amount.amount shouldBe BigInteger("18446744073709551615")
            amount.decimals shouldBe 9
            amount.uiAmount shouldBe BigDecimal("1.234567890123456789")
            amount.uiAmountString shouldBe "18446744073.709551615"
            meta.postTokenBalances shouldBe emptyList()
            meta.loadedAddresses!!.writable shouldBe listOf(address)
            meta.loadedAddresses.readonly shouldBe emptyList()
            meta.returnData!!.programId shouldBe address
            meta.returnData.data.toByteArray() shouldBe byteArrayOf(1, 2, 3)
            val reward = meta.rewards!!.single()
            reward.pubkey shouldBe address
            reward.lamports shouldBe Long.MIN_VALUE
            reward.postBalance shouldBe BigInteger("18446744073709551615")
            reward.rewardType shouldBe RewardType("futureReward")
            reward.commission shouldBe 255
            meta.computeUnitsConsumed shouldBe BigInteger("18446744073709551615")
            meta.costUnits shouldBe BigInteger("9007199254740993")
            // the fixture numbers its extra fields in wire order; the header, the lookup table and the
            // addresses it resolves to mirror fixed parts of the message format, so theirs are dropped
            listOf(
                1 to tx.otherFields, 2 to tx.transaction.otherFields, 4 to message.otherFields,
                5 to instruction.otherFields, 7 to inner.otherFields, 8 to token.otherFields, 9 to amount.otherFields,
                11 to meta.returnData.otherFields, 12 to reward.otherFields,
                13 to meta.otherFields,
            ).forEach { (expected, fields) -> fields["extra"] shouldBe RawJson(expected.toString()) }
            listOf(
                Kotlinx.DEFAULT.encodeToJsonElement(message.header),
                Kotlinx.DEFAULT.encodeToJsonElement(lookup),
                Kotlinx.DEFAULT.encodeToJsonElement(meta.loadedAddresses!!),
            ).forEach { it.jsonObject.containsKey("extra") shouldBe false }
            meta.otherFields["status"] shouldBe RawJson("""{"Ok":null}""")
            roundtrip(json)
            Kotlinx.DEFAULT.parseToJsonElement(Kotlinx.DEFAULT.encodeToString(tx)).jsonObject.getValue("meta").jsonObject.getValue("preTokenBalances").jsonArray.first().jsonObject.getValue("uiTokenAmount").jsonObject.getValue("uiAmount").jsonPrimitive.content shouldBe "1.234567890123456789"
        }
    }

    test("genuinely nullable and optional fields remain nullable") {
        val nullMeta = JsonObject(legacy + mapOf("meta" to JsonNull, "blockTime" to JsonNull))
        decode(nullMeta).meta shouldBe null
        decode(nullMeta).blockTime shouldBe null
        roundtrip(nullMeta)
        val minimal = Kotlinx.DEFAULT.parseToJsonElement("""{"err":null,"fee":0,"preBalances":[],"postBalances":[],"logMessages":null}""").jsonObject
        val meta = Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCTransactionMeta>(minimal)
        meta.isSuccess shouldBe true
        meta.fee shouldBe BigInteger("0")
        meta.preBalances shouldBe emptyList()
        meta.logMessages shouldBe null
        meta.innerInstructions shouldBe null
        meta.loadedAddresses shouldBe null
        meta.returnData shouldBe null
        meta.computeUnitsConsumed shouldBe null
        val token = Kotlinx.DEFAULT.decodeFromJsonElement<TokenBalance>(Kotlinx.DEFAULT.parseToJsonElement("""{"accountIndex":0,"mint":"$address","uiTokenAmount":{"amount":"0","decimals":9,"uiAmount":null,"uiAmountString":"0"}}""").jsonObject)
        token.owner shouldBe null
        token.programId shouldBe null
        token.uiTokenAmount.uiAmount shouldBe null
    }

    test("unrequested parsed and binary transaction encodings are rejected") {
        shouldThrow<IllegalArgumentException> { decode(fixtures.getValue("2")) }
        shouldThrow<IllegalArgumentException> {
            decode(JsonObject(legacy + ("transaction" to Kotlinx.DEFAULT.parseToJsonElement("""["AQID","base64"]"""))))
        }
    }

    test("malformed quantities and signatures fail rather than falling back to null") {
        for (slot in listOf("-1", "18446744073709551616", "\"" + "1" + "\"")) {
            shouldThrow<IllegalArgumentException> { decode(JsonObject(legacy + ("slot" to Kotlinx.DEFAULT.parseToJsonElement(slot)))) }
        }
        val payload = legacy.getValue("transaction").jsonObject
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCTransactionData>(JsonObject(payload + ("signatures" to Kotlinx.DEFAULT.parseToJsonElement("""["invalid"]""")))) }
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCInstruction>(Kotlinx.DEFAULT.parseToJsonElement("""{"programIdIndex":1,"accounts":[-1],"data":""}""")) }
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromJsonElement<SolanaRPCInstruction>(Kotlinx.DEFAULT.parseToJsonElement("""{"programIdIndex":1,"accounts":[],"data":"0"}""")) }
        shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromJsonElement<InstructionError>(Kotlinx.DEFAULT.parseToJsonElement("""{"Custom":4294967296}""")) }
    }

    test("JSON serialization preserves unknown numeric literals without floating point conversion") {
        val numbers = Kotlinx.DEFAULT.parseToJsonElement("""[1.234567890123456789,123456789012345678901234567890123456789,1e999,true,false,null,"1e999"]""")
        val tx = decode(JsonObject(legacy + ("future" to numbers)))
        tx.otherFields["future"] shouldBe RawJson(numbers.toString())
        Kotlinx.DEFAULT.parseToJsonElement(Kotlinx.DEFAULT.encodeToString(tx)).jsonObject["future"] shouldBe numbers
        val nested = tx.copy(transaction = tx.transaction.copy(message = tx.transaction.message.copy(otherFields = mapOf("future" to RawJson(numbers.toString())))))
        Kotlinx.DEFAULT.parseToJsonElement(Kotlinx.DEFAULT.encodeToString(nested)).jsonObject.getValue("transaction").jsonObject.getValue("message").jsonObject["future"] shouldBe numbers
    }
})
