package io.ethers.aa.account

import io.ethers.aa.EntryPoint
import io.ethers.aa.FakeJsonRpcClient
import io.ethers.aa.UserOperation
import io.ethers.aa.bundle.StandardCallDataDecoder
import io.ethers.aa.bundler.BundlerClient
import io.ethers.aa.errors.EntryPointErrorCode
import io.ethers.aa.errors.UserOperationError
import io.ethers.aa.paymaster.PaymasterClient
import io.ethers.core.Result
import io.ethers.core.success
import io.ethers.core.types.Address
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Signature
import io.ethers.providers.Provider
import io.ethers.providers.RpcError
import io.ethers.signers.PrivateKeySigner
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SmartAccountClientTest : FunSpec({
    val accountAddress = Address("0x1111111111111111111111111111111111111111")
    val target = Address("0x2222222222222222222222222222222222222222")
    val paymasterAddress = Address("0x3333333333333333333333333333333333333333")
    val userOpHash = Hash("0x" + "ab".repeat(32))
    val call = Call(target, bigIntegerOf(5), Bytes("0x1234"))
    val fixedFees = FeeEstimator { success(UserOperationFees(bigIntegerOf(2_000), bigIntegerOf(100))) }

    class Setup(
        val node: FakeJsonRpcClient,
        val bundlerRpc: FakeJsonRpcClient,
        val paymasterRpc: FakeJsonRpcClient,
        val account: TestSmartAccount,
        val client: SmartAccountClient,
    ) {
        fun sent(): UserOperation = UserOperation.fromJson(bundlerRpc.requests("eth_sendUserOperation").single().params[0])
    }

    fun setup(
        deployed: Boolean,
        account: TestSmartAccount = TestSmartAccount(accountAddress),
        withPaymaster: Boolean = true,
        stubIsFinal: Boolean = false,
    ): Setup {
        val node = FakeJsonRpcClient()
            .respond("eth_getCode", if (deployed) "\"0x6080\"" else "\"0x\"")
            .respond("eth_call", "\"0x" + "0".repeat(63) + "9\"")
            .respond("eth_getTransactionCount", "\"0x7\"")

        val bundlerRpc = FakeJsonRpcClient()
            .respond(
                "eth_estimateUserOperationGas",
                """{"preVerificationGas":"0x10","verificationGasLimit":"0x20","callGasLimit":"0x30","paymasterVerificationGasLimit":"0x40","paymasterPostOpGasLimit":"0x50"}""",
            )
            .respond("eth_sendUserOperation", "\"$userOpHash\"")

        val paymasterRpc = FakeJsonRpcClient()
            .respond(
                "pm_getPaymasterStubData",
                """{"paymaster":"$paymasterAddress","paymasterData":"0xaa","paymasterPostOpGasLimit":"0x60","sponsor":{"name":"Test"},"isFinal":$stubIsFinal}""",
            )
            .respond("pm_getPaymasterData", """{"paymaster":"$paymasterAddress","paymasterData":"0xbbbb"}""")

        val client = SmartAccountClient(
            account = account,
            bundler = BundlerClient(bundlerRpc),
            provider = Provider(node, 1),
            paymaster = if (withPaymaster) PaymasterClient(paymasterRpc) else null,
            feeEstimator = fixedFees,
        )
        return Setup(node, bundlerRpc, paymasterRpc, account, client)
    }

    test("sends a sponsored operation that deploys the account") {
        val setup = setup(deployed = false)
        val pending = setup.client.sendUserOperation(listOf(call)).send().unwrap()
        pending.userOpHash shouldBe userOpHash

        val sent = setup.sent()
        sent.sender shouldBe accountAddress
        sent.nonce shouldBe bigIntegerOf(9)
        sent.factory shouldBe TestSmartAccount.FACTORY
        sent.factoryData shouldBe Bytes("0x5fbfb9cf")
        sent.callData shouldBe TestSmartAccount.EXECUTE.encodeCall(listOf(target, bigIntegerOf(5), Bytes("0x1234")))
        sent.maxFeePerGas shouldBe bigIntegerOf(2_000)
        sent.maxPriorityFeePerGas shouldBe bigIntegerOf(100)
        sent.callGasLimit shouldBe bigIntegerOf(0x30)
        sent.verificationGasLimit shouldBe bigIntegerOf(0x20)
        sent.preVerificationGas shouldBe bigIntegerOf(0x10)
        sent.paymaster shouldBe paymasterAddress
        sent.paymasterData shouldBe Bytes("0xbbbb")
        // the stub's own estimate wins over the bundler's
        sent.paymasterPostOpGasLimit shouldBe bigIntegerOf(0x60)
        sent.paymasterVerificationGasLimit shouldBe bigIntegerOf(0x40)

        // the operation is signed by the owner over its final hash
        val hash = sent.hash(EntryPoint.V0_7, 1)
        Signature.fromByteArray(sent.signature.asByteArray()).unwrap().recoverFromHash(hash.asByteArray()) shouldBe
            setup.account.owner.address

        // the bundler estimated gas with the stub signature and stub paymaster data
        val estimated = UserOperation.fromJson(setup.bundlerRpc.requests("eth_estimateUserOperationGas").single().params[0])
        estimated.signature shouldBe TestSmartAccount.STUB_SIGNATURE
        estimated.paymasterData shouldBe Bytes("0xaa")

        // the final paymaster data was requested for the estimated operation
        val finalRequest = setup.paymasterRpc.requests("pm_getPaymasterData").single()
        UserOperation.fromJson(finalRequest.params[0]).callGasLimit shouldBe bigIntegerOf(0x30)
        finalRequest.params[1] shouldBe JsonPrimitive(EntryPoint.V0_7.address.toString())
        finalRequest.params[2] shouldBe JsonPrimitive("0x1")
        setup.account.adjusted shouldBe 1
    }

    test("deployed account has no factory, and reads the nonce of its signer's key") {
        val setup = setup(deployed = true, withPaymaster = false)
        val sessionKey = PrivateKeySigner("0x" + "02".repeat(32))
        val request = UserOperationRequest(listOf(call)).signer(TestSessionSigner(bigIntegerOf(42), sessionKey))
        setup.client.sendUserOperation(request).send().unwrap()

        val sent = setup.sent()
        sent.factory.shouldBeNull()
        sent.paymaster.shouldBeNull()
        val nonceCall = setup.node.requests("eth_call").single().params[0].jsonObject
        // getNonce(address sender, uint192 key) with key 42
        val data = (nonceCall["data"] ?: nonceCall["input"])!!.jsonPrimitive.content
        data shouldBe "0x35567e1a" + "0".repeat(24) + accountAddress.toString().removePrefix("0x") + "0".repeat(62) + "2a"
        Signature.fromByteArray(sent.signature.asByteArray()).unwrap().recoverFromHash(sent.hash(EntryPoint.V0_7, 1).asByteArray()) shouldBe
            sessionKey.address
    }

    test("final stub paymaster data skips pm_getPaymasterData") {
        val setup = setup(deployed = true, stubIsFinal = true)
        setup.client.sendUserOperation(listOf(call)).send().unwrap()
        setup.paymasterRpc.requests("pm_getPaymasterData").shouldBeEmpty()
        setup.sent().paymasterData shouldBe Bytes("0xaa")
    }

    test("unsponsored request skips the paymaster") {
        val setup = setup(deployed = true)
        setup.client.sendUserOperation(UserOperationRequest(listOf(call)).sponsored(false)).send().unwrap()
        setup.paymasterRpc.requests.shouldBeEmpty()
        setup.sent().paymaster.shouldBeNull()
    }

    test("explicit paymaster is used as-is") {
        val setup = setup(deployed = true)
        val request = UserOperationRequest(listOf(call)).paymaster(Address("0x4444444444444444444444444444444444444444"), Bytes("0x01"))
        setup.client.sendUserOperation(request).send().unwrap()
        setup.paymasterRpc.requests.shouldBeEmpty()
        setup.sent().paymasterData shouldBe Bytes("0x01")
    }

    test("fully specified request is not estimated") {
        val setup = setup(deployed = true, withPaymaster = false)
        val request = UserOperationRequest(listOf(call))
            .nonce(bigIntegerOf(3))
            .callGasLimit(bigIntegerOf(1))
            .verificationGasLimit(bigIntegerOf(2))
            .preVerificationGas(bigIntegerOf(3))
            .maxFeePerGas(bigIntegerOf(4))
            .maxPriorityFeePerGas(bigIntegerOf(5))
        val prepared = setup.client.prepareUserOperation(request).send().unwrap()

        setup.bundlerRpc.requests.shouldBeEmpty()
        setup.node.requests("eth_call").shouldBeEmpty()
        prepared.signature shouldBe TestSmartAccount.STUB_SIGNATURE
        prepared.nonce shouldBe bigIntegerOf(3)
        prepared.callGasLimit shouldBe bigIntegerOf(1)
    }

    test("v0.6 operations carry no paymaster gas limits") {
        val account = TestSmartAccount(accountAddress, entryPoint = EntryPoint.V0_6)
        val setup = setup(deployed = true, account = account)
        setup.client.sendUserOperation(listOf(call)).send().unwrap()

        val sent = setup.bundlerRpc.requests("eth_sendUserOperation").single().params[0].jsonObject
        sent.containsKey("paymasterAndData") shouldBe true
        val decoded = UserOperation.fromJson(sent)
        decoded.paymaster shouldBe paymasterAddress
        decoded.paymasterVerificationGasLimit.shouldBeNull()
    }

    test("EIP-7702 account is delegated with the operation") {
        val delegate = Address("0x5555555555555555555555555555555555555555")
        val account = TestSmartAccount(accountAddress, entryPoint = EntryPoint.V0_8, factoryArgs = null, eip7702Delegate = delegate)
        val setup = setup(deployed = false, account = account, withPaymaster = false)
        setup.client.sendUserOperation(listOf(call)).send().unwrap()

        val sent = setup.sent()
        sent.factory shouldBe UserOperation.EIP7702_FACTORY_MARKER
        val auth = sent.eip7702Auth.shouldNotBeNull()
        auth.address shouldBe delegate
        auth.nonce shouldBe 7L
        auth.chainId shouldBe 1L
    }

    test("EIP-7702 account on an EntryPoint without EIP-7702 support fails") {
        val account = TestSmartAccount(
            accountAddress,
            entryPoint = EntryPoint.V0_7,
            factoryArgs = null,
            eip7702Delegate = Address("0x5555555555555555555555555555555555555555"),
        )
        val error = setup(deployed = false, account = account).client.sendUserOperation(listOf(call)).send().unwrapError()
        error.shouldBeInstanceOf<UserOperationError.InvalidOperation>().message shouldContain "EIP-7702"
    }

    test("undeployed account without a factory fails") {
        val account = TestSmartAccount(accountAddress, factoryArgs = null)
        setup(deployed = false, account = account).client.sendUserOperation(listOf(call)).send().unwrapError()
            .shouldBeInstanceOf<UserOperationError.InvalidOperation>()
    }

    test("sponsorship without a paymaster fails") {
        setup(deployed = true, withPaymaster = false).client
            .sendUserOperation(UserOperationRequest(listOf(call)).sponsored(true)).send().unwrapError()
            .shouldBeInstanceOf<UserOperationError.InvalidOperation>()
    }

    test("missing paymaster gas limits fail early") {
        val setup = setup(deployed = true)
        val bundlerRpc = FakeJsonRpcClient()
            .respond("eth_estimateUserOperationGas", """{"preVerificationGas":"0x1","verificationGasLimit":"0x1","callGasLimit":"0x1"}""")
        val paymasterRpc = FakeJsonRpcClient().respond("pm_getPaymasterStubData", """{"paymaster":"$paymasterAddress","paymasterData":"0xaa"}""")
        val client = SmartAccountClient(setup.account, BundlerClient(bundlerRpc), Provider(setup.node, 1), PaymasterClient(paymasterRpc), fixedFees)
        client.sendUserOperation(listOf(call)).send().unwrapError().shouldBeInstanceOf<UserOperationError.InvalidOperation>()
        bundlerRpc.requests("eth_sendUserOperation").shouldBeEmpty()
    }

    test("final stub paymaster data is not adjusted by the account") {
        val setup = setup(deployed = true, stubIsFinal = true)
        setup.client.sendUserOperation(listOf(call)).send().unwrap()
        setup.account.adjusted shouldBe 0
    }

    test("request without calls fails") {
        setup(deployed = true).client.sendUserOperation(UserOperationRequest()).send().unwrapError()
            .shouldBeInstanceOf<UserOperationError.InvalidOperation>()
    }

    test("bundler rejection is reported with its EntryPoint error code") {
        val setup = setup(deployed = true, withPaymaster = false)
        val bundlerRpc = FakeJsonRpcClient()
            .fail("eth_sendUserOperation", RpcError(-32500, "AA25 invalid account nonce"))
            .respond("eth_estimateUserOperationGas", """{"preVerificationGas":"0x1","verificationGasLimit":"0x1","callGasLimit":"0x1"}""")
        val client = SmartAccountClient(setup.account, BundlerClient(bundlerRpc), Provider(setup.node, 1), null, fixedFees)

        val error = client.sendUserOperation(listOf(call)).send().unwrapError().shouldBeInstanceOf<UserOperationError.Rpc>()
        error.entryPointError shouldBe EntryPointErrorCode.INVALID_NONCE
    }

    test("signing failure is reported") {
        val failing = object : UserOperationSigner {
            override suspend fun getStubSignature(userOperation: UserOperation) = Bytes("0x00")
            override suspend fun signUserOperation(userOperation: UserOperation, userOpHash: Hash, chainId: Long): Bytes {
                throw IllegalStateException("device locked")
            }
        }
        val setup = setup(deployed = true, withPaymaster = false)
        val error = setup.client.sendUserOperation(UserOperationRequest(listOf(call)).signer(failing)).send().unwrapError()
        error.shouldBeInstanceOf<UserOperationError.Signing>().cause.shouldNotBeNull().message shouldBe "device locked"
        setup.bundlerRpc.requests("eth_sendUserOperation").shouldBeEmpty()
    }

    test("node fee estimator doubles the base fee and adds the priority fee") {
        val node = FakeJsonRpcClient()
            .respond("eth_maxPriorityFeePerGas", "\"0x64\"")
            .respond("eth_getBlockByNumber", TEST_BLOCK_JSON)
        val fees = NodeFeeEstimator(Provider(node, 1)).estimateFees()
        fees shouldBe Result.success(UserOperationFees(bigIntegerOf(2 * 0x3e8 + 0x64), bigIntegerOf(0x64)))
        node.requests.map { it.method }.toSet() shouldBe setOf("eth_maxPriorityFeePerGas", "eth_getBlockByNumber")
    }

    test("calls are encoded by the account") {
        val setup = setup(deployed = true, withPaymaster = false)
        val prepared = setup.client.prepareUserOperation(listOf(call, call)).send().unwrap()
        StandardCallDataDecoder.decodeCalls(accountAddress, prepared.callData) shouldContainExactly listOf(call, call)
    }

    test("paymaster context is sent to the paymaster") {
        val setup = setup(deployed = true)
        setup.client.sendUserOperation(UserOperationRequest(listOf(call)).paymasterContext(mapOf("policyId" to "abc"))).send().unwrap()
        setup.paymasterRpc.requests("pm_getPaymasterStubData").single().params[3] shouldBe
            JsonObject(mapOf("policyId" to JsonPrimitive("abc")))
    }
})

private val TEST_BLOCK_JSON = """
    {
      "difficulty": "0x0",
      "extraData": "0x",
      "gasLimit": "0x1c9c380",
      "gasUsed": "0x0",
      "hash": "0x${"11".repeat(32)}",
      "logsBloom": "0x${"00".repeat(256)}",
      "mixHash": "0x${"22".repeat(32)}",
      "nonce": "0x0000000000000000",
      "number": "0x10",
      "parentHash": "0x${"33".repeat(32)}",
      "receiptsRoot": "0x${"44".repeat(32)}",
      "sha3Uncles": "0x${"55".repeat(32)}",
      "size": "0x100",
      "stateRoot": "0x${"66".repeat(32)}",
      "timestamp": "0x6527e5fb",
      "transactionsRoot": "0x${"77".repeat(32)}",
      "baseFeePerGas": "0x3e8",
      "miner": "0x0000000000000000000000000000000000000000",
      "transactions": [],
      "uncles": []
    }
""".trimIndent()
