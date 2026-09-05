package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.crypto.Base58
import io.ethers.solana.types.Blockhash
import io.ethers.solana.types.Programs
import io.ethers.solana.types.RPCTransaction
import io.ethers.solana.types.Signature
import io.ethers.solana.types.transaction.SolanaTxType
import io.github.artificialpb.bignum.BigDecimal
import io.github.artificialpb.bignum.BigInteger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class RPCTransactionTest : FunSpec({
    val signature = Signature(ByteArray(64)) // Deliberately not a valid transaction signature.
    val address = Programs.SYSTEM
    val blockhash = Blockhash(ByteArray(32) { 3 })

    test("legacy, v0 and unsupported versions preserve raw payloads and metadata") {
        for ((version, type) in listOf(
            "\"legacy\"" to SolanaTxType.Legacy,
            "0" to SolanaTxType.V0,
            "1" to SolanaTxType.Unsupported(1),
            "127" to SolanaTxType.Unsupported(127),
            "255" to SolanaTxType.Unsupported(255),
        )) {
            // No binary decoder or signature verifier should run, even for a recognized version.
            val json = Kotlinx.DEFAULT.parseToJsonElement(
                """{"slot":18446744073709551615,"blockTime":-1,"version":$version,
                    "transaction":{"signatures":["$signature"],"message":{"futureLayout":[1,{"new":true}]}},
                    "meta":{"err":{"FutureError":[7]},"futureCounter":18446744073709551615},
                    "futureField":{"nested":null}}""",
            ).jsonObject
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
            tx.type shouldBe type
            tx.slot shouldBe BigInteger("18446744073709551615")
            tx.blockTime shouldBe -1L
            tx.transaction.raw shouldBe json.getValue("transaction")
            tx.transaction.signatures shouldBe listOf(signature)
            tx.transaction.message!!.otherFields.keys shouldBe setOf("futureLayout")
            tx.meta!!.raw shouldBe json.getValue("meta")
            tx.meta.err!!.kind shouldBe "FutureError"
            tx.otherFields shouldBe mapOf("futureField" to json.getValue("futureField"))
            tx.raw shouldBe json
            Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
            Kotlinx.DEFAULT.parseToJsonElement(Kotlinx.DEFAULT.encodeToString(tx)) shouldBe json
        }
    }

    test("omitted, null and unrecognized versions are not silently classified as legacy") {
        for (version in listOf("", ",\"version\":null", ",\"version\":\"future\"", ",\"version\":{\"future\":true}", ",\"version\":4294967296")) {
            val json = Kotlinx.DEFAULT.parseToJsonElement("""{"slot":1,"transaction":{},"meta":null$version}""")
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
            tx.type shouldBe null
            tx.meta shouldBe null
            tx.blockTime shouldBe null
            Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
        }
    }

    test("encoded and future transaction payload representations remain readable") {
        for (payload in listOf("[\"AQID\",\"base64\"]", "\"opaque\"", "{\"newFormat\":true}")) {
            val json = Kotlinx.DEFAULT.parseToJsonElement("""{"slot":1,"blockTime":null,"transaction":$payload,"version":1}""")
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
            tx.type shouldBe SolanaTxType.Unsupported(1)
            tx.raw["blockTime"] shouldBe JsonNull
            Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
        }
    }

    test("malformed slot quantities still fail instead of being treated as unsupported versions") {
        for (slot in listOf("-1", "18446744073709551616", "\"1\"")) {
            shouldThrow<IllegalArgumentException> {
                Kotlinx.DEFAULT.decodeFromString<RPCTransaction>("""{"slot":$slot,"transaction":{},"version":1}""")
            }
        }
    }

    test("type support is independent of RPC readability") {
        SolanaTxType.Legacy.version shouldBe null
        SolanaTxType.Legacy.isSupported shouldBe true
        SolanaTxType.fromVersion(0) shouldBe SolanaTxType.V0
        SolanaTxType.V0.isSupported shouldBe true
        SolanaTxType.fromVersion(1) shouldBe SolanaTxType.Unsupported(1)
        SolanaTxType.Unsupported(1).version shouldBe 1
        SolanaTxType.Unsupported(1).isSupported shouldBe false
        shouldThrow<IllegalArgumentException> { SolanaTxType.fromVersion(-1) }
        shouldThrow<IllegalArgumentException> { SolanaTxType.Unsupported(0) }
    }

    test("known fields are fully decoded for legacy, v0 and unsupported versions") {
        for (version in listOf("\"legacy\"", "0", "1")) {
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
            val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
            tx.transaction.signatures shouldBe listOf(signature)
            val message = tx.transaction.message!!
            message.header!!.numRequiredSignatures shouldBe 1
            message.header.numReadonlySignedAccounts shouldBe 0
            message.header.numReadonlyUnsignedAccounts shouldBe 1
            message.accountKeys!!.single().address shouldBe address
            message.accountKeys.single().signer shouldBe null
            message.recentBlockhash shouldBe blockhash
            val instruction = message.instructions!!.single()
            instruction.programIdIndex shouldBe 255 // No binary sanitization against the account list.
            instruction.accountIndices shouldBe listOf(0, 128, 255)
            instruction.accounts shouldBe null
            instruction.data!!.toByteArray() shouldBe byteArrayOf(1, 2, 3)
            instruction.stackHeight shouldBe 4294967295L
            val lookup = message.addressTableLookups!!.single()
            lookup.accountKey shouldBe address
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
            inner.instructions!!.single().data!!.size shouldBe 0
            inner.instructions.single().stackHeight shouldBe null
            val token = meta.preTokenBalances!!.single()
            token.accountIndex shouldBe 128
            token.mint shouldBe address
            token.owner shouldBe address
            token.programId shouldBe address
            val amount = token.uiTokenAmount!!
            amount.amount shouldBe BigInteger("18446744073709551615")
            amount.decimals shouldBe 9
            amount.uiAmount shouldBe BigDecimal("1.234567890123456789")
            amount.uiAmountString shouldBe "18446744073.709551615"
            meta.postTokenBalances shouldBe emptyList()
            meta.loadedAddresses!!.writable shouldBe listOf(address)
            meta.loadedAddresses.readonly shouldBe emptyList()
            meta.returnData!!.programId shouldBe address
            meta.returnData.data!!.toByteArray() shouldBe byteArrayOf(1, 2, 3)
            meta.returnData.encoding shouldBe "base64"
            val reward = meta.rewards!!.single()
            reward.pubkey shouldBe address
            reward.lamports shouldBe Long.MIN_VALUE
            reward.postBalance shouldBe BigInteger("18446744073709551615")
            reward.rewardType shouldBe "futureReward"
            reward.commission shouldBe 255
            meta.computeUnitsConsumed shouldBe BigInteger("18446744073709551615")
            meta.costUnits shouldBe BigInteger("9007199254740993")
            listOf(
                tx.otherFields, tx.transaction.otherFields, message.header.otherFields, message.otherFields,
                instruction.otherFields, lookup.otherFields, inner.otherFields, token.otherFields, amount.otherFields,
                meta.loadedAddresses.otherFields, meta.returnData.otherFields, reward.otherFields, meta.otherFields,
            ).forEachIndexed { index, fields -> fields["extra"] shouldBe JsonPrimitive(index + 1) }
            meta.otherFields["status"] shouldBe JsonObject(mapOf("Ok" to JsonNull))
            Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
        }
    }

    test("jsonParsed accounts, parsed instructions and partially decoded instructions retain their variants") {
        val json = Kotlinx.DEFAULT.parseToJsonElement(
            """{"slot":1,"version":1,"transaction":{"signatures":["$signature"],"message":{
                "accountKeys":[{"pubkey":"$address","signer":true,"writable":false,"source":"futureSource","extra":1}],
                "recentBlockhash":"$blockhash","instructions":[
                    {"program":"spl-token","programId":"$address","parsed":{"type":"transfer","info":{"futureField":7}},"stackHeight":2},
                    {"programId":"$address","accounts":["$address"],"data":"1","stackHeight":null},
                    {"programId":"$address","accounts":[],"data":""},
                    {"futureInstruction":true,"stackHeight":3}
                ]}},"meta":{"err":{"InstructionError":[255,{"Custom":4294967295}]}}}""",
        )
        val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
        val message = tx.transaction.message!!
        message.header shouldBe null
        val account = message.accountKeys!!.single()
        account.address shouldBe address
        account.signer shouldBe true
        account.writable shouldBe false
        account.source shouldBe "futureSource"
        account.otherFields shouldBe mapOf("extra" to JsonPrimitive(1))
        val instructions = message.instructions!!
        instructions[0].program shouldBe "spl-token"
        instructions[0].programId shouldBe address
        instructions[0].parsed!!.jsonObject["type"] shouldBe JsonPrimitive("transfer")
        instructions[0].data shouldBe null
        instructions[0].stackHeight shouldBe 2L
        instructions[1].accounts shouldBe listOf(address)
        instructions[1].accountIndices shouldBe null
        instructions[1].data!!.toByteArray() shouldBe byteArrayOf(0)
        instructions[2].accounts shouldBe emptyList()
        instructions[2].accountIndices shouldBe null
        instructions[3].otherFields shouldBe mapOf("futureInstruction" to JsonPrimitive(true))
        val err = tx.meta!!.err!!
        tx.meta.isSuccess shouldBe false
        err.kind shouldBe "InstructionError"
        err.instructionIndex shouldBe 255
        err.instructionError!!.kind shouldBe "Custom"
        err.instructionError.customCode shouldBe 4294967295L
        Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
    }

    test("null and omitted metadata fields stay distinct from empty lists and zero quantities") {
        val json = Kotlinx.DEFAULT.parseToJsonElement("""{"slot":1,"transaction":{},"meta":{"fee":0,"preBalances":[],"logMessages":null,"returnData":null}}""")
        val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
        val meta = tx.meta!!
        meta.isSuccess shouldBe null
        meta.fee shouldBe BigInteger("0")
        meta.preBalances shouldBe emptyList()
        meta.postBalances shouldBe null
        meta.logMessages shouldBe null
        meta.returnData shouldBe null
        Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
    }

    test("encoded payloads and unknown nested layouts do not require a known binary version") {
        for ((encoding, encoded) in listOf("base64" to "AQID", "base58" to Base58.encode(byteArrayOf(1, 2, 3)))) {
            val tx = Kotlinx.DEFAULT.decodeFromString<RPCTransaction>("""{"slot":1,"version":1,"transaction":["$encoded","$encoding"]}""")
            tx.transaction.encoding shouldBe encoding
            tx.transaction.data!!.toByteArray() shouldBe byteArrayOf(1, 2, 3)
            tx.transaction.message shouldBe null
        }
        val unknown = Kotlinx.DEFAULT.parseToJsonElement("""{"slot":1,"version":1,"transaction":[{"future":true},"newEncoding"],"meta":{"fee":5,"returnData":{"programId":"$address","data":["opaque","future"]},"loadedAddresses":{"future":true}}}""")
        val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(unknown)
        tx.transaction.data shouldBe null
        tx.meta!!.fee shouldBe BigInteger("5")
        tx.meta.returnData!!.programId shouldBe address
        tx.meta.returnData.encoding shouldBe "future"
        tx.meta.returnData.data shouldBe null
        tx.meta.loadedAddresses!!.writable shouldBe null
        tx.meta.loadedAddresses.otherFields shouldBe mapOf("future" to JsonPrimitive(true))
        Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe unknown
    }

    test("malformed recognized fields fail instead of discarding typed data") {
        for (payload in listOf(
            """"transaction":{"signatures":["invalid"]}""",
            """"transaction":{"message":{"accountKeys":["invalid"]}}""",
            """"transaction":{"message":{"header":{"numRequiredSignatures":256}}}""",
            """"transaction":{"message":{"instructions":[{"programIdIndex":1,"accounts":[-1]}]}}""",
            """"transaction":{"message":{"instructions":[{"data":"0"}]}}""",
            """"transaction":{},"meta":{"fee":-1}""",
            """"transaction":{},"meta":{"postBalances":[18446744073709551616]}""",
            """"transaction":{},"meta":{"rewards":[{"lamports":9223372036854775808}]}""",
            """"transaction":{},"meta":{"preTokenBalances":[{"uiTokenAmount":{"amount":"-1"}}]}""",
            """"transaction":{},"meta":{"err":{"InstructionError":[0,{"Custom":4294967296}]}}""",
        )) {
            shouldThrow<IllegalArgumentException> { Kotlinx.DEFAULT.decodeFromString<RPCTransaction>("""{"slot":1,"version":1,$payload}""") }
        }
    }

    test("JSON serialization preserves unknown numeric literals without floating point conversion") {
        val json = Kotlinx.DEFAULT.parseToJsonElement("""{"slot":1,"transaction":{},"future":[1.234567890123456789,123456789012345678901234567890123456789,1e999,true,false,null,"1e999"]}""")
        val tx = Kotlinx.DEFAULT.decodeFromJsonElement<RPCTransaction>(json)
        Kotlinx.DEFAULT.encodeToJsonElement(tx) shouldBe json
        Kotlinx.DEFAULT.parseToJsonElement(Kotlinx.DEFAULT.encodeToString(tx)) shouldBe json
    }
})
