package io.ethers.aa.bundle

import io.ethers.aa.EntryPoint
import io.ethers.aa.contracts.EntryPointV09
import io.ethers.core.types.Address
import io.ethers.core.types.Log
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Decodes real bundles recorded from Ethereum mainnet, of every EntryPoint version.
 */
class BundleDecoderFixtureTest : FunSpec({
    val decoder = BundleDecoder
    val bundleFixtures = BundleFixture.ALL.filter { it.name != "v07_no_bundle" }

    fun userOperationEvents(fixture: BundleFixture): List<Log> {
        return fixture.receipt.logs.filter {
            EntryPoint.canonical(it.address) != null && EntryPointV09.UserOperationEvent.isLogValid(it)
        }
    }

    fun allLogs(bundles: List<Bundle>): List<Log> {
        return bundles.flatMap { bundle -> bundle.unattributedLogs + bundle.userOperations.flatMap { it.logs } }
    }

    context("receipt only") {
        withData(bundleFixtures.associateBy { it.name }) { fixture ->
            val bundles = decoder.decode(fixture.receipt).unwrap()
            val events = userOperationEvents(fixture)

            bundles.flatMap { it.userOperations }.map { it.userOpHash } shouldContainExactly events.map { it.topics[1] }
            // every log is accounted for, exactly once
            allLogs(bundles) shouldContainExactlyInAnyOrder fixture.receipt.logs

            for (bundle in bundles) {
                bundle.bundler shouldBe fixture.receipt.from
                bundle.beneficiary.shouldBeNull()
                for (op in bundle.userOperations) {
                    op.userOperation.shouldBeNull()
                    op.executionFrames.shouldBeNull()
                    op.executionLogs.last().topics[0] shouldBe EntryPointV09.UserOperationEvent.abi.topicId
                }
            }
        }
    }

    context("with transaction") {
        withData(bundleFixtures.associateBy { it.name }) { fixture ->
            val bundles = decoder.decode(fixture.receipt, fixture.transaction).unwrap()
            val isDirect = EntryPoint.canonical(fixture.transaction.to ?: Address.ZERO) != null

            for (bundle in bundles) {
                if (!isDirect) {
                    bundle.beneficiary.shouldBeNull()
                    continue
                }

                bundle.beneficiary.shouldNotBeNull()
                for (op in bundle.userOperations) {
                    val userOperation = op.userOperation.shouldNotBeNull()
                    userOperation.sender shouldBe op.sender
                    userOperation.nonce shouldBe op.nonce
                    userOperation.paymaster shouldBe op.paymaster
                }
            }
        }
    }

    context("computed hashes match the EntryPoint's") {
        withData(bundleFixtures.associateBy { it.name }) { fixture ->
            val bundles = decoder.decode(fixture.receipt, fixture.transaction, fixture.trace).unwrap()
            val authorities = fixture.transaction.authorizationList.orEmpty().associateBy { it.recoverAuthority() }

            for (bundle in bundles) {
                for (op in bundle.userOperations) {
                    val userOperation = op.userOperation.shouldNotBeNull()
                    // an EIP-7702 operation's hash covers the sender's delegate, taken from the bundle's authorization
                    // list, or from the event of an account the EntryPoint initialized. An account that was delegated
                    // earlier is not covered by either, its delegate is only known from its code.
                    val delegate = authorities[userOperation.sender]?.address ?: op.eip7702Delegate
                    if (userOperation.isEip7702 && delegate == null) {
                        shouldThrow<IllegalArgumentException> { userOperation.hash(bundle.entryPoint, fixture.chainId) }
                        continue
                    }

                    userOperation.hash(bundle.entryPoint, fixture.chainId, delegate) shouldBe op.userOpHash
                }
            }
        }
    }

    context("with trace") {
        withData(bundleFixtures.associateBy { it.name }) { fixture ->
            val bundles = decoder.decode(fixture.receipt, fixture.transaction, fixture.trace).unwrap()
            val receiptOnly = decoder.decode(fixture.receipt).unwrap()

            bundles.flatMap { it.userOperations }.map { it.userOpHash } shouldContainExactly
                userOperationEvents(fixture).map { it.topics[1] }
            allLogs(bundles) shouldContainExactlyInAnyOrder fixture.receipt.logs

            for ((bundle, plain) in bundles.zip(receiptOnly)) {
                bundle.beneficiary.shouldNotBeNull()
                for ((op, plainOp) in bundle.userOperations.zip(plain.userOperations)) {
                    op.userOperation.shouldNotBeNull()

                    // the trace only moves logs between groups, the execution split stays the same
                    (op.executionLogs + op.postOpLogs) shouldContainExactlyInAnyOrder plainOp.executionLogs

                    val validationFrames = op.validationFrames.shouldNotBeNull()
                    validationFrames.shouldNotBeEmpty()
                    validationFrames.any { it.to == op.sender } shouldBe true

                    val executionFrames = op.executionFrames.shouldNotBeNull()
                    executionFrames.shouldNotBeEmpty()
                    executionFrames.first().to shouldBe bundle.entryPoint.address
                }
            }
        }
    }

    test("transaction without bundles decodes to no bundles") {
        val fixture = BundleFixture.load("v07_no_bundle")
        decoder.decode(fixture.receipt, fixture.transaction, fixture.trace).unwrap().shouldBeEmpty()
        decoder.decode(fixture.receipt).unwrap().shouldBeEmpty()
    }

    test("reverted operations carry their revert reason") {
        for (name in listOf("v06_revert", "v07_revert", "v08_revert")) {
            val fixture = BundleFixture.load(name)
            val ops = decoder.decode(fixture.receipt).unwrap().flatMap { it.userOperations }
            val failed = ops.filter { !it.success }
            failed.shouldNotBeEmpty()
            failed.forEach { it.revertReason.shouldNotBeNull() }
        }
    }

    test("paymaster postOp reverts are reported and postOp logs are split with a trace") {
        val fixture = BundleFixture.load("v07_postop_revert")
        val op = decoder.decode(fixture.receipt, fixture.transaction, fixture.trace).unwrap().single().userOperations.single()
        op.postOpRevertReason.shouldNotBeNull()
        op.paymaster.shouldNotBeNull()
    }

    test("account deployments are attributed to their operation") {
        val fixture = BundleFixture.load("v07_multi_deployed")
        val ops = decoder.decode(fixture.receipt).unwrap().flatMap { it.userOperations }
        val deployed = ops.filter { it.deployedAccount }
        deployed.shouldNotBeEmpty()
        deployed.forEach { op ->
            op.validationLogs.any { EntryPointV09.AccountDeployed.isLogValid(it) && it.topics[1] == op.userOpHash } shouldBe true
        }
    }

    test("bundle sent through another contract is decoded from the trace") {
        val fixture = BundleFixture.load("v08_via_contract")
        fixture.transaction.to shouldNotBe EntryPoint.V0_8.address

        val withoutTrace = decoder.decode(fixture.receipt, fixture.transaction).unwrap()
        withoutTrace.flatMap { it.userOperations }.forEach { it.userOperation.shouldBeNull() }

        val withTrace = decoder.decode(fixture.receipt, fixture.transaction, fixture.trace).unwrap()
        withTrace shouldHaveSize withoutTrace.size
        withTrace.flatMap { it.userOperations }.forEach { it.userOperation.shouldNotBeNull() }
    }

    test("decoded calls of operations whose account uses a standard execute function") {
        val ops = bundleFixtures.flatMap { decoder.decode(it.receipt, it.transaction, it.trace).unwrap() }.flatMap { it.userOperations }
        ops.count { it.calls != null } shouldNotBe 0
    }
})
