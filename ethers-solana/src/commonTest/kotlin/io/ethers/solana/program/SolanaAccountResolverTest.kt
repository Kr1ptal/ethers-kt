package io.ethers.solana.program

import io.channels.core.ChannelReceiver
import io.ethers.core.Kotlinx
import io.ethers.core.Result
import io.ethers.core.success
import io.ethers.providers.JsonRpcClient
import io.ethers.providers.RpcError
import io.ethers.providers.types.BatchRpcRequest
import io.ethers.solana.codec.BorshReader
import io.ethers.solana.codec.BorshWriter
import io.ethers.solana.codec.SolanaAccountFactory
import io.ethers.solana.codec.SolanaCodec
import io.ethers.solana.codec.SolanaDiscriminator
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.SolanaAddress
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonElement
import kotlin.io.encoding.Base64

private val PROGRAM = SolanaAddress(ByteArray(32) { 9 })

private fun address(byte: Int) = SolanaAddress(ByteArray(32) { byte.toByte() })

/** An account holding the address of the next one. */
private data class Link(val next: SolanaAddress) {
    companion object : SolanaCodec<Link> {
        val ACCOUNT = SolanaAccountFactory("Link", SolanaDiscriminator.of(1, 1, 1, 1, 1, 1, 1, 1), this)

        override fun read(reader: BorshReader) = Link(reader.readAddress())

        override fun write(writer: BorshWriter, value: Link) = writer.writeAddress(value.next)
    }
}

private fun link(next: SolanaAddress): String {
    val data = Link.ACCOUNT.encode(Link(next))
    return """{"data":["${Base64.encode(data)}","base64"],"executable":false,"lamports":1,"owner":"$PROGRAM","rentEpoch":0,"space":${data.size}}"""
}

private val NEXT = listOf(SolanaRelationCandidate(Link.ACCOUNT) { it.next })

/** Answers `getMultipleAccounts` from [accounts], recording each request's addresses and slot bound. */
private class AccountsClient : JsonRpcClient {
    val accounts = HashMap<SolanaAddress, String>()
    val requests = ArrayList<List<SolanaAddress>>()
    val minContextSlots = ArrayList<String?>()
    private var slot = 10

    override suspend fun <T> request(method: String, params: Array<*>, resultDecoder: (JsonElement) -> T): Result<T, RpcError> {
        check(method == "getMultipleAccounts") { "Unexpected $method" }
        @Suppress("UNCHECKED_CAST")
        val addresses = (params[0] as List<String>).map(::SolanaAddress)
        requests.add(addresses)
        minContextSlots.add((params[1] as Map<*, *>)["minContextSlot"]?.toString())
        val values = addresses.joinToString(",") { accounts[it] ?: "null" }
        return success(resultDecoder(Kotlinx.DEFAULT.parseToJsonElement("""{"context":{"slot":${slot++}},"value":[$values]}""")))
    }

    override suspend fun <T : Any> subscribe(params: Array<*>, resultDecoder: (JsonElement) -> T): Result<ChannelReceiver<T>, RpcError> = error("Unexpected subscription")
    override suspend fun requestBatch(batch: BatchRpcRequest): Boolean = error("Unexpected batch")
    override fun close() = Unit
}

private fun account(path: String, provided: SolanaAddress? = null, vararg derivations: SolanaAccountDerivation) = SolanaAccountResolver.Account(path, provided, false, derivations.toList())

private fun pda(vararg dependencies: SolanaAccountDependency, derive: (SolanaAccountResolver.Inputs) -> SolanaAddress) = SolanaAccountDerivation.Pda(dependencies.toList(), derive)

class SolanaAccountResolverTest : FunSpec({
    test("a chain of relations is fetched round by round, each round no older than the one before") {
        val client = AccountsClient()
        client.accounts[address(1)] = link(address(2))
        client.accounts[address(2)] = link(address(3))
        val resolver = SolanaAccountResolver(
            PROGRAM,
            listOf(
                account("head", address(1)),
                account("second", null, SolanaAccountDerivation.Relations(listOf(SolanaRelationSource(0, NEXT)))),
                account("third", null, SolanaAccountDerivation.Relations(listOf(SolanaRelationSource(1, NEXT)))),
            ),
        )
        val resolved = resolver.resolve(SolanaProvider(client)).send().unwrap()
        resolved.required(1) shouldBe address(2)
        resolved.required(2) shouldBe address(3)
        resolved.byPath("third") shouldBe address(3)
        resolved.byPath("none").shouldBeNull()
        resolved.size shouldBe 3
        client.requests shouldBe listOf(listOf(address(1)), listOf(address(2)))
        client.minContextSlots shouldBe listOf(null, "10")
    }

    test("more accounts than one request takes are fetched in requests of 100") {
        val client = AccountsClient()
        val sources = (0 until 150).map { SolanaAddress(ByteArray(32) { i -> if (i == 0) 1 else it.toByte() }) }
        sources.forEach { client.accounts[it] = link(address(7)) }
        val dependencies = sources.indices.map { SolanaAccountDependency.data(it, Link.ACCOUNT) }.toTypedArray()
        val resolver = SolanaAccountResolver(
            PROGRAM,
            sources.mapIndexed { i, it -> account("s$i", it) } + account("sum", null, pda(*dependencies) { inputs -> inputs.data(149, Link.ACCOUNT).next }),
        )
        resolver.resolve(SolanaProvider(client)).send().unwrap().required(150) shouldBe address(7)
        client.requests.map { it.size } shouldBe listOf(100, 50)
        client.minContextSlots shouldBe listOf(null, "10")
    }

    test("the first derivation that does not depend on an absent optional account is used") {
        val resolver = SolanaAccountResolver(
            PROGRAM,
            listOf(
                SolanaAccountResolver.Account("optional", null, optional = true),
                account(
                    "target",
                    null,
                    pda(SolanaAccountDependency.address(0)) { error("not derived") },
                    SolanaAccountDerivation.Fixed(address(4)),
                    SolanaAccountDerivation.Fixed(address(5)),
                ),
            ),
        )
        val resolved = resolver.resolve(SolanaProvider(AccountsClient())).send().unwrap()
        resolved[0].shouldBeNull()
        resolved.required(1) shouldBe address(4)
        shouldThrow<IllegalStateException> { resolved.required(0) }.message shouldContain "'optional' is absent"
        resolved.toString() shouldBe "SolanaResolvedAccounts(optional=null, target=${address(4)})"
    }

    test("relation sources that are absent optional accounts are left out, the others must still agree") {
        val client = AccountsClient()
        client.accounts[address(1)] = link(address(3))
        val resolver = SolanaAccountResolver(
            PROGRAM,
            listOf(
                SolanaAccountResolver.Account("optional", null, optional = true),
                account("present", address(1)),
                account("target", null, SolanaAccountDerivation.Relations(listOf(SolanaRelationSource(0, NEXT), SolanaRelationSource(1, NEXT)))),
            ),
        )
        resolver.resolve(SolanaProvider(client)).send().unwrap().required(2) shouldBe address(3)

        val only = SolanaAccountResolver(
            PROGRAM,
            listOf(
                SolanaAccountResolver.Account("optional", null, optional = true),
                account("target", null, SolanaAccountDerivation.Relations(listOf(SolanaRelationSource(0, NEXT)))),
            ),
        )
        only.resolve(SolanaProvider(client)).send().unwrapError() shouldBe SolanaAccountResolutionError.MissingAccount(
            "target",
            "it was not provided, and every way to derive it depends on an optional account that was not provided: 'optional'",
        )
    }

    test("a derivation whose seeds cannot derive an address fails with the reason") {
        val resolver = SolanaAccountResolver(
            PROGRAM,
            listOf(account("long", null, pda { SolanaAddress.findProgramAddress(listOf(ByteArray(33)), PROGRAM).address })),
        )
        val error = resolver.resolve(SolanaProvider(AccountsClient())).send().unwrapError().shouldBeInstanceOf<SolanaAccountResolutionError.InvalidSeeds>()
        error.account shouldBe "long"
        error.message shouldContain "cannot be derived"
    }

    test("cycles are reported from where they close, including an account that depends on itself") {
        val self = SolanaAccountResolver(PROGRAM, listOf(account("a", null, pda(SolanaAccountDependency.address(0)) { it.address(0) })))
        self.resolve(SolanaProvider(AccountsClient())).send().unwrapError() shouldBe SolanaAccountResolutionError.DependencyCycle(listOf("a", "a"))

        // a waits on the cycle between b and c without being part of it
        val behind = SolanaAccountResolver(
            PROGRAM,
            listOf(
                account("a", null, pda(SolanaAccountDependency.address(1)) { it.address(1) }),
                account("b", null, pda(SolanaAccountDependency.address(2)) { it.address(2) }),
                account("c", null, SolanaAccountDerivation.Relations(listOf(SolanaRelationSource(1, NEXT)))),
            ),
        )
        val error = behind.resolve(SolanaProvider(AccountsClient())).send().unwrapError()
        error shouldBe SolanaAccountResolutionError.DependencyCycle(listOf("b", "c", "b"))
        error.message shouldBe "Accounts depend on each other: b -> c -> b; provide one of them"
    }

    test("the first failure in account order is reported, after the round that found it") {
        val client = AccountsClient()
        val resolver = SolanaAccountResolver(
            PROGRAM,
            listOf(
                account("source", address(1)),
                account("first", null, SolanaAccountDerivation.Relations(listOf(SolanaRelationSource(0, NEXT)))),
                account("second", null, SolanaAccountDerivation.Relations(listOf(SolanaRelationSource(0, NEXT)))),
            ),
        )
        resolver.resolve(SolanaProvider(client)).send().unwrapError()
            .shouldBeInstanceOf<SolanaAccountResolutionError.AccountNotFound>().requiredBy shouldBe "first"
    }

    test("dependencies must name accounts of the resolver") {
        shouldThrow<IllegalArgumentException> {
            SolanaAccountResolver(PROGRAM, listOf(account("a", null, pda(SolanaAccountDependency.address(3)) { it.address(3) })))
        }.message shouldBe "Account 'a' depends on account 3, but there are 1"
        shouldThrow<IllegalArgumentException> { SolanaAccountDerivation.Relations(emptyList()) }
        shouldThrow<IllegalArgumentException> { SolanaRelationSource(0, emptyList()) }
    }
})
