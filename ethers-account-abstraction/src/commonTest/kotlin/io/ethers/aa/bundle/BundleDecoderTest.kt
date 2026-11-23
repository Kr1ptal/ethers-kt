package io.ethers.aa.bundle

import io.ethers.aa.EntryPoint
import io.ethers.aa.EntryPointVersion
import io.ethers.aa.PackedUserOperation
import io.ethers.aa.UserOperation
import io.ethers.aa.contracts.EntryPointV07
import io.ethers.aa.contracts.EntryPointV09
import io.ethers.abi.AbiCodec
import io.ethers.abi.AbiType
import io.ethers.core.Kotlinx
import io.ethers.core.types.Address
import io.ethers.core.types.Bloom
import io.ethers.core.types.Bytes
import io.ethers.core.types.Hash
import io.ethers.core.types.Log
import io.ethers.core.types.RPCTransaction
import io.ethers.core.types.TransactionReceipt
import io.ethers.core.types.transaction.TxType
import io.github.artificialpb.bignum.BigInteger
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class BundleDecoderTest : FunSpec({
    val ep7 = EntryPoint.V0_7.address
    val ep8 = EntryPoint.V0_8.address
    val bundler = Address("0x9999999999999999999999999999999999999999")
    val alice = Address("0xa11ce00000000000000000000000000000000000")
    val bob = Address("0xb0b0000000000000000000000000000000000000")
    val token = Address("0x7070707070707070707070707070707070707070")
    val aggregator = Address("0xa66a000000000000000000000000000000000000")
    val txHash = Hash("0x" + "fe".repeat(32))

    fun hash(n: Int) = Hash("0x" + n.toString(16).padStart(64, '0'))

    var logIndex = 0
    fun log(address: Address, topics: List<Hash>, data: Bytes = Bytes.EMPTY) = Log(address, topics, data, Hash.ZERO, 100, -1, txHash, 3, logIndex++, false)

    fun word(value: BigInteger) = AbiCodec.encode(AbiType.UInt(256), value)

    fun beforeExecution(ep: Address) = log(ep, listOf(EntryPointV09.BeforeExecution.abi.topicId))
    fun transfer(n: Int) = log(token, listOf(Hash("0x" + "dd".repeat(32)), hash(n)))
    fun userOperationEvent(ep: Address, opHash: Hash, sender: Address, nonce: Int, success: Boolean = true) = log(
        ep,
        listOf(EntryPointV09.UserOperationEvent.abi.topicId, opHash, Hash(sender), Hash(Address.ZERO)),
        Bytes(
            AbiCodec.encode(
                listOf(AbiType.UInt(256), AbiType.Bool, AbiType.UInt(256), AbiType.UInt(256)),
                listOf(bigIntegerOf(nonce.toLong()), success, bigIntegerOf(1000), bigIntegerOf(100)),
            ),
        ),
    )
    fun accountDeployed(ep: Address, opHash: Hash, sender: Address, factory: Address) = log(
        ep,
        listOf(EntryPointV09.AccountDeployed.abi.topicId, opHash, Hash(sender)),
        Bytes(AbiCodec.encode(listOf(AbiType.Address, AbiType.Address), listOf(factory, Address.ZERO))),
    )
    fun revertReason(ep: Address, opHash: Hash, sender: Address, reason: Bytes) = log(
        ep,
        listOf(EntryPointV09.UserOperationRevertReason.abi.topicId, opHash, Hash(sender)),
        Bytes(AbiCodec.encode(listOf(AbiType.UInt(256), AbiType.Bytes), listOf(bigIntegerOf(0), reason))),
    )
    fun aggregatorChanged(ep: Address, aggregator: Address) = log(ep, listOf(EntryPointV09.SignatureAggregatorChanged.abi.topicId, Hash(aggregator)))

    fun receipt(logs: List<Log>, to: Address? = ep7) = TransactionReceipt(
        blockHash = Hash.ZERO,
        blockNumber = 100,
        transactionHash = txHash,
        transactionIndex = 3,
        from = bundler,
        to = to,
        gasUsed = 1,
        cumulativeGasUsed = 1,
        contractAddress = null,
        logs = logs,
        logsBloom = Bloom(ByteArray(256)),
        type = TxType.DynamicFee,
        effectiveGasPrice = bigIntegerOf(1),
        status = 1,
        root = null,
    )

    fun transaction(input: Bytes, to: Address = ep7, hash: Hash = txHash): RPCTransaction {
        val json = """
            {"blockHash":"${Hash.ZERO}","blockNumber":"0x64","transactionIndex":"0x3","hash":"$hash","from":"$bundler",
             "to":"$to","value":"0x0","nonce":"0x1","gas":"0x100000","gasPrice":"0x1","maxFeePerGas":"0x1",
             "maxPriorityFeePerGas":"0x1","input":"$input","accessList":[],"chainId":"0x1","type":"0x2",
             "v":"0x0","r":"0x1","s":"0x1","yParity":"0x0"}
        """.trimIndent()
        return Kotlinx.DEFAULT.decodeFromString(RPCTransaction.serializer(), json)
    }

    fun packed(sender: Address, nonce: Int) = EntryPointV07.PackedUserOperation(
        sender, bigIntegerOf(nonce.toLong()), Bytes.EMPTY, Bytes("0x01"), Bytes(ByteArray(32)), bigIntegerOf(0),
        Bytes(ByteArray(32)), Bytes.EMPTY, Bytes.EMPTY,
    )

    val decoder = BundleDecoder()

    beforeTest { logIndex = 0 }

    test("splits execution logs at each UserOperationEvent") {
        val logs = listOf(
            accountDeployed(ep7, hash(1), alice, Address("0xfac7000000000000000000000000000000000000")),
            transfer(0),
            beforeExecution(ep7),
            transfer(1),
            userOperationEvent(ep7, hash(1), alice, 0),
            transfer(2),
            transfer(3),
            revertReason(ep7, hash(2), bob, Bytes("0x08c379a0")),
            userOperationEvent(ep7, hash(2), bob, 5, success = false),
        )

        val bundle = decoder.decode(receipt(logs)).unwrap().single()
        bundle.bundler shouldBe bundler
        bundle.entryPoint shouldBe EntryPoint.V0_7
        bundle.userOperations shouldHaveSize 2

        val (first, second) = bundle.userOperations
        first.userOpHash shouldBe hash(1)
        first.factory shouldBe Address("0xfac7000000000000000000000000000000000000")
        first.validationLogs shouldContainExactly listOf(logs[0])
        first.executionLogs shouldContainExactly listOf(logs[3], logs[4])
        first.success shouldBe true

        second.index shouldBe 1
        second.sender shouldBe bob
        second.nonce shouldBe bigIntegerOf(5)
        second.success shouldBe false
        second.revertReason shouldBe Bytes("0x08c379a0")
        second.executionLogs shouldContainExactly logs.subList(5, 9)
        second.validationLogs.shouldBeEmpty()

        // the validation log of an unknown origin and the marker belong to the bundle
        bundle.unattributedLogs shouldContainExactly listOf(logs[1], logs[2])
    }

    test("look-alike events of other contracts do not split operations") {
        val fake = Address("0x0000000000000000000000000000000000000bad")
        val logs = listOf(
            beforeExecution(ep7),
            beforeExecution(fake),
            userOperationEvent(fake, hash(9), alice, 0),
            userOperationEvent(ep7, hash(1), alice, 0),
        )
        val op = decoder.decode(receipt(logs)).unwrap().single().userOperations.single()
        op.userOpHash shouldBe hash(1)
        op.executionLogs shouldContainExactly logs.subList(1, 4)
    }

    test("two bundles in one transaction") {
        val logs = listOf(
            beforeExecution(ep7),
            userOperationEvent(ep7, hash(1), alice, 0),
            transfer(0),
            accountDeployed(ep8, hash(2), bob, Address("0xfac7000000000000000000000000000000000000")),
            beforeExecution(ep8),
            userOperationEvent(ep8, hash(2), bob, 0),
            transfer(1),
        )
        val bundles = decoder.decode(receipt(logs, to = Address("0x00000000000000000000000000000000000000aa"))).unwrap()
        bundles.map { it.entryPoint } shouldContainExactly listOf(EntryPoint.V0_7, EntryPoint.V0_8)
        bundles[0].userOperations.single().userOpHash shouldBe hash(1)
        // logs between the bundles are part of the second bundle's validation phase
        bundles[1].unattributedLogs shouldContainExactly listOf(logs[2], logs[4], logs[6])
        bundles[1].userOperations.single().validationLogs shouldContainExactly listOf(logs[3])
    }

    test("only configured EntryPoints are decoded") {
        val logs = listOf(beforeExecution(ep7), userOperationEvent(ep7, hash(1), alice, 0))
        BundleDecoder(listOf(EntryPoint.V0_8)).decode(receipt(logs)).unwrap().shouldBeEmpty()
    }

    test("aggregator markers attribute operations to their aggregator") {
        val logs = listOf(
            beforeExecution(ep7),
            aggregatorChanged(ep7, aggregator),
            userOperationEvent(ep7, hash(1), alice, 0),
            aggregatorChanged(ep7, Address.ZERO),
            userOperationEvent(ep7, hash(2), bob, 0),
        )
        val bundle = decoder.decode(receipt(logs)).unwrap().single()
        bundle.userOperations.map { it.aggregator } shouldContainExactly listOf(aggregator, null)
        bundle.userOperations[0].executionLogs shouldContainExactly listOf(logs[2])
        bundle.unattributedLogs shouldContainExactly listOf(logs[0], logs[1], logs[3])
    }

    test("decodes the bundle calldata of a direct handleOps call") {
        val beneficiary = Address("0xbe4e000000000000000000000000000000000000")
        val input = EntryPointV07.FUNCTION_HANDLE_OPS.encodeCall(listOf(listOf(packed(alice, 0), packed(bob, 5)), beneficiary))
        val logs = listOf(
            beforeExecution(ep7),
            userOperationEvent(ep7, hash(1), alice, 0),
            userOperationEvent(ep7, hash(2), bob, 5),
        )

        val bundle = decoder.decode(receipt(logs), transaction(input)).unwrap().single()
        bundle.beneficiary shouldBe beneficiary
        bundle.userOperations.map { it.userOperation?.sender } shouldContainExactly listOf(alice, bob)
        bundle.userOperations[1].userOperation shouldBe UserOperation.fromPacked(
            PackedUserOperation(
                bob, bigIntegerOf(5), Bytes.EMPTY, Bytes("0x01"), Bytes(ByteArray(32)), bigIntegerOf(0),
                Bytes(ByteArray(32)), Bytes.EMPTY, Bytes.EMPTY,
            ),
            EntryPointVersion.V0_7,
        )
        // "0x01" is not a call data any standard decoder understands
        bundle.userOperations[0].calls.shouldBeNull()
    }

    test("calldata that does not match the executed operations is an error") {
        val beneficiary = Address("0xbe4e000000000000000000000000000000000000")
        val logs = listOf(beforeExecution(ep7), userOperationEvent(ep7, hash(1), alice, 0))

        val twoOps = EntryPointV07.FUNCTION_HANDLE_OPS.encodeCall(listOf(listOf(packed(alice, 0), packed(bob, 0)), beneficiary))
        decoder.decode(receipt(logs), transaction(twoOps)).unwrapError().message shouldContain "2 operations"

        val otherSender = EntryPointV07.FUNCTION_HANDLE_OPS.encodeCall(listOf(listOf(packed(bob, 0)), beneficiary))
        decoder.decode(receipt(logs), transaction(otherSender)).unwrapError().message shouldContain "does not match"
    }

    test("transaction of another receipt is rejected") {
        val logs = listOf(beforeExecution(ep7), userOperationEvent(ep7, hash(1), alice, 0))
        decoder.decode(receipt(logs), transaction(Bytes.EMPTY, hash = Hash.ZERO)).unwrapError().message shouldContain "does not match"
    }

    test("transaction that is not a bundle call leaves the operations undecoded") {
        val logs = listOf(beforeExecution(ep7), userOperationEvent(ep7, hash(1), alice, 0))
        val op = decoder.decode(receipt(logs), transaction(Bytes("0x12345678"))).unwrap().single().userOperations.single()
        op.userOperation.shouldBeNull()
    }

    test("trailing logs after the last operation belong to the bundle") {
        val logs = listOf(beforeExecution(ep7), userOperationEvent(ep7, hash(1), alice, 0), transfer(0))
        decoder.decode(receipt(logs)).unwrap().single().unattributedLogs shouldContainExactly listOf(logs[0], logs[2])
    }
})
