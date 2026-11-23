package io.ethers.aa.bundler

import io.ethers.aa.EntryPoint
import io.ethers.aa.EntryPointVersion
import io.ethers.aa.FakeJsonRpcClient
import io.ethers.aa.UserOperation
import io.ethers.aa.UserOperationJson
import io.ethers.aa.errors.BundlerErrorCode
import io.ethers.aa.errors.EntryPointErrorCode
import io.ethers.aa.errors.UserOperationError
import io.ethers.core.Kotlinx
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.providers.RpcError
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

class BundlerClientTest : FunSpec({
    val userOpHash = Hash("0x" + "ab".repeat(32))
    val sender = Address("0x1111111111111111111111111111111111111111")

    val userOp = UserOperation(
        sender = sender,
        nonce = bigIntegerOf(1),
        callData = Bytes("0x1234"),
        callGasLimit = bigIntegerOf(1),
        verificationGasLimit = bigIntegerOf(2),
        preVerificationGas = bigIntegerOf(3),
        maxFeePerGas = bigIntegerOf(4),
        maxPriorityFeePerGas = bigIntegerOf(5),
        signature = Bytes("0x99"),
    )

    val receiptJson = """
        {
          "userOpHash": "$userOpHash",
          "entryPoint": "${EntryPoint.V0_7.address}",
          "sender": "$sender",
          "nonce": "0x1",
          "paymaster": "0x0000000000000000000000000000000000000000",
          "actualGasCost": "0x5208",
          "actualGasUsed": "0x100",
          "success": true,
          "reason": "",
          "logs": [],
          "receipt": {
            "blockHash": "0x${"01".repeat(32)}",
            "blockNumber": "0x10",
            "transactionHash": "0x${"02".repeat(32)}",
            "transactionIndex": "0x0",
            "from": "0x2222222222222222222222222222222222222222",
            "to": "${EntryPoint.V0_7.address}",
            "gasUsed": "0x5208",
            "cumulativeGasUsed": "0x5208",
            "contractAddress": null,
            "logs": [],
            "logsBloom": "0x${"00".repeat(256)}",
            "type": "0x2",
            "effectiveGasPrice": "0x1",
            "status": "0x1"
          }
        }
    """.trimIndent()

    test("sendUserOperation sends the operation in the EntryPoint's layout") {
        val rpc = FakeJsonRpcClient().respond("eth_sendUserOperation", "\"$userOpHash\"")
        BundlerClient(rpc).sendUserOperation(userOp, EntryPoint.V0_6).send().unwrap() shouldBe userOpHash

        val params = rpc.requests("eth_sendUserOperation").single().params
        params[0] shouldBe UserOperationJson.encode(userOp, EntryPointVersion.V0_6)
        params[1] shouldBe JsonPrimitive(EntryPoint.V0_6.address.toString())
    }

    test("estimateUserOperationGas parses all gas fields") {
        val rpc = FakeJsonRpcClient().respond(
            "eth_estimateUserOperationGas",
            """{"preVerificationGas":"0x1","verificationGasLimit":"0x2","callGasLimit":"0x3","paymasterVerificationGasLimit":"0x4","paymasterPostOpGasLimit":"0x5"}""",
        )
        BundlerClient(rpc).estimateUserOperationGas(userOp, EntryPoint.V0_7).send().unwrap() shouldBe
            UserOperationGasEstimate(bigIntegerOf(1), bigIntegerOf(2), bigIntegerOf(3), bigIntegerOf(4), bigIntegerOf(5))
        rpc.requests.single().params shouldHaveSize 2
    }

    test("estimateUserOperationGas accepts v0.6 estimates without paymaster limits, and decimal numbers") {
        val rpc = FakeJsonRpcClient().respond(
            "eth_estimateUserOperationGas",
            """{"preVerificationGas":1,"verificationGasLimit":"2","callGasLimit":"0x3"}""",
        )
        BundlerClient(rpc).estimateUserOperationGas(userOp, EntryPoint.V0_6).send().unwrap() shouldBe
            UserOperationGasEstimate(bigIntegerOf(1), bigIntegerOf(2), bigIntegerOf(3))
    }

    test("getUserOperationReceipt returns null for an unknown operation") {
        val rpc = FakeJsonRpcClient().respond("eth_getUserOperationReceipt", "null")
        BundlerClient(rpc).getUserOperationReceipt(userOpHash).send().unwrap().shouldBeNull()
    }

    test("getUserOperationReceipt parses the receipt") {
        val rpc = FakeJsonRpcClient().respond("eth_getUserOperationReceipt", receiptJson)
        val receipt = BundlerClient(rpc).getUserOperationReceipt(userOpHash).send().unwrap().shouldNotBeNull()

        receipt.userOpHash shouldBe userOpHash
        receipt.paymaster.shouldBeNull()
        receipt.success shouldBe true
        receipt.reason.shouldBeNull()
        receipt.actualGasCost shouldBe bigIntegerOf(21000)
        receipt.receipt.blockNumber shouldBe 16L
    }

    test("getUserOperationByHash parses pending and included operations") {
        val opJson = UserOperationJson.encode(userOp, EntryPointVersion.V0_7)
        val rpc = FakeJsonRpcClient().respond(
            "eth_getUserOperationByHash",
            """{"userOperation":$opJson,"entryPoint":"${EntryPoint.V0_7.address}","blockNumber":null,"blockHash":null,"transactionHash":null}""",
        )
        val result = BundlerClient(rpc).getUserOperationByHash(userOpHash).send().unwrap().shouldNotBeNull()
        result.userOperation shouldBe userOp
        result.blockNumber.shouldBeNull()
        result.entryPoint shouldBe EntryPoint.V0_7.address
    }

    test("supportedEntryPoints and chainId") {
        val rpc = FakeJsonRpcClient()
            .respond("eth_supportedEntryPoints", """["${EntryPoint.V0_7.address}","${EntryPoint.V0_8.address}"]""")
            .respond("eth_chainId", "\"0x2105\"")
        val bundler = BundlerClient(rpc)
        bundler.supportedEntryPoints().send().unwrap() shouldBe listOf(EntryPoint.V0_7.address, EntryPoint.V0_8.address)
        bundler.chainId().send().unwrap() shouldBe 8453L
    }

    context("waitForUserOperationReceipt") {
        test("polls until the receipt is available") {
            var calls = 0
            val rpc = FakeJsonRpcClient().on("eth_getUserOperationReceipt") { if (++calls < 3) "null" else receiptJson }
            val receipt = BundlerClient(rpc).waitForUserOperationReceipt(userOpHash, 5_000.milliseconds, 1.milliseconds, false)
                .send().unwrap()
            receipt.userOpHash shouldBe userOpHash
            calls shouldBe 3
        }

        test("times out") {
            val rpc = FakeJsonRpcClient().respond("eth_getUserOperationReceipt", "null")
            val error = BundlerClient(rpc).waitForUserOperationReceipt(userOpHash, 30.milliseconds, 5.milliseconds, false)
                .send().unwrapError()
            error.shouldBeInstanceOf<UserOperationError.ReceiptTimeout>().userOpHash shouldBe userOpHash
        }

        test("fails on an RPC error, unless retrying on errors") {
            var calls = 0
            val rpc = FakeJsonRpcClient().on("eth_getUserOperationReceipt") {
                if (++calls == 1) throw FakeJsonRpcClient.RpcFailure(RpcError(-32000, "temporarily unavailable"))
                receiptJson
            }
            BundlerClient(rpc).waitForUserOperationReceipt(userOpHash, 5_000.milliseconds, 1.milliseconds, false)
                .send().unwrapError().shouldBeInstanceOf<UserOperationError.Rpc>()

            calls = 0
            BundlerClient(rpc).waitForUserOperationReceipt(userOpHash, 5_000.milliseconds, 1.milliseconds, true)
                .send().unwrap().userOpHash shouldBe userOpHash
        }
    }

    test("bundler rejections carry their EntryPoint and ERC-7769 error codes") {
        val rpc = FakeJsonRpcClient().fail(
            "eth_sendUserOperation",
            RpcError(-32500, "FailedOp(0,\"AA21 didn't pay prefund\")"),
        )
        val error = BundlerClient(rpc).sendUserOperation(userOp, EntryPoint.V0_7)
            .mapError { UserOperationError.Rpc(it) }
            .send()
            .unwrapError()
            .shouldBeInstanceOf<UserOperationError.Rpc>()

        error.entryPointError shouldBe EntryPointErrorCode.INSUFFICIENT_PREFUND
        error.bundlerError shouldBe BundlerErrorCode.SIMULATE_VALIDATION
    }

    test("result types decode with their own serializers") {
        Kotlinx.DEFAULT.decodeFromString(
            UserOperationGasEstimate.serializer(),
            """{"preVerificationGas":"0x1","verificationGasLimit":"0x2","callGasLimit":"0x3","paymasterPostOpGasLimit":null}""",
        ) shouldBe UserOperationGasEstimate(bigIntegerOf(1), bigIntegerOf(2), bigIntegerOf(3))

        Kotlinx.DEFAULT.decodeFromString(UserOperationReceipt.serializer(), receiptJson).userOpHash shouldBe userOpHash
    }

    test("missing required result fields fail to decode") {
        shouldThrow<SerializationException> {
            Kotlinx.DEFAULT.decodeFromString(UserOperationGasEstimate.serializer(), """{"preVerificationGas":"0x1","callGasLimit":"0x3"}""")
        }.message shouldBe "Missing field 'verificationGasLimit'"

        val rpc = FakeJsonRpcClient().respond("eth_estimateUserOperationGas", """{"preVerificationGas":"0x1"}""")
        BundlerClient(rpc).estimateUserOperationGas(userOp, EntryPoint.V0_7).send().unwrapError().message shouldContain "verificationGasLimit"
    }

    test("error code parsing") {
        EntryPointErrorCode.parse("UserOperation reverted during simulation with reason: AA25 invalid account nonce") shouldBe
            EntryPointErrorCode.INVALID_NONCE
        EntryPointErrorCode.parse("AA00 not a code").shouldBeNull()
        EntryPointErrorCode.parse("no code at all").shouldBeNull()
        EntryPointErrorCode.parse("XAA21").shouldBeNull()
        BundlerErrorCode.fromCode(-32507) shouldBe BundlerErrorCode.INVALID_SIGNATURE
        BundlerErrorCode.fromCode(-1).shouldBeNull()
    }
})
