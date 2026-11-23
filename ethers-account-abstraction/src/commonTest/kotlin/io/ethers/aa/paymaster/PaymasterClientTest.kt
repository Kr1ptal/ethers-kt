package io.ethers.aa.paymaster

import io.ethers.aa.EntryPoint
import io.ethers.aa.EntryPointVersion
import io.ethers.aa.FakeJsonRpcClient
import io.ethers.aa.UserOperation
import io.ethers.aa.UserOperationJson
import io.ethers.core.isFailure
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class PaymasterClientTest : FunSpec({
    val paymaster = Address("0x3333333333333333333333333333333333333333")
    val userOp = UserOperation(
        sender = Address("0x1111111111111111111111111111111111111111"),
        nonce = bigIntegerOf(1),
        callData = Bytes("0x1234"),
        callGasLimit = bigIntegerOf(0),
        verificationGasLimit = bigIntegerOf(0),
        preVerificationGas = bigIntegerOf(0),
        maxFeePerGas = bigIntegerOf(4),
        maxPriorityFeePerGas = bigIntegerOf(5),
        signature = Bytes("0x99"),
    )

    test("stub data request carries the operation, EntryPoint, chain ID and context") {
        val rpc = FakeJsonRpcClient().respond(
            "pm_getPaymasterStubData",
            """{"paymaster":"$paymaster","paymasterData":"0x01","paymasterVerificationGasLimit":"0x10","paymasterPostOpGasLimit":"0x20","sponsor":{"name":"Acme","icon":"data:image/png;base64,AA=="},"isFinal":true}""",
        )
        val stub = PaymasterClient(rpc).getPaymasterStubData(userOp, EntryPoint.V0_7, 8453, mapOf("policyId" to "p1")).send().unwrap()

        stub shouldBe PaymasterStubData(
            paymaster = paymaster,
            paymasterData = Bytes("0x01"),
            paymasterVerificationGasLimit = bigIntegerOf(16),
            paymasterPostOpGasLimit = bigIntegerOf(32),
            sponsor = PaymasterSponsor("Acme", "data:image/png;base64,AA=="),
            isFinal = true,
        )

        val params = rpc.requests.single().params
        params[0] shouldBe UserOperationJson.encode(userOp, EntryPointVersion.V0_7)
        params[1] shouldBe JsonPrimitive(EntryPoint.V0_7.address.toString())
        params[2] shouldBe JsonPrimitive("0x2105")
        params[3] shouldBe JsonObject(mapOf("policyId" to JsonPrimitive("p1")))
    }

    test("missing context is sent as an empty object") {
        val rpc = FakeJsonRpcClient().respond("pm_getPaymasterData", """{"paymaster":"$paymaster","paymasterData":"0x"}""")
        PaymasterClient(rpc).getPaymasterData(userOp, EntryPoint.V0_7, 1).send().unwrap() shouldBe PaymasterData(paymaster, Bytes.EMPTY)
        rpc.requests.single().params[3] shouldBe JsonObject(emptyMap())
    }

    test("v0.6 paymasterAndData is split into paymaster and data") {
        val rpc = FakeJsonRpcClient()
            .respond("pm_getPaymasterStubData", """{"paymasterAndData":"${paymaster}abcd"}""")
            .respond("pm_getPaymasterData", """{"paymasterAndData":"${paymaster}ef"}""")
        val client = PaymasterClient(rpc)

        val stub = client.getPaymasterStubData(userOp, EntryPoint.V0_6, 1).send().unwrap()
        stub.paymaster shouldBe paymaster
        stub.paymasterData shouldBe Bytes("0xabcd")
        stub.isFinal shouldBe false
        stub.sponsor.shouldBeNull()

        client.getPaymasterData(userOp, EntryPoint.V0_6, 1).send().unwrap() shouldBe PaymasterData(paymaster, Bytes("0xef"))
    }

    test("packed v0.7 paymasterAndData yields its gas limits") {
        val packed = "$paymaster" + "000000000000000000000000000000aa" + "000000000000000000000000000000bb" + "cc"
        val rpc = FakeJsonRpcClient().respond("pm_getPaymasterStubData", """{"paymasterAndData":"$packed"}""")
        val stub = PaymasterClient(rpc).getPaymasterStubData(userOp, EntryPoint.V0_7, 1).send().unwrap()
        stub.paymasterVerificationGasLimit shouldBe bigIntegerOf(0xaa)
        stub.paymasterPostOpGasLimit shouldBe bigIntegerOf(0xbb)
        stub.paymasterData shouldBe Bytes("0xcc")
    }

    test("malformed response fails") {
        val rpc = FakeJsonRpcClient().respond("pm_getPaymasterData", """{"paymasterData":"0x01"}""")
        PaymasterClient(rpc).getPaymasterData(userOp, EntryPoint.V0_7, 1).send().isFailure() shouldBe true
    }
})
