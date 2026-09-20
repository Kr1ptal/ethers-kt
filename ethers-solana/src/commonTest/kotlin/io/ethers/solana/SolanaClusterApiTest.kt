package io.ethers.solana

import io.ethers.core.Kotlinx
import io.ethers.providers.HttpClient
import io.ethers.solana.providers.SolanaProvider
import io.ethers.solana.types.Programs
import io.ethers.solana.types.SolanaAddress
import io.ethers.solana.types.rpc.Commitment
import io.ethers.solana.types.rpc.LargestAccountsFilter
import io.ethers.solana.types.rpc.SlotRange
import io.ethers.solana.types.rpc.SolanaReadConfig
import io.github.artificialpb.bignum.bigIntegerOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.HttpClient as KtorHttpClient

/** Covers the cluster, ledger and economics reads: the parameters sent and the shapes decoded. */
class SolanaClusterApiTest : FunSpec({
    val address = Programs.SYSTEM
    lateinit var provider: SolanaProvider
    lateinit var ktor: KtorHttpClient
    var response = "null"
    val requests = mutableListOf<JsonObject>()

    beforeEach {
        requests.clear()
        ktor = KtorHttpClient(
            MockEngine { request ->
                val body = Kotlinx.DEFAULT.parseToJsonElement((request.body as TextContent).text).jsonObject
                requests.add(body)
                respond("""{"jsonrpc":"2.0","id":${body.getValue("id")},"result":$response}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            },
        )
        provider = SolanaProvider(HttpClient("https://example.invalid", ktor), defaultCommitment = Commitment.CONFIRMED)
    }
    afterEach {
        provider.close()
        ktor.close()
    }

    fun assertRequest(method: String, params: String) {
        requests.last().getValue("method").jsonPrimitive.content shouldBe method
        requests.last().getValue("params") shouldBe Kotlinx.DEFAULT.parseToJsonElement(params)
    }
    fun contextual(value: String) = """{"context":{"slot":9},"value":$value}"""

    test("ledger reads send their slot arguments and decode scalars") {
        response = "1"
        provider.getFirstAvailableBlock().send().unwrap() shouldBe bigIntegerOf(1)
        assertRequest("getFirstAvailableBlock", "[]")
        provider.minimumLedgerSlot().send().unwrap() shouldBe bigIntegerOf(1)
        provider.getMaxShredInsertSlot().send().unwrap() shouldBe bigIntegerOf(1)
        provider.getMaxRetransmitSlot().send().unwrap() shouldBe bigIntegerOf(1)

        response = "[7,8]"
        provider.getBlocksWithLimit(bigIntegerOf(7), 2).send().unwrap() shouldBe listOf(bigIntegerOf(7), bigIntegerOf(8))
        assertRequest("getBlocksWithLimit", """[7,2,{"commitment":"confirmed"}]""")

        // a slot the node cannot answer for is null rather than an error
        response = "null"
        provider.getBlockTime(bigIntegerOf(7)).send().unwrap() shouldBe null
        response = "1700000000"
        provider.getBlockTime(bigIntegerOf(7)).send().unwrap() shouldBe 1700000000L
        assertRequest("getBlockTime", "[7]")
    }

    test("block commitment, production and performance samples decode their nested shapes") {
        response = """{"commitment":[0,1,2],"totalStake":42}"""
        provider.getBlockCommitment(bigIntegerOf(7)).send().unwrap().totalStake shouldBe bigIntegerOf(42)

        response = contextual("""{"byIdentity":{"$address":[10,9]},"range":{"firstSlot":1,"lastSlot":100}}""")
        val production = provider.getBlockProduction(null, SlotRange(bigIntegerOf(1), bigIntegerOf(100)), Commitment.FINALIZED).send().unwrap().value
        production.byIdentity.getValue(address.toString()) shouldBe listOf(10L, 9L)
        production.range.lastSlot shouldBe bigIntegerOf(100)
        assertRequest("getBlockProduction", """[{"commitment":"finalized","range":{"firstSlot":1,"lastSlot":100}}]""")

        response = """[{"slot":9,"numTransactions":100,"numSlots":60,"samplePeriodSecs":60,"numNonVoteTransactions":40}]"""
        provider.getRecentPerformanceSamples(5).send().unwrap().single().numNonVoteTransactions shouldBe bigIntegerOf(40)
        assertRequest("getRecentPerformanceSamples", "[5]")
    }

    test("cluster reads decode nullable node ports and both vote account groups") {
        response = """[{"pubkey":"$address","gossip":"127.0.0.1:8001","rpc":null,"version":"3.0.0","featureSet":123,"shredVersion":1}]"""
        val node = provider.getClusterNodes().send().unwrap().single()
        node.pubkey shouldBe address
        node.gossip shouldBe "127.0.0.1:8001"
        node.rpc shouldBe null

        response = """{"current":[{"votePubkey":"$address","nodePubkey":"$address","activatedStake":1,"epochVoteAccount":true,"commission":5,"lastVote":9,"rootSlot":8,"epochCredits":[[1,2,3]]}],"delinquent":[]}"""
        val votes = provider.getVoteAccounts().send().unwrap()
        votes.current.single().commission shouldBe 5
        votes.current.single().epochCredits.single() shouldBe listOf(bigIntegerOf(1), bigIntegerOf(2), bigIntegerOf(3))
        votes.delinquent shouldBe emptyList()
        assertRequest("getVoteAccounts", """[{"commitment":"confirmed"}]""")

        // the delinquency filters the node applies are the caller's to set
        provider.getVoteAccounts(null, Commitment.FINALIZED, true, bigIntegerOf(256)).send().unwrap()
        assertRequest("getVoteAccounts", """[{"commitment":"finalized","keepUnstakedDelinquents":true,"delinquentSlotDistance":256}]""")

        // a stale read is refused rather than answered from an earlier slot
        response = "5"
        provider.getSlot(SolanaReadConfig(Commitment.CONFIRMED, bigIntegerOf(400))).send().unwrap() shouldBe bigIntegerOf(5)
        assertRequest("getSlot", """[{"commitment":"confirmed","minContextSlot":400}]""")

        provider.getSlot(SolanaReadConfig(minContextSlot = bigIntegerOf(402))).send().unwrap()
        assertRequest("getSlot", """[{"commitment":"confirmed","minContextSlot":402}]""")
        provider.getSlot(Commitment.FINALIZED).send().unwrap()
        assertRequest("getSlot", """[{"commitment":"finalized"}]""")
        provider.getSlot(SolanaReadConfig(commitment = Commitment.FINALIZED)).send().unwrap()
        assertRequest("getSlot", """[{"commitment":"finalized"}]""")

        response = contextual("""{"blockhash":"$address","lastValidBlockHeight":3}""")
        provider.getLatestBlockhash(SolanaReadConfig(Commitment.FINALIZED, bigIntegerOf(401))).send().unwrap()
        assertRequest("getLatestBlockhash", """[{"commitment":"finalized","minContextSlot":401}]""")
    }

    test("leader schedule is keyed by identity, and null for an epoch the node cannot answer for") {
        response = """{"$address":[0,1,2]}"""
        provider.getLeaderSchedule().send().unwrap()!!.getValue(address.toString()) shouldBe listOf(bigIntegerOf(0), bigIntegerOf(1), bigIntegerOf(2))
        assertRequest("getLeaderSchedule", """[null,{"commitment":"confirmed"}]""")

        provider.getLeaderSchedule(bigIntegerOf(7)).send().unwrap() shouldBe mapOf(address.toString() to listOf(bigIntegerOf(0), bigIntegerOf(1), bigIntegerOf(2)))
        assertRequest("getLeaderSchedule", """[7,{"commitment":"confirmed"}]""")

        response = "null"
        provider.getLeaderSchedule().send().unwrap() shouldBe null
    }

    test("slot leaders and cluster identity decode as addresses") {
        response = "\"$address\""
        provider.getSlotLeader().send().unwrap() shouldBe address
        assertRequest("getSlotLeader", """[{"commitment":"confirmed"}]""")

        response = """["$address","$address"]"""
        provider.getSlotLeaders(bigIntegerOf(7), 2).send().unwrap().size shouldBe 2
        assertRequest("getSlotLeaders", "[7,2]")

        response = "\"${SolanaAddress(ByteArray(32) { 3 })}\""
        provider.getGenesisHash().send().unwrap().toString() shouldBe SolanaAddress(ByteArray(32) { 3 }).toString()
    }

    test("epoch schedule and snapshot slots decode, including the optional incremental slot") {
        response = """{"slotsPerEpoch":432000,"leaderScheduleSlotOffset":432000,"warmup":false,"firstNormalEpoch":0,"firstNormalSlot":0}"""
        provider.getEpochSchedule().send().unwrap().slotsPerEpoch shouldBe bigIntegerOf(432000)

        response = """{"full":100}"""
        provider.getHighestSnapshotSlot().send().unwrap().incremental shouldBe null
        response = """{"full":100,"incremental":110}"""
        provider.getHighestSnapshotSlot().send().unwrap().incremental shouldBe bigIntegerOf(110)
    }

    test("supply, inflation and stake reads send their options and decode their shapes") {
        response = contextual("""{"total":3,"circulating":2,"nonCirculating":1,"nonCirculatingAccounts":["$address"]}""")
        provider.getSupply(true).send().unwrap().value.nonCirculatingAccounts.single() shouldBe address
        assertRequest("getSupply", """[{"commitment":"confirmed","excludeNonCirculatingAccountsList":false}]""")

        provider.getSupply().send().unwrap().value.total shouldBe bigIntegerOf(3)
        assertRequest("getSupply", """[{"commitment":"confirmed","excludeNonCirculatingAccountsList":true}]""")

        response = """{"initial":0.15,"terminal":0.015,"taper":0.15,"foundation":0.05,"foundationTerm":7.0}"""
        provider.getInflationGovernor().send().unwrap().terminal shouldBe 0.015

        response = """{"total":0.149,"validator":0.148,"foundation":0.001,"epoch":100}"""
        provider.getInflationRate().send().unwrap().epoch shouldBe bigIntegerOf(100)

        // an address that earned nothing keeps its position as null
        response = """[null,{"epoch":100,"effectiveSlot":9,"amount":5,"postBalance":10,"commission":5}]"""
        val rewards = provider.getInflationReward(listOf(address, address), bigIntegerOf(100)).send().unwrap()
        rewards[0] shouldBe null
        rewards[1]!!.amount shouldBe bigIntegerOf(5)
        assertRequest("getInflationReward", """[["$address","$address"],{"commitment":"confirmed","epoch":100}]""")

        response = contextual("1000000")
        provider.getStakeMinimumDelegation().send().unwrap().value shouldBe bigIntegerOf(1000000)
    }

    test("account and token queries send their filters") {
        response = contextual("""[{"address":"$address","lamports":9}]""")
        provider.getLargestAccounts(LargestAccountsFilter.CIRCULATING).send().unwrap().value.single().lamports shouldBe bigIntegerOf(9)
        assertRequest("getLargestAccounts", """[{"commitment":"confirmed","filter":"circulating"}]""")

        provider.getLargestAccounts().send().unwrap().value.size shouldBe 1
        assertRequest("getLargestAccounts", """[{"commitment":"confirmed"}]""")

        response = contextual("""[{"address":"$address","amount":"5","decimals":6,"uiAmount":0.000005,"uiAmountString":"0.000005"}]""")
        val largest = provider.getTokenLargestAccounts(address).send().unwrap().value.single()
        largest.amount shouldBe bigIntegerOf(5)
        largest.uiAmountString shouldBe "0.000005"
        assertRequest("getTokenLargestAccounts", """["$address",{"commitment":"confirmed"}]""")

        response = contextual("""[{"pubkey":"$address","account":{"data":["AQID","base64"],"executable":false,"lamports":1,"owner":"$address","rentEpoch":0}}]""")
        provider.getTokenAccountsByDelegate(address, Programs.TOKEN).send().unwrap().value.single().pubkey shouldBe address
        assertRequest("getTokenAccountsByDelegate", """["$address",{"mint":"${Programs.TOKEN}"},{"commitment":"confirmed","encoding":"base64"}]""")

        provider.getTokenAccountsByDelegateForProgram(address, Programs.TOKEN_2022).send().unwrap().value.size shouldBe 1
        assertRequest("getTokenAccountsByDelegate", """["$address",{"programId":"${Programs.TOKEN_2022}"},{"commitment":"confirmed","encoding":"base64"}]""")
    }
})
