package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.core.isFailure
import io.ethers.providers.HttpClient
import io.ethers.providers.RpcClientConfig
import io.ethers.providers.RpcError
import io.ethers.providers.SubscriptionDescriptor
import io.ethers.solana.instruction.Instruction
import io.ethers.solana.instruction.SystemProgram
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.providers.middleware.SolanaApi
import io.ethers.solana.signers.KeypairSigner
import io.ethers.solana.types.AccountMeta
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.SolanaBlockhash
import io.ethers.solana.types.SolanaSignature
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.ContextValue
import io.ethers.solana.types.rpc.RpcContext
import io.ethers.solana.types.rpc.SolanaNodeHealth
import io.ethers.solana.types.rpc.SolanaNodeIdentity
import io.ethers.solana.types.rpc.SolanaSendConfig
import io.ethers.solana.types.transaction.AddressLookupTableAccount
import io.ethers.solana.types.transaction.SolanaTransactionConfig
import io.ethers.solana.types.transaction.SolanaTransactionRequest
import io.ethers.solana.types.transaction.SolanaTxLegacy
import io.ethers.solana.types.transaction.SolanaTxType
import io.ethers.solana.types.transaction.SolanaTxV0
import io.ethers.solana.types.transaction.SolanaTxV1
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import io.ktor.client.HttpClient as KtorHttpClient

class SolanaProviderTest : FunSpec({
    val address = Programs.SYSTEM
    val signature = SolanaSignature(ByteArray(64) { 1 })
    val blockhash = SolanaBlockhash(ByteArray(32))
    val account = """{"data":["AQID","base64"],"executable":false,"lamports":18446744073709551615,"owner":"$address","rentEpoch":18446744073709551615}"""
    fun contextual(value: String) = """{"context":{"slot":9007199254740993,"apiVersion":"3.0.0"},"value":$value}"""
    lateinit var provider: SolanaProvider
    lateinit var ktor: KtorHttpClient
    var response = "null"
    var error: String? = null
    var status = HttpStatusCode.OK
    val requests = mutableListOf<JsonObject>()
    beforeEach {
        requests.clear()
        error = null
        status = HttpStatusCode.OK
        ktor = KtorHttpClient(
            MockEngine { request ->
                val body = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject
                requests.add(body)
                val payload = error?.let { "\"error\":$it" } ?: "\"result\":$response"
                respond("""{"jsonrpc":"2.0","id":${body.getValue("id")},$payload}""", status, headersOf("Content-Type", "application/json"))
            },
        )
        provider = SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    }
    afterEach {
        provider.close()
        ktor.close()
    }
    fun assertRequest(method: String, expectedParams: String) {
        requests.last().getValue("method").jsonPrimitive.content shouldBe method
        requests.last().getValue("params") shouldBe Kotlinx.DEFAULT.parseToJsonElement(expectedParams)
    }

    test("newly added read methods send the parameters the node expects") {
        response = "12345"
        provider.getSlot().send().unwrap() shouldBe bigIntegerOf(12345)
        assertRequest("getSlot", """[{"commitment":"confirmed"}]""")
        provider.getBlockHeight().send().unwrap() shouldBe bigIntegerOf(12345)
        assertRequest("getBlockHeight", """[{"commitment":"confirmed"}]""")

        response = "[1,2,3]"
        provider.getBlocks(bigIntegerOf(1), bigIntegerOf(3)).send().unwrap() shouldBe listOf(bigIntegerOf(1), bigIntegerOf(2), bigIntegerOf(3))
        assertRequest("getBlocks", """[1,3,{"commitment":"confirmed"}]""")

        // a skipped slot is null rather than an error
        response = "null"
        provider.getBlock(bigIntegerOf(5)).send().unwrap() shouldBe null
        assertRequest("getBlock", """[5,{"commitment":"confirmed","encoding":"json","transactionDetails":"full","maxSupportedTransactionVersion":255,"rewards":false}]""")
    }

    test("signature statuses keep their position, with null for a signature the node never saw") {
        response = contextual("""[null,{"slot":9,"confirmations":null,"err":null,"confirmationStatus":"finalized"}]""")
        val statuses = provider.getSignatureStatuses(listOf(signature, signature), true).send().unwrap().value

        statuses.size shouldBe 2
        statuses[0] shouldBe null
        statuses[1]!!.confirmationStatus shouldBe Commitment.FINALIZED
        // null confirmations means finalized, which satisfies every commitment
        statuses[1]!!.isAtLeast(Commitment.FINALIZED) shouldBe true
        assertRequest("getSignatureStatuses", """[["$signature","$signature"],{"searchTransactionHistory":true}]""")
    }

    test("program and token account queries ask for base64 and carry their filters") {
        response = """[{"pubkey":"$address","account":$account}]"""
        val accounts = provider.getProgramAccounts(address).send().unwrap()
        accounts.single().pubkey shouldBe address
        accounts.single().account.lamports shouldBe BigInteger("18446744073709551615")
        assertRequest("getProgramAccounts", """["$address",{"commitment":"confirmed","encoding":"base64"}]""")

        response = contextual("""[{"pubkey":"$address","account":$account}]""")
        provider.getTokenAccountsByOwner(address, Programs.TOKEN).send().unwrap().value.size shouldBe 1
        assertRequest("getTokenAccountsByOwner", """["$address",{"mint":"${Programs.TOKEN}"},{"commitment":"confirmed","encoding":"base64"}]""")

        provider.getTokenAccountsByOwnerForProgram(address, Programs.TOKEN_2022).send().unwrap().value.size shouldBe 1
        assertRequest("getTokenAccountsByOwner", """["$address",{"programId":"${Programs.TOKEN_2022}"},{"commitment":"confirmed","encoding":"base64"}]""")
    }

    test("sending exposes the options a caller needs, and omits the ones left unset") {
        response = "\"$signature\""
        provider.sendTransaction(ByteArray(1)).send().unwrap().signature shouldBe signature
        assertRequest("sendTransaction", """["AA==",{"encoding":"base64","preflightCommitment":"confirmed"}]""")

        provider.sendTransaction(ByteArray(1), SolanaSendConfig(skipPreflight = true, maxRetries = 3, minContextSlot = bigIntegerOf(7))).send().unwrap()
        assertRequest("sendTransaction", """["AA==",{"encoding":"base64","preflightCommitment":"confirmed","skipPreflight":true,"maxRetries":3,"minContextSlot":7}]""")
    }

    test("standalone API implementations inherit RPC conveniences and commitment forwarding") {
        val api = object : SolanaApi {
            override val client = provider.client
            override val defaultCommitment = Commitment.CONFIRMED
        }
        response = contextual("1")
        api.getBalance(address).send().unwrap().value shouldBe bigIntegerOf(1)
        assertRequest("getBalance", """["$address",{"commitment":"confirmed"}]""")
        response = "123"
        api.getMinimumBalanceForRentExemption(165L).send().unwrap() shouldBe bigIntegerOf(123)
        assertRequest("getMinimumBalanceForRentExemption", """[165,{"commitment":"confirmed"}]""")
        api.getMinimumBalanceForRentExemption(165L, Commitment.FINALIZED).send().unwrap() shouldBe bigIntegerOf(123)
        assertRequest("getMinimumBalanceForRentExemption", """[165,{"commitment":"finalized"}]""")
        response = "\"$signature\""
        api.requestAirdrop(address, 1L).send().unwrap() shouldBe signature
        assertRequest("requestAirdrop", """["$address",1,{"commitment":"confirmed"}]""")
        api.requestAirdrop(address, 1L, Commitment.FINALIZED).send().unwrap() shouldBe signature
        assertRequest("requestAirdrop", """["$address",1,{"commitment":"finalized"}]""")
        response = "null"
        api.getTransaction(signature).send().unwrap() shouldBe null
        assertRequest("getTransaction", """["$signature",{"commitment":"confirmed","encoding":"json","maxSupportedTransactionVersion":255}]""")
        api.getTransaction(signature, Commitment.FINALIZED).send().unwrap() shouldBe null
        assertRequest("getTransaction", """["$signature",{"commitment":"finalized","encoding":"json","maxSupportedTransactionVersion":255}]""")
        api.getTransaction(signature, 1).send().unwrap() shouldBe null
        assertRequest("getTransaction", """["$signature",{"commitment":"confirmed","encoding":"json","maxSupportedTransactionVersion":1}]""")
        response = "[]"
        api.getRecentPrioritizationFees().send().unwrap() shouldBe emptyList()
        assertRequest("getRecentPrioritizationFees", "[[]]")
        api.getSignaturesForAddress(address).send().unwrap() shouldBe emptyList()
        assertRequest("getSignaturesForAddress", """["$address",{"limit":1000,"commitment":"confirmed"}]""")
        api.getSignaturesForAddress(address, 10).send().unwrap() shouldBe emptyList()
        assertRequest("getSignaturesForAddress", """["$address",{"limit":10,"commitment":"confirmed"}]""")
        api.getSignaturesForAddress(address, Commitment.FINALIZED).send().unwrap() shouldBe emptyList()
        assertRequest("getSignaturesForAddress", """["$address",{"limit":1000,"commitment":"finalized"}]""")
        api.getSignaturesForAddress(address, 10, Commitment.FINALIZED).send().unwrap() shouldBe emptyList()
        assertRequest("getSignaturesForAddress", """["$address",{"limit":10,"commitment":"finalized"}]""")
        api.getSignaturesForAddress(address, 10, Commitment.FINALIZED, signature).send().unwrap() shouldBe emptyList()
        assertRequest("getSignaturesForAddress", """["$address",{"limit":10,"commitment":"finalized","before":"$signature"}]""")
        api.getSignaturesForAddress(address, 10, signature, signature).send().unwrap() shouldBe emptyList()
        assertRequest("getSignaturesForAddress", """["$address",{"limit":10,"commitment":"confirmed","before":"$signature","until":"$signature"}]""")
        api.defaultCommitment shouldBe Commitment.CONFIRMED
    }

    test("request commitment overrides do not change the provider default or other requests") {
        response = contextual("1")
        val overridden = provider.getBalance(address, commitment = Commitment.FINALIZED)
        val inherited = provider.getBalance(address)
        provider.defaultCommitment shouldBe Commitment.CONFIRMED
        val expected = ContextValue(RpcContext(BigInteger("9007199254740993"), "3.0.0"), bigIntegerOf(1))
        overridden.send().unwrap() shouldBe expected
        assertRequest("getBalance", """["$address",{"commitment":"finalized"}]""")
        inherited.send().unwrap() shouldBe expected
        assertRequest("getBalance", """["$address",{"commitment":"confirmed"}]""")
        provider.defaultCommitment shouldBe Commitment.CONFIRMED
    }

    test("balance queries preserve context and the full u64 range") {
        response = contextual("18446744073709551615")
        val expectedContext = RpcContext(BigInteger("9007199254740993"), "3.0.0")
        provider.getBalance(address).send().unwrap() shouldBe ContextValue(expectedContext, BigInteger("18446744073709551615"))
        assertRequest("getBalance", """["$address",{"commitment":"confirmed"}]""")
        response = contextual("0")
        provider.getBalance(address, Commitment.FINALIZED).send().unwrap() shouldBe ContextValue(expectedContext, bigIntegerOf(0))
        assertRequest("getBalance", """["$address",{"commitment":"finalized"}]""")
        response = """{"context":{"slot":42},"value":0}"""
        provider.getBalance(address).send().unwrap() shouldBe ContextValue(RpcContext(bigIntegerOf(42)), bigIntegerOf(0))
    }

    test("account queries preserve u64, context, missing accounts and binary data") {
        response = contextual(account)
        val info = provider.getAccountInfo(address).send().unwrap().value!!
        info.data.toByteArray() shouldBe byteArrayOf(1, 2, 3)
        info.space shouldBe bigIntegerOf(3)
        info.lamports shouldBe BigInteger("18446744073709551615")
        assertRequest("getAccountInfo", """["$address",{"commitment":"confirmed","encoding":"base64"}]""")
        response = contextual("null")
        provider.getAccountInfo(address).send().unwrap().value shouldBe null
        response = contextual("[$account,null]")
        val multiple = provider.getMultipleAccounts(listOf(address, address)).send().unwrap().value
        multiple.size shouldBe 2
        multiple[0] shouldBe info
        multiple[1] shouldBe null
        assertRequest("getMultipleAccounts", """[["$address","$address"],{"commitment":"confirmed","encoding":"base64"}]""")
    }

    test("token, blockhash and fee queries decode their contextual results") {
        response = contextual("""{"amount":"18446744073709551615","decimals":9,"uiAmount":null,"uiAmountString":"18446744073.709551615"}""")
        provider.getTokenAccountBalance(address).send().unwrap().value.amount shouldBe BigInteger("18446744073709551615")
        assertRequest("getTokenAccountBalance", """["$address",{"commitment":"confirmed"}]""")
        provider.getTokenSupply(address).send().unwrap().value.decimals shouldBe 9
        assertRequest("getTokenSupply", """["$address",{"commitment":"confirmed"}]""")
        response = contextual("""{"blockhash":"$blockhash","lastValidBlockHeight":1000}""")
        provider.getLatestBlockhash().send().unwrap().value.lastValidBlockHeight shouldBe bigIntegerOf(1000)
        assertRequest("getLatestBlockhash", """[{"commitment":"confirmed"}]""")
        response = contextual("true")
        provider.isBlockhashValid(blockhash).send().unwrap().value shouldBe true
        assertRequest("isBlockhashValid", """["$blockhash",{"commitment":"confirmed"}]""")
        response = contextual("null")
        provider.getFeeForMessage(byteArrayOf(1, 2)).send().unwrap().value shouldBe null
        assertRequest("getFeeForMessage", """["AQI=",{"commitment":"confirmed"}]""")
        response = contextual("5000")
        provider.getFeeForMessage(byteArrayOf()).send().unwrap().value shouldBe bigIntegerOf(5000)
    }

    test("cluster, rent and prioritization queries use decimal values") {
        response = "\"ok\""
        provider.getHealth().send().unwrap() shouldBe SolanaNodeHealth.OK
        assertRequest("getHealth", "[]")
        response = """{"absoluteSlot":1,"blockHeight":2,"epoch":3,"slotIndex":4,"slotsInEpoch":5,"transactionCount":null}"""
        provider.getEpochInfo().send().unwrap().slotsInEpoch shouldBe bigIntegerOf(5)
        assertRequest("getEpochInfo", """[{"commitment":"confirmed"}]""")
        response = """{"identity":"$address"}"""
        val identity = provider.getIdentity().send().unwrap()
        identity shouldBe SolanaNodeIdentity(address)
        Kotlinx.DEFAULT.encodeToString(SolanaNodeIdentity.serializer(), identity) shouldBe response
        assertRequest("getIdentity", "[]")
        response = """{"solana-core":"3.0.0","feature-set":42}"""
        provider.getVersion().send().unwrap().solanaCore shouldBe "3.0.0"
        assertRequest("getVersion", "[]")
        response = "123"
        provider.getTransactionCount().send().unwrap() shouldBe bigIntegerOf(123)
        assertRequest("getTransactionCount", """[{"commitment":"confirmed"}]""")
        provider.getMinimumBalanceForRentExemption(165).send().unwrap() shouldBe bigIntegerOf(123)
        assertRequest("getMinimumBalanceForRentExemption", """[165,{"commitment":"confirmed"}]""")
        response = """[{"slot":42,"prioritizationFee":500}]"""
        provider.getRecentPrioritizationFees(listOf(address)).send().unwrap().single().prioritizationFee shouldBe bigIntegerOf(500)
        assertRequest("getRecentPrioritizationFees", """[["$address"]]""")
    }

    test("airdrop, signature history, submission and simulation") {
        response = "\"$signature\""
        provider.requestAirdrop(address, BigInteger("18446744073709551615")).send().unwrap() shouldBe signature
        assertRequest("requestAirdrop", """["$address",18446744073709551615,{"commitment":"confirmed"}]""")
        val signer = KeypairSigner.fromSeed(ByteArray(32))
        val transaction = SolanaTxV0.compile(signer.publicKey, blockhash, SystemProgram.transfer(signer.publicKey, address, 1L)).unwrap().sign(signer)
        provider.sendTransaction(transaction).send().unwrap().signature shouldBe signature
        assertRequest("sendTransaction", """["${transaction.toBase64()}",{"encoding":"base64","preflightCommitment":"confirmed"}]""")
        provider.sendTransaction(transaction, preflightCommitment = Commitment.FINALIZED).send().unwrap().signature shouldBe signature
        assertRequest("sendTransaction", """["${transaction.toBase64()}",{"encoding":"base64","preflightCommitment":"finalized"}]""")
        provider.sendTransaction(transaction).send().unwrap().signature shouldBe signature
        assertRequest("sendTransaction", """["${transaction.toBase64()}",{"encoding":"base64","preflightCommitment":"confirmed"}]""")
        provider.defaultCommitment shouldBe Commitment.CONFIRMED
        response = contextual("""{"err":{"InstructionError":[0,"InvalidArgument"]},"logs":["failed"],"unitsConsumed":9007199254740993}""")
        val simulation = provider.simulateTransaction(transaction).send().unwrap().value
        simulation.isSuccess shouldBe false
        simulation.err shouldBe io.ethers.solana.types.rpc.TransactionError.InstructionFailure(0, io.ethers.solana.types.rpc.InstructionError.InvalidArgument)
        assertRequest("simulateTransaction", """["${transaction.toBase64()}",{"commitment":"confirmed","encoding":"base64"}]""")
        response = contextual("""{"err":null,"logs":null}""")
        provider.simulateTransaction(transaction).send().unwrap().value.isSuccess shouldBe true
        response = """[{"signature":"$signature","slot":42,"err":null,"memo":null,"blockTime":null,"confirmationStatus":"finalized"}]"""
        provider.getSignaturesForAddress(address, 10, Commitment.FINALIZED, before = signature).send().unwrap().single().isError shouldBe false
        assertRequest("getSignaturesForAddress", """["$address",{"limit":10,"commitment":"finalized","before":"$signature"}]""")
    }

    test("invalid RPC parameters are forwarded verbatim and node errors remain results") {
        error = """{"code":-32602,"message":"invalid params","data":{"source":"node"}}"""
        for (value in listOf(BigInteger("-1"), BigInteger("18446744073709551616"))) {
            provider.requestAirdrop(address, value).send().unwrapError().code shouldBe -32602
            assertRequest("requestAirdrop", """["$address",$value,{"commitment":"confirmed"}]""")
            provider.getMinimumBalanceForRentExemption(value).send().unwrapError().code shouldBe -32602
            assertRequest("getMinimumBalanceForRentExemption", """[$value,{"commitment":"confirmed"}]""")
        }
        for (limit in listOf(0, 1001)) {
            provider.getSignaturesForAddress(address, limit, Commitment.PROCESSED).send().unwrapError().code shouldBe -32602
            assertRequest("getSignaturesForAddress", """["$address",{"commitment":"processed","limit":$limit}]""")
        }
        for (version in listOf(-1, 256)) {
            provider.getTransaction(signature, Commitment.PROCESSED, version).send().unwrapError().code shouldBe -32602
            assertRequest("getTransaction", """["$signature",{"commitment":"processed","encoding":"json","maxSupportedTransactionVersion":$version}]""")
        }
        provider.getMultipleAccounts(List(101) { address }).send().unwrapError().code shouldBe -32602
        assertRequest("getMultipleAccounts", """[${List(101) { "\"$address\"" }.joinToString(",", "[", "]")},{"commitment":"confirmed","encoding":"base64"}]""")
        val result = provider.getRecentPrioritizationFees(List(129) { address }).send()
        result.unwrapError().code shouldBe -32602
        result.unwrapError().message shouldBe "invalid params"
        result.unwrapError().data.toString() shouldBe """{"source":"node"}"""
        assertRequest("getRecentPrioritizationFees", """[${List(129) { "\"$address\"" }.joinToString(",", "[", "]")}]""")
    }

    test("RPC errors retain structured data and HTTP does not support subscriptions") {
        error = """{"code":-32002,"message":"Transaction simulation failed","data":{"logs":["failed"]}}"""
        status = HttpStatusCode.BadRequest
        val result = provider.getHealth().send()
        result.isFailure() shouldBe true
        result.unwrapError().code shouldBe -32002
        result.unwrapError().data.toString() shouldBe """{"logs":["failed"]}"""
        provider.subscribeSlot().send().unwrapError().code shouldBe RpcError.CODE_METHOD_NOT_FOUND
    }

    test("simulation accepts every signing state but raw submission rejects incomplete envelopes") {
        val alice = KeypairSigner.fromSeed(ByteArray(32) { 1 })
        val bob = KeypairSigner.fromSeed(ByteArray(32) { 2 })
        val tx = SolanaTxV0.compile(alice.publicKey, blockhash, Instruction(Programs.SYSTEM, listOf(AccountMeta.signer(alice.publicKey), AccountMeta.signer(bob.publicKey)), byteArrayOf())).unwrap()
        val builder = tx.signingBuilder().sign(bob)
        val partial = builder.serializePartial()
        val signed = builder.sign(alice).build()
        response = contextual("""{"err":null,"logs":null}""")
        for (state in listOf(tx, signed)) {
            provider.simulateTransaction(state).send().unwrap().value.isSuccess shouldBe true
            assertRequest("simulateTransaction", """["${Base64.encode(state.serializeForSimulation())}",{"commitment":"confirmed","encoding":"base64"}]""")
        }
        provider.simulateTransaction(partial).send().unwrap().value.isSuccess shouldBe true
        assertRequest("simulateTransaction", """["${Base64.encode(partial)}",{"commitment":"confirmed","encoding":"base64"}]""")
        response = contextual("5000")
        for (state in listOf(tx, signed)) {
            provider.getFeeForMessage(state).send().unwrap().value shouldBe bigIntegerOf(5000)
            assertRequest("getFeeForMessage", """["${Base64.encode(tx.serializeMessage())}",{"commitment":"confirmed"}]""")
        }
        error = """{"code":-32003,"message":"Transaction signature verification failure"}"""
        for (wire in listOf(tx.serializeForSimulation(), partial)) {
            provider.sendTransaction(wire).send().isFailure() shouldBe true
            assertRequest("sendTransaction", """["${Base64.encode(wire)}",{"encoding":"base64","preflightCommitment":"confirmed"}]""")
        }
    }

    test("submission accepts maximum-size valid transactions") {
        val signer = KeypairSigner.fromSeed(ByteArray(32) { 1 })
        fun instruction(size: Int) = Instruction(Programs.SYSTEM, emptyList(), ByteArray(size))
        for (size in listOf(1232, 1233, 4096)) {
            // 42 fixed + 64 account bytes + 4 instruction header + 64 signature.
            val signed = SolanaTxV1.compile(signer.publicKey, blockhash, instruction(size - 174), SolanaTransactionConfig()).unwrap().sign(signer)
            signed.serialize().size shouldBe size
            response = "\"${signed.id}\""
            provider.sendTransaction(signed).send().unwrap().signature shouldBe signed.id
            provider.sendTransaction(signed.serialize()).send().unwrap().signature shouldBe signed.id
        }
        for (version in listOf("legacy", "v0")) {
            for (size in listOf(1232, 1233)) {
                val dataSize = size - if (version == "legacy") 170 else 172
                if (size > 1232) {
                    shouldThrow<IllegalArgumentException> {
                        if (version == "legacy") SolanaTxLegacy.compile(signer.publicKey, blockhash, instruction(dataSize)).unwrap() else SolanaTxV0.compile(signer.publicKey, blockhash, instruction(dataSize)).unwrap()
                    }
                    continue
                }
                val tx = if (version == "legacy") SolanaTxLegacy.compile(signer.publicKey, blockhash, instruction(dataSize)).unwrap() else SolanaTxV0.compile(signer.publicKey, blockhash, instruction(dataSize)).unwrap()
                val signed = tx.sign(signer)
                signed.serialize().size shouldBe size
                response = "\"${signed.id}\""
                provider.sendTransaction(signed).send().unwrap().signature shouldBe signed.id
                provider.sendTransaction(signed.serialize()).send().unwrap().signature shouldBe signed.id
            }
        }
    }

    test("raw submission forwards malformed unsupported and oversized bytes and preserves RPC errors") {
        error = """{"code":-32602,"message":"invalid transaction"}"""
        for (wire in listOf(byteArrayOf(), byteArrayOf(130.toByte(), 1), ByteArray(1233), ByteArray(4097))) {
            val request = provider.sendTransaction(wire, Commitment.FINALIZED)
            val result = request.send()
            result.isFailure() shouldBe true
            result.unwrapError().code shouldBe -32602
            result.unwrapError().message shouldBe "invalid transaction"
            assertRequest("sendTransaction", """["${Base64.encode(wire)}",{"encoding":"base64","preflightCommitment":"finalized"}]""")
        }
    }

    test("v1 uses tail-signature envelopes for send/simulation and message-only bytes for fees") {
        val signer = KeypairSigner.fromSeed(ByteArray(32) { 1 })
        val tx = SolanaTxV1.compile(signer.publicKey, blockhash, SystemProgram.transfer(signer.publicKey, address, 1), SolanaTransactionConfig(computeUnitLimit = 20000, loadedAccountsDataSizeLimit = 65536)).unwrap()
        val signed = tx.sign(signer)
        response = "\"${signed.id}\""
        provider.sendTransaction(signed).send().unwrap().signature shouldBe signed.id
        assertRequest("sendTransaction", """["${signed.toBase64()}",{"encoding":"base64","preflightCommitment":"confirmed"}]""")
        provider.sendTransaction(signed.serialize()).send().unwrap().signature shouldBe signed.id
        response = contextual("""{"err":null,"logs":[]}""")
        provider.simulateTransaction(tx).send().unwrap().value.err shouldBe null
        assertRequest("simulateTransaction", """["${Base64.encode(tx.serializeForSimulation())}",{"commitment":"confirmed","encoding":"base64"}]""")
        provider.simulateTransaction(signed).send().unwrap().value.err shouldBe null
        assertRequest("simulateTransaction", """["${signed.toBase64()}",{"commitment":"confirmed","encoding":"base64"}]""")
        response = contextual("5000")
        provider.getFeeForMessage(signed).send().unwrap().value shouldBe bigIntegerOf(5000)
        assertRequest("getFeeForMessage", """["${Base64.encode(tx.serializeMessage())}",{"commitment":"confirmed"}]""")
        response = """{"slot":1,"blockTime":null,"version":1,"meta":null,"transaction":{"signatures":["${signed.id}"],"message":{"header":{"numRequiredSignatures":1,"numReadonlySignedAccounts":0,"numReadonlyUnsignedAccounts":0},"accountKeys":["${signer.publicKey}","$address"],"recentBlockhash":"$blockhash","instructions":[],"transactionConfig":{"priorityFee":null,"computeUnitLimit":20000,"loadedAccountsDataSizeLimit":65536,"heapSize":null}}}}"""
        val rpc = provider.getTransaction(signed.id).send().unwrap()!!
        rpc.type shouldBe SolanaTxType.V1
        rpc.transaction.message.transactionConfig shouldBe tx.config
    }

    test("transaction history preserves unsupported versions and returns null for missing transactions") {
        response = """{"slot":9007199254740993,"blockTime":null,"version":2,"transaction":{"signatures":["$signature"],"message":{"header":{"numRequiredSignatures":1,"numReadonlySignedAccounts":0,"numReadonlyUnsignedAccounts":0},"accountKeys":["$address"],"recentBlockhash":"$blockhash","instructions":[]},"futureMessage":true},"meta":{"err":null,"fee":0,"preBalances":[],"postBalances":[],"future":7},"extra":42}"""
        val tx = provider.getTransaction(signature).send().unwrap()!!
        tx.slot shouldBe BigInteger("9007199254740993")
        tx.type shouldBe SolanaTxType.Unsupported(2)
        tx.otherFields["extra"] shouldBe Kotlinx.DEFAULT.parseToJsonElement("42")
        tx.transaction.otherFields["futureMessage"] shouldBe Kotlinx.DEFAULT.parseToJsonElement("true")
        tx.meta!!.otherFields["future"] shouldBe Kotlinx.DEFAULT.parseToJsonElement("7")
        assertRequest("getTransaction", """["$signature",{"commitment":"confirmed","encoding":"json","maxSupportedTransactionVersion":255}]""")
        response = "null"
        provider.getTransaction(signature, Commitment.FINALIZED, 0).send().unwrap() shouldBe null
        assertRequest("getTransaction", """["$signature",{"commitment":"finalized","encoding":"json","maxSupportedTransactionVersion":0}]""")
        provider.getTransaction(signature, 1).send().unwrap() shouldBe null
        assertRequest("getTransaction", """["$signature",{"commitment":"confirmed","encoding":"json","maxSupportedTransactionVersion":1}]""")
    }

    test("transaction history leaves node version errors intact") {
        error = """{"code":-32015,"message":"Transaction version is not supported by the requesting client","data":{"version":1}}"""
        val result = provider.getTransaction(signature, 0).send()
        result.isFailure() shouldBe true
        result.unwrapError().code shouldBe -32015
        result.unwrapError().data.toString() shouldBe """{"version":1}"""
    }

    test("transaction history returns typed fields even for unsupported versions") {
        response = """{"slot":1,"blockTime":null,"version":2,"transaction":{"signatures":["$signature"],"message":{"header":{"numRequiredSignatures":1,"numReadonlySignedAccounts":0,"numReadonlyUnsignedAccounts":0},"accountKeys":["$address"],"recentBlockhash":"$blockhash","instructions":[]}},"meta":{"err":null,"fee":5000,"preBalances":[],"postBalances":[],"logMessages":["hello"]}}"""
        val tx = provider.getTransaction(signature).send().unwrap()!!
        tx.type shouldBe SolanaTxType.Unsupported(2)
        tx.transaction.signatures shouldBe listOf(signature)
        tx.transaction.message.accountKeys.single() shouldBe address
        tx.transaction.message.recentBlockhash shouldBe blockhash
        tx.meta!!.fee shouldBe bigIntegerOf(5000)
        tx.meta.logMessages shouldBe listOf("hello")
        tx.meta.isSuccess shouldBe true
    }

    test("builder performs no RPC and uses finalized by default") {
        val config = RpcClientConfig().client(ktor)
        val built = SolanaProvider.builder("https://example.invalid").config(config).build().unwrap()
        config.subscriptionDescriptor shouldBe SubscriptionDescriptor.ETHEREUM
        built.defaultCommitment shouldBe Commitment.FINALIZED
        requests.size shouldBe 0
        built.close()
        SolanaProvider.builder("ftp://example.invalid").build().isFailure() shouldBe true
    }

    test("builder defaults are copied and invalid per-method defaults are never silently substituted") {
        val builder = SolanaProvider.builder("https://example.invalid")
            .config(RpcClientConfig().client(ktor))
            .defaultCommitment(Commitment.PROCESSED)
        val built = builder.build().unwrap()
        builder.defaultCommitment(Commitment.FINALIZED)
        try {
            built.defaultCommitment shouldBe Commitment.PROCESSED
            response = contextual("1")
            built.getBalance(address).send().unwrap().value shouldBe bigIntegerOf(1)
            assertRequest("getBalance", """["$address",{"commitment":"processed"}]""")
            error = """{"code":-32602,"message":"Invalid commitment"}"""
            built.getTransaction(signature).send().unwrapError().code shouldBe -32602
            assertRequest("getTransaction", """["$signature",{"commitment":"processed","encoding":"json","maxSupportedTransactionVersion":255}]""")
            built.getSignaturesForAddress(address).send().unwrapError().code shouldBe -32602
            assertRequest("getSignaturesForAddress", """["$address",{"commitment":"processed","limit":1000}]""")
            error = null
            response = "null"
            built.getTransaction(signature, commitment = Commitment.CONFIRMED).send().unwrap() shouldBe null
            assertRequest("getTransaction", """["$signature",{"commitment":"confirmed","encoding":"json","maxSupportedTransactionVersion":255}]""")
            built.defaultCommitment shouldBe Commitment.PROCESSED
        } finally {
            built.close()
        }
    }

    test("decompiling a transaction fetches only the lookup tables it actually needs") {
        val movable = List(6) { SolanaAddress(ByteArray(32) { i -> if (i == 0) (100 + it).toByte() else 7 }) }
        val tableKey = SolanaAddress(ByteArray(32) { 77 })
        val table = AddressLookupTableAccount(tableKey, movable)
        val program = SolanaAddress(ByteArray(32) { 42 })
        val payer = KeypairSigner.fromSeed(ByteArray(32) { 1 })
        val instruction = Instruction(program, movable.mapIndexed { i, a -> AccountMeta(a, writable = i % 2 == 0) }, byteArrayOf(1))
        val request = SolanaTransactionRequest()
            .feePayer(payer.publicKey)
            .blockhash(blockhash)
            .instruction(instruction)

        // a legacy message loads no addresses, so no request is made at all
        val legacy = request.compileLegacy().unwrap()
        requests.clear()
        provider.decompileTransaction(legacy).send().unwrap().instructions shouldBe listOf(instruction)
        requests.size shouldBe 0

        // a v0 message drawn on a table costs exactly one getMultipleAccounts
        val v0 = request.compileV0(listOf(table)).unwrap()
        v0.addressLookupTables.size shouldBe 1
        val header = ByteArray(4).also { it[0] = 1 } + ByteArray(8) { -1 } + ByteArray(9) + ByteArray(33) + ByteArray(2)
        val encoded = Base64.encode(header + movable.fold(ByteArray(0)) { acc, it -> acc + it.asByteArray() })
        response = contextual("""[{"data":["$encoded","base64"],"executable":false,"lamports":1,"owner":"$address","rentEpoch":0}]""")
        provider.decompileTransaction(v0).send().unwrap().instructions shouldBe listOf(instruction)
        requests.size shouldBe 1
        assertRequest("getMultipleAccounts", """[["$tableKey"],{"commitment":"confirmed","encoding":"base64"}]""")

        // a table that no longer exists is reported rather than silently resolving to nothing
        response = contextual("[null]")
        provider.decompileTransaction(v0).send().unwrapError().message shouldBe
            "Lookup table $tableKey does not exist, so this transaction cannot be resolved"
    }
})
